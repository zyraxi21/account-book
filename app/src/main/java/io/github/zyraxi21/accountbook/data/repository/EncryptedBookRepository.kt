package io.github.zyraxi21.accountbook.data.repository

import androidx.room.withTransaction
import io.github.zyraxi21.accountbook.data.local.*
import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.sms.ParsedIcbcIncome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

class EncryptedBookRepository(
    private val databaseProvider: () -> BookDatabase,
    private val defaultChannelNames: List<String>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BookRepository {
    private val database by lazy(databaseProvider)

    override fun observeBook(): Flow<BookData> = flow {
        initialize()
        emitAll(database.invalidationTracker.createFlow(
            "channels", "monthly_assets", "channel_balances", "incomes", "app_settings", "remembered_channels", "sms_import_receipts",
        ).map { database.withTransaction { readBook(database.bookDao()) } })
    }.flowOn(dispatcher)

    private suspend fun initialize() {
        database.withTransaction {
            val dao = database.bookDao()
            if (dao.settings() == null) {
                val channels = defaultChannelNames.mapIndexed { index, name -> ChannelEntity(UUID.randomUUID().toString(), name, position = index) }
                dao.insertChannels(channels)
                dao.insertRememberedChannels(channels.map { RememberedChannelEntity(it.id, it.position) })
                dao.saveSettings(AppSettingsEntity())
            }
        }
    }

    private suspend fun <T> write(block: suspend (BookDao) -> T): T = withContext(dispatcher) {
        try {
            initialize()
            database.withTransaction { block(database.bookDao()) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: BookException) {
            throw error
        } catch (error: Exception) {
            throw BookException(BookError.STORAGE_UNAVAILABLE, error)
        }
    }

    override suspend fun saveAsset(snapshot: MonthlyAssetSnapshot, originalMonth: YearMonth?) = write { dao ->
        if (snapshot.balances.isEmpty()) throw BookException(BookError.CHANNEL_REQUIRED)
        if (snapshot.liability.fen < 0 || snapshot.balances.any { it.amount.fen < 0 }) throw BookException(BookError.INVALID_AMOUNT)
        snapshot.total
        if (snapshot.balances.map { it.channelId }.distinct().size != snapshot.balances.size) throw BookException(BookError.CHANNEL_REQUIRED)
        val month = snapshot.month.toString()
        if (dao.snapshot(month) != null && snapshot.month != originalMonth) throw BookException(BookError.MONTH_EXISTS)
        val original = originalMonth?.let { dao.snapshot(it.toString()) }
        if (originalMonth != null && original == null) throw BookException(BookError.RECORD_NOT_FOUND)
        val oldNames = original?.balances?.associate { it.channelId to it.channelName }.orEmpty()
        val channels = dao.channels().associateBy { it.id }
        val balances = snapshot.balances.mapIndexed { index, balance ->
            val channel = channels[balance.channelId] ?: throw BookException(BookError.CHANNEL_UNAVAILABLE)
            if (!channel.active && balance.channelId !in oldNames) throw BookException(BookError.CHANNEL_UNAVAILABLE)
            ChannelBalanceEntity(month, balance.channelId, oldNames[balance.channelId] ?: channel.name, balance.amount.fen, index)
        }
        if (originalMonth != null && snapshot.month != originalMonth) dao.deleteSnapshot(originalMonth.toString())
        dao.saveSnapshot(MonthlyAssetEntity(month, snapshot.registeredAt.toEpochMilli(), snapshot.liability.fen))
        dao.deleteBalances(month)
        dao.insertBalances(balances)
        dao.clearRememberedChannels()
        dao.insertRememberedChannels(balances.filter { channels.getValue(it.channelId).active }
            .mapIndexed { index, balance -> RememberedChannelEntity(balance.channelId, index) })
    }

    override suspend fun deleteAsset(month: YearMonth) = write { it.deleteSnapshot(month.toString()) }

    override suspend fun saveIncome(income: Income, importFingerprint: String?) = write { dao ->
        validateIncome(dao, income)
        if (importFingerprint != null && dao.hasReceipt(importFingerprint)) throw BookException(BookError.SMS_DUPLICATE)
        val existing = dao.income(income.id)
        dao.saveIncome(income.copy(title = income.title.trim(), source = existing?.let { IncomeSource.valueOf(it.source) } ?: income.source).toEntity())
        if (importFingerprint != null) dao.insertReceipt(SmsImportReceiptEntity(importFingerprint))
    }

    override suspend fun deleteIncome(id: String) = write { it.deleteIncome(id) }

    override suspend fun addChannel(name: String) = write { dao ->
        val channels = dao.channels()
        val cleanName = validateChannelName(name, channels)
        dao.saveChannel(ChannelEntity(UUID.randomUUID().toString(), cleanName, position = (channels.maxOfOrNull { it.position } ?: -1) + 1))
    }

    override suspend fun renameChannel(id: String, name: String) = write { dao ->
        val channels = dao.channels()
        val channel = channels.firstOrNull { it.id == id && it.active } ?: throw BookException(BookError.CHANNEL_UNAVAILABLE)
        dao.saveChannel(channel.copy(name = validateChannelName(name, channels, id)))
    }

    override suspend fun deleteChannel(id: String) = write { dao ->
        val channel = dao.channels().firstOrNull { it.id == id } ?: throw BookException(BookError.CHANNEL_UNAVAILABLE)
        dao.saveChannel(channel.copy(active = false))
        dao.forgetChannel(id)
    }

    override suspend fun setSmsAutoImport(enabled: Boolean) = write { dao ->
        val current = dao.settings() ?: AppSettingsEntity()
        if (current.smsAutoImportEnabled == enabled) Unit else dao.saveSettings(current.copy(smsAutoImportEnabled = enabled))
    }

    override suspend fun recordExport(exportedAt: Instant) = write { dao ->
        val current = dao.settings() ?: AppSettingsEntity()
        dao.saveSettings(current.copy(lastExportAtMillis = exportedAt.toEpochMilli()))
    }

    override suspend fun setHideOnStartup(enabled: Boolean) = write { dao ->
        val current = dao.settings() ?: AppSettingsEntity()
        dao.saveSettings(current.copy(hideOnStartup = enabled))
    }

    override suspend fun setAllowScreenshots(enabled: Boolean) = write { dao ->
        val current = dao.settings() ?: AppSettingsEntity()
        dao.saveSettings(current.copy(allowScreenshots = enabled))
    }

    /** 单一事务提交；设备偏好和短信去重凭据在覆盖时也保留。 */
    override suspend fun importBook(data: BookData, mode: ImportMode): BookImportResult = write { dao ->
        val existing = readBook(dao)
        val pending = prepareBookImport(existing, data, mode)
        if (mode == ImportMode.REPLACE) {
            dao.clearRememberedChannels()
            dao.deleteAllBalances()
            dao.deleteAllSnapshots()
            dao.deleteAllIncomes()
            dao.deleteAllChannels()
        }
        // 合并只新增渠道，按依赖顺序先建渠道，再写资产表和余额。
        if (pending.channels.isNotEmpty()) dao.insertChannels(pending.channels.map { ChannelEntity(it.id, it.name, it.active, it.position) })
        val balances = pending.snapshots.sortedBy { it.month }.flatMap { snapshot ->
            snapshot.balances.mapIndexed { index, balance -> ChannelBalanceEntity(snapshot.month.toString(), balance.channelId,
                balance.channelName, balance.amount.fen, index) }
        }
        pending.snapshots.forEach { dao.saveSnapshot(MonthlyAssetEntity(it.month.toString(), it.registeredAt.toEpochMilli(), it.liability.fen)) }
        if (balances.isNotEmpty()) dao.insertBalances(balances)
        pending.incomes.forEach { dao.saveIncome(it.toEntity()) }
        if (mode == ImportMode.REPLACE) {
            val activeIds = pending.activeChannels.map { it.id }.toSet()
            val remembered = existing.settings.rememberedChannelIds.filter { it in activeIds }.ifEmpty {
                pending.activeChannels.sortedBy { it.position }.map { it.id }
            }
            dao.insertRememberedChannels(remembered.mapIndexed { index, id -> RememberedChannelEntity(id, index) })
        }
        BookImportResult(mode, pending.channels.size, pending.snapshots.size, pending.incomes.size)
    }
    override suspend fun importSms(parsed: ParsedIcbcIncome, requireAutoEnabled: Boolean): Boolean = write { dao ->
        if (requireAutoEnabled && dao.settings()?.smsAutoImportEnabled != true) return@write false
        if (dao.hasReceipt(parsed.fingerprint)) return@write false
        val income = Income(UUID.randomUUID().toString(), parsed.title, parsed.amount, parsed.receivedAt, IncomeSource.SMS)
        validateIncome(dao, income)
        dao.insertReceipt(SmsImportReceiptEntity(parsed.fingerprint))
        dao.saveIncome(income.toEntity())
        true
    }

    private suspend fun validateIncome(dao: BookDao, income: Income) {
        if (income.title.trim().isEmpty() || income.title.length > 120) throw BookException(BookError.TITLE_REQUIRED)
        if (income.amount.fen <= 0) throw BookException(BookError.INCOME_MUST_BE_POSITIVE)
        Money.sum(dao.incomes().filter { it.id != income.id }.map { Money(it.amountFen) }) + income.amount
    }

    private fun validateChannelName(name: String, channels: List<ChannelEntity>, exceptId: String? = null): String {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName.length > 40) throw BookException(BookError.CHANNEL_NAME_REQUIRED)
        if (channels.any { it.active && it.id != exceptId && it.name.equals(cleanName, ignoreCase = true) }) throw BookException(BookError.CHANNEL_NAME_EXISTS)
        return cleanName
    }

    private suspend fun readBook(dao: BookDao): BookData = BookData(
        channels = dao.channels().map { Channel(it.id, it.name, it.active, it.position) },
        snapshots = dao.snapshots().map { record ->
            MonthlyAssetSnapshot(Instant.ofEpochMilli(record.asset.registeredAtMillis),
                record.balances.sortedBy { it.position }.map { ChannelBalance(it.channelId, it.channelName, Money(it.amountFen)) },
                Money(record.asset.liabilityFen))
        },
        incomes = dao.incomes().map { Income(it.id, it.title, Money(it.amountFen), Instant.ofEpochMilli(it.receivedAtMillis), IncomeSource.valueOf(it.source)) },
        settings = dao.settings().let { settings -> BookSettings(
            smsAutoImportEnabled = settings?.smsAutoImportEnabled == true,
            rememberedChannelIds = dao.rememberedChannels().map { it.channelId },
            exportedAt = settings?.lastExportAtMillis?.let(Instant::ofEpochMilli),
            hideOnStartup = settings?.hideOnStartup ?: true,
            allowScreenshots = settings?.allowScreenshots ?: false,
        ) },
    )

    private fun Income.toEntity() = IncomeEntity(id, title, amount.fen, receivedAt.toEpochMilli(), source.name)
}
