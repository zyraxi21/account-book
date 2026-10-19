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
        if (snapshot.liability.fen < 0 || snapshot.receivable.fen < 0 ||
            snapshot.balances.any { it.amount.fen < 0 }) throw BookException(BookError.INVALID_AMOUNT)
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
        dao.saveSnapshot(MonthlyAssetEntity(month, snapshot.registeredAt.toEpochMilli(), snapshot.liability.fen, snapshot.receivable.fen))
        dao.deleteBalances(month)
        dao.insertBalances(balances)
        saveChannelOrder(dao, balances.filter { channels.getValue(it.channelId).active }.map { it.channelId })
        dao.clearRememberedChannels()
        dao.insertRememberedChannels(balances.filter { channels.getValue(it.channelId).active }
            .mapIndexed { index, balance -> RememberedChannelEntity(balance.channelId, index) })
    }

    override suspend fun deleteAsset(month: YearMonth) = write { it.deleteSnapshot(month.toString()) }

    override suspend fun saveIncome(income: Income, importFingerprint: String?) = write { dao ->
        if (importFingerprint != null && (dao.hasReceipt(importFingerprint) ||
                matchingIncome(dao, income.title, income.amount.fen, income.receivedAt.toEpochMilli(), income.id) != null)) {
            throw BookException(BookError.SMS_DUPLICATE)
        }
        validateIncome(dao, income)
        val existing = dao.income(income.id)
        val saved = income.copy(title = income.title.trim(), source = existing?.let { IncomeSource.valueOf(it.source) } ?: income.source)
        dao.saveIncome(saved.toEntity())
        if (importFingerprint != null) {
            val record = dao.smsIncomeRecord(importFingerprint) ?: SmsIncomeRecordEntity(importFingerprint,
                saved.title, saved.amount.fen, saved.receivedAt.toEpochMilli())
            finishSmsRecord(dao, record, SmsIncomeStatus.IMPORTED, saved.id)
        }
        reconcilePendingSms(dao)
    }

    override suspend fun deleteIncome(id: String) = write { it.deleteIncome(id) }

    override suspend fun addChannel(name: String): Channel = write { dao ->
        val channels = dao.channels()
        val cleanName = validateChannelName(name, channels)
        val entity = ChannelEntity(UUID.randomUUID().toString(), cleanName, position = (channels.maxOfOrNull { it.position } ?: -1) + 1)
        dao.saveChannel(entity)
        Channel(entity.id, entity.name, entity.active, entity.position)
    }

    /**
     * 拖动排序只改启用渠道的登记顺序（channels.position）。
     * 历史资产表的余额顺序存在 channel_balances.position，故意不改写，避免回看旧月份时顺序跳动。
     */
    override suspend fun reorderChannels(orderedIds: List<String>) = write { dao ->
        val ordered = saveChannelOrder(dao, orderedIds)
        reorderRemembered(dao, ordered.map { it.id })
    }

    private suspend fun saveChannelOrder(dao: BookDao, orderedIds: List<String>): List<ChannelEntity> {
        val active = dao.channels().filter { it.active }
        val byId = active.associateBy { it.id }
        if (orderedIds.size != orderedIds.distinct().size || !byId.keys.containsAll(orderedIds)) {
            throw BookException(BookError.CHANNEL_UNAVAILABLE)
        }
        val requested = orderedIds.toSet()
        val ordered = orderedIds.mapNotNull(byId::get) + active.filterNot { it.id in requested }
        dao.saveChannels(ordered.mapIndexed { index, channel -> channel.copy(position = index) })
        return ordered
    }

    /** 只调整默认渠道的相对顺序，成员集合保持原样。 */
    private suspend fun reorderRemembered(dao: BookDao, channelOrder: List<String>) {
        val members = dao.rememberedChannels().map { it.channelId }.toSet()
        val ordered = channelOrder.filter { it in members }
        if (ordered.isEmpty()) return
        dao.clearRememberedChannels()
        dao.insertRememberedChannels(ordered.mapIndexed { index, id -> RememberedChannelEntity(id, index) })
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

    override suspend fun setUseWanGrouping(enabled: Boolean) = write { dao ->
        val current = dao.settings() ?: AppSettingsEntity()
        dao.saveSettings(current.copy(useWanGrouping = enabled))
    }

    override suspend fun setDifferenceUsesNet(enabled: Boolean) = write { dao ->
        val current = dao.settings() ?: AppSettingsEntity()
        dao.saveSettings(current.copy(differenceUsesNet = enabled))
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
        pending.snapshots.forEach { dao.saveSnapshot(MonthlyAssetEntity(it.month.toString(), it.registeredAt.toEpochMilli(),
            it.liability.fen, it.receivable.fen)) }
        if (balances.isNotEmpty()) dao.insertBalances(balances)
        pending.incomes.forEach { dao.saveIncome(it.toEntity()) }
        if (mode == ImportMode.REPLACE) {
            val activeIds = pending.activeChannels.map { it.id }.toSet()
            val remembered = existing.settings.defaultChannelIds.filter { it in activeIds }.ifEmpty {
                pending.activeChannels.sortedBy { it.position }.map { it.id }
            }
            dao.insertRememberedChannels(remembered.mapIndexed { index, id -> RememberedChannelEntity(id, index) })
        }
        reconcilePendingSms(dao)
        BookImportResult(mode, pending.channels.size, pending.snapshots.size, pending.incomes.size)
    }
    override suspend fun importSms(parsed: ParsedIcbcIncome, requireAutoEnabled: Boolean): Boolean = write { dao ->
        if (requireAutoEnabled && dao.settings()?.smsAutoImportEnabled != true) return@write false
        val record = stageSmsRecord(dao, parsed)
        if (record.status != SmsIncomeStatus.PENDING.name) return@write false
        val imported = importSmsRecord(dao, record)
        reconcilePendingSms(dao)
        imported
    }

    override fun observeSmsIncomeRecords(): Flow<List<SmsIncomeRecord>> = flow {
        initialize()
        database.withTransaction { reconcilePendingSms(database.bookDao()) }
        emitAll(database.bookDao().observeSmsIncomeRecords().map { rows -> rows.map {
            SmsIncomeRecord(it.fingerprint, it.title, Money(it.amountFen), Instant.ofEpochMilli(it.receivedAtMillis),
                SmsIncomeStatus.valueOf(it.status), it.incomeId)
        } })
    }.flowOn(dispatcher)

    override fun observePendingSmsIncomes(): Flow<List<ParsedIcbcIncome>> = observeSmsIncomeRecords().map { records ->
        records.filter { it.status == SmsIncomeStatus.PENDING }.sortedWith(compareBy({ it.receivedAt }, { it.fingerprint }))
            .map { ParsedIcbcIncome(it.title, it.amount, it.receivedAt, it.fingerprint) }
    }

    override suspend fun smsScanSince(): Instant? = write { dao ->
        dao.settings()?.lastSmsScanAtMillis?.let(Instant::ofEpochMilli)
    }

    override suspend fun stageSmsIncomes(incomes: List<ParsedIcbcIncome>, scannedThrough: Instant?) = write { dao ->
        val settings = dao.settings() ?: return@write
        if (!settings.smsAutoImportEnabled) return@write
        incomes.distinctBy { it.fingerprint }.forEach { stageSmsRecord(dao, it) }
        reconcilePendingSms(dao)
        if (scannedThrough != null) dao.saveSettings(settings.copy(lastSmsScanAtMillis =
            maxOf(settings.lastSmsScanAtMillis ?: Long.MIN_VALUE, scannedThrough.toEpochMilli())))
    }

    override suspend fun resolveSmsIncomes(fingerprints: Set<String>, import: Boolean): SmsResolutionResult = write { dao ->
        var imported = 0
        var ignored = 0
        var matched = 0
        for (fingerprint in fingerprints.sorted()) {
            val record = dao.smsIncomeRecord(fingerprint) ?: continue
            val status = SmsIncomeStatus.valueOf(record.status)
            if (import && status in setOf(SmsIncomeStatus.PENDING, SmsIncomeStatus.IGNORED, SmsIncomeStatus.PROCESSED)) {
                if (importSmsRecord(dao, record)) imported++ else matched++
            } else if (!import && status == SmsIncomeStatus.PENDING) {
                finishSmsRecord(dao, record, SmsIncomeStatus.IGNORED)
                ignored++
            }
        }
        reconcilePendingSms(dao)
        SmsResolutionResult(imported, ignored, matched)
    }

    private suspend fun stageSmsRecord(dao: BookDao, parsed: ParsedIcbcIncome): SmsIncomeRecordEntity {
        if (parsed.title.isBlank() || parsed.title.length > 120) throw BookException(BookError.TITLE_REQUIRED)
        if (parsed.amount.fen <= 0) throw BookException(BookError.INCOME_MUST_BE_POSITIVE)
        val existing = dao.smsIncomeRecord(parsed.fingerprint)
        if (existing != null && existing.status != SmsIncomeStatus.PENDING.name) return existing
        val record = existing ?: SmsIncomeRecordEntity(parsed.fingerprint, parsed.title.trim(), parsed.amount.fen, parsed.receivedAt.toEpochMilli())
        val match = matchingIncome(dao, record.title, record.amountFen, record.receivedAtMillis)
        return when {
            match != null -> finishSmsRecord(dao, record, SmsIncomeStatus.MATCHED, match.id)
            existing == null && dao.hasReceipt(record.fingerprint) -> finishSmsRecord(dao, record, SmsIncomeStatus.PROCESSED)
            else -> record.also { dao.saveSmsIncomeRecord(it) }
        }
    }

    /** 识别时与最终入账时都比较，事务内覆盖手动补录和并发确认的情况。 */
    private suspend fun importSmsRecord(dao: BookDao, record: SmsIncomeRecordEntity): Boolean {
        val match = matchingIncome(dao, record.title, record.amountFen, record.receivedAtMillis)
        if (match != null) {
            finishSmsRecord(dao, record, SmsIncomeStatus.MATCHED, match.id)
            return false
        }
        val income = Income(UUID.randomUUID().toString(), record.title, Money(record.amountFen),
            Instant.ofEpochMilli(record.receivedAtMillis), IncomeSource.SMS)
        validateIncome(dao, income)
        dao.saveIncome(income.toEntity())
        finishSmsRecord(dao, record, SmsIncomeStatus.IMPORTED, income.id)
        return true
    }

    private suspend fun finishSmsRecord(dao: BookDao, record: SmsIncomeRecordEntity, status: SmsIncomeStatus,
                                        incomeId: String? = null): SmsIncomeRecordEntity {
        val updated = record.copy(status = status.name, incomeId = incomeId)
        dao.saveSmsIncomeRecord(updated)
        if (!dao.hasReceipt(record.fingerprint)) dao.insertReceipt(SmsImportReceiptEntity(record.fingerprint))
        return updated
    }

    private suspend fun reconcilePendingSms(dao: BookDao) {
        dao.pendingSmsIncomes().forEach { record ->
            matchingIncome(dao, record.title, record.amountFen, record.receivedAtMillis)?.let { match ->
                finishSmsRecord(dao, record, SmsIncomeStatus.MATCHED, match.id)
            }
        }
    }

    private suspend fun matchingIncome(dao: BookDao, title: String, amountFen: Long, receivedAtMillis: Long,
                                       exceptId: String? = null): IncomeEntity? {
        val date = Instant.ofEpochMilli(receivedAtMillis).atZone(BOOK_ZONE).toLocalDate()
        return dao.matchingIncome(title.trim(), amountFen,
            date.atStartOfDay(BOOK_ZONE).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(BOOK_ZONE).toInstant().toEpochMilli(), exceptId)
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
                Money(record.asset.liabilityFen), Money(record.asset.receivableFen))
        },
        incomes = dao.incomes().map { Income(it.id, it.title, Money(it.amountFen), Instant.ofEpochMilli(it.receivedAtMillis), IncomeSource.valueOf(it.source)) },
        settings = dao.settings().let { settings -> BookSettings(
            smsAutoImportEnabled = settings?.smsAutoImportEnabled == true,
            defaultChannelIds = dao.rememberedChannels().map { it.channelId },
            exportedAt = settings?.lastExportAtMillis?.let(Instant::ofEpochMilli),
            hideOnStartup = settings?.hideOnStartup ?: true,
            allowScreenshots = settings?.allowScreenshots ?: false,
            useWanGrouping = settings?.useWanGrouping ?: false,
            differenceUsesNet = settings?.differenceUsesNet ?: true,
        ) },
    )

    private fun Income.toEntity() = IncomeEntity(id, title, amount.fen, receivedAt.toEpochMilli(), source.name)
}
