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

    /** 已经有账本时才允许初始化渠道；空账本交给导入流程直接建表，避免凭空多出默认渠道。 */
    private suspend fun initializeIfNeeded() {
        if (database.bookDao().channels().isEmpty()) initialize()
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

    /**
     * 整本导入。解码已在数据层之外完成，这里只做一次事务内的整表重组，
     * 任一条记录违反约束都会让整个事务回滚，不会留下半份数据。
     *
     * 覆盖模式保留原有的渠道记忆与短信自动登记开关：
     * 它们描述的是"这台设备怎么用"，而不是被导入的账务内容。
     */
    override suspend fun importBook(data: BookData): ImportMode = write { dao ->
        initializeIfNeeded()
        val existing = readBook(dao)
        val mode = if (existing.channels.isEmpty() && existing.snapshots.isEmpty() && existing.incomes.isEmpty()) {
            ImportMode.REPLACE
        } else {
            ImportMode.MERGE
        }
        val snapshot = when (mode) {
            ImportMode.MERGE -> merge(existing, data)
            ImportMode.REPLACE -> replace(data)
        }
        persist(dao, snapshot, mode)
        mode
    }

    private fun merge(existing: BookData, incoming: BookData): BookData {
        val channels = existing.channels.associateBy { it.id }.toMutableMap()
        val order = existing.channels.map { it.id }.toMutableList()
        val names = channels.values.map { it.name.lowercase() }.toMutableSet()
        incoming.channels.forEach { channel ->
            val current = channels[channel.id]
            if (current != null) {
                // 同一标识以现有记录为准，避免导入把仍在使用的渠道改名或隐藏。
                if (!current.active && channel.active) channels[channel.id] = current.copy(active = true)
                return@forEach
            }
            val name = channel.name.trim()
            // 名称冲突时保留原有渠道，新记录自动改名而不是覆盖。
            var candidate = name
            var suffix = 2
            while (candidate.lowercase() in names) { candidate = "$name ($suffix)"; suffix++ }
            if (candidate.length > 40) candidate = "$name ($suffix)".takeLast(40)
            names.add(candidate.lowercase())
            channels[channel.id] = channel.copy(name = candidate, active = channel.active)
            order.add(channel.id)
        }
        val snapshots = existing.snapshots.associateBy { it.month }.toMutableMap()
        // 现有资产表优先，导入文件不覆盖本机已经登记的月份。
        incoming.snapshots.forEach { snapshots.putIfAbsent(it.month, it) }
        val incomes = existing.incomes.associateBy { it.id }.toMutableMap()
        incoming.incomes.forEach { incomes.putIfAbsent(it.id, it) }
        return BookData(
            channels = order.mapNotNull(channels::get),
            snapshots = snapshots.values.toList(),
            incomes = incomes.values.toList(),
            settings = existing.settings,
        )
    }

    private fun replace(incoming: BookData) = incoming.copy(settings = BookSettings())

    private suspend fun persist(dao: BookDao, data: BookData, mode: ImportMode) {
        if (mode == ImportMode.REPLACE) {
            dao.clearRememberedChannels()
            dao.deleteAllBalances()
            dao.deleteAllSnapshots()
            dao.deleteAllChannels()
            dao.deleteAllIncomes()
        }
        val channels = data.channels.associateBy { it.id }
        if (channels.size != data.channels.size) throw BookException(BookError.IMPORT_INVALID_FIELD)
        // 未出现在导入文件里的余额必须能在现有渠道中找到，否则外键约束会直接失败。
        val known = if (mode == ImportMode.REPLACE) channels.keys else dao.channels().map { it.id }.toSet()
        val balances = data.snapshots.sortedBy { it.month }.flatMap { snapshot ->
            val month = snapshot.month.toString()
            if (dao.snapshot(month) != null) throw BookException(BookError.IMPORT_DUPLICATE_MONTH)
            snapshot.balances.mapIndexed { index, balance ->
                if (balance.channelId !in known) throw BookException(BookError.CHANNEL_UNAVAILABLE)
                ChannelBalanceEntity(month, balance.channelId, balance.channelName, balance.amount.fen, index)
            }
        }
        if (channels.isNotEmpty()) dao.insertChannels(data.channels.map { ChannelEntity(it.id, it.name, it.active, it.position) })
        data.snapshots.forEach { dao.saveSnapshot(MonthlyAssetEntity(it.month.toString(), it.registeredAt.toEpochMilli(), it.liability.fen)) }
        if (balances.isNotEmpty()) dao.insertBalances(balances)
        data.incomes.forEach { dao.saveIncome(IncomeEntity(it.id, it.title, it.amount.fen, it.receivedAt.toEpochMilli(), it.source.name)) }
        if (mode == ImportMode.REPLACE) {
            // 渠道记忆随渠道一起重组，只保留仍启用的部分。
            val remembered = data.channels.filter { it.active }.sortedBy { it.position }
            if (remembered.isNotEmpty()) dao.insertRememberedChannels(remembered.map { RememberedChannelEntity(it.id, it.position) })
        }
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
        settings = BookSettings(dao.settings()?.smsAutoImportEnabled == true, dao.rememberedChannels().map { it.channelId },
            dao.settings()?.lastExportAtMillis?.let(Instant::ofEpochMilli)),
    )

    private fun Income.toEntity() = IncomeEntity(id, title, amount.fen, receivedAt.toEpochMilli(), source.name)
}
