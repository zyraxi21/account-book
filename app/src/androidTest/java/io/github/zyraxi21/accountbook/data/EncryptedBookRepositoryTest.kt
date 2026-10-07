package io.github.zyraxi21.accountbook.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.zyraxi21.accountbook.data.crypto.DatabaseKeyStore
import io.github.zyraxi21.accountbook.data.local.BookDatabase
import io.github.zyraxi21.accountbook.data.local.EncryptedDatabaseFactory
import io.github.zyraxi21.accountbook.data.repository.EncryptedBookRepository
import io.github.zyraxi21.accountbook.data.transfer.BookTransfer
import io.github.zyraxi21.accountbook.data.transfer.ExportFormat
import io.github.zyraxi21.accountbook.data.transfer.ImportRequest
import android.net.Uri
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.sms.IcbcSmsParser
import io.github.zyraxi21.accountbook.sms.SmsParseResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedBookRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var directory: File
    private lateinit var alias: String
    private lateinit var database: BookDatabase
    private lateinit var repository: EncryptedBookRepository
    private val now = Instant.parse("2026-10-06T04:35:00Z")

    @Before fun setup() = runBlocking {
        val id = UUID.randomUUID().toString()
        directory = File(context.noBackupFilesDir, "instrumentation/$id")
        alias = "accountbook.test.$id"
        reopen()
        book()
        Unit
    }

    @After fun cleanup() {
        database.close()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        directory.deleteRecursively()
    }

    @Test fun channelMemoryAndHistoricalNamesSurviveRestart() = runBlocking {
        val initial = book()
        assertEquals(listOf("银行", "支付宝", "微信"), initial.activeChannels.map { it.name })
        val bank = initial.activeChannels[0]
        val alipay = initial.activeChannels[1]
        repository.saveAsset(MonthlyAssetSnapshot(now, listOf(ChannelBalance(alipay.id, alipay.name, Money(200)),
            ChannelBalance(bank.id, bank.name, Money(100))), Money.ZERO))
        repository.renameChannel(bank.id, "历史名称验证")
        repository.deleteChannel(alipay.id)
        database.close(); reopen()
        val restored = book()
        assertEquals(listOf(bank.id), restored.settings.defaultChannelIds)
        assertEquals(listOf("支付宝", "银行"), restored.snapshots.single().balances.map { it.channelName })
        assertEquals("历史名称验证", restored.activeChannels.first { it.id == bank.id }.name)
        assertFalse(restored.activeChannels.any { it.id == alipay.id })
        assertEquals(3, restored.channels.size)
    }

    @Test fun duplicateManualAndAutomaticImportsDoNotReappearAfterDeletion() = runBlocking {
        repository.setSmsAutoImport(true)
        val parsed = parsed()
        assertTrue(repository.importSms(parsed))
        assertFalse(repository.importSms(parsed))
        val income = book().incomes.single()
        assertEquals(Money.parse("100.00"), income.amount)
        repository.deleteIncome(income.id)
        assertFalse(repository.importSms(parsed))
        try {
            repository.saveIncome(Income("manual-duplicate", parsed.title, parsed.amount, parsed.receivedAt, IncomeSource.SMS), parsed.fingerprint)
            fail("重复粘贴应被拒绝")
        } catch (error: BookException) { assertEquals(BookError.SMS_DUPLICATE, error.error) }
        assertTrue(book().incomes.isEmpty())
        database.close(); reopen()
        assertFalse(repository.importSms(parsed))
    }

    @Test fun disabledAutoImportDoesNotConsumeTheReceipt() = runBlocking {
        assertFalse(repository.importSms(parsed()))
        assertTrue(book().incomes.isEmpty())
        repository.setSmsAutoImport(true)
        assertTrue(repository.importSms(parsed()))
    }

    @Test fun concurrentImportAndAssetRegistrationAreAtomic() = runBlocking {
        repository.setSmsAutoImport(true)
        val channel = book().activeChannels.first()
        val parsed = parsed()
        val results = coroutineScope {
            val snapshot = async { repository.saveAsset(MonthlyAssetSnapshot(now,
                listOf(ChannelBalance(channel.id, channel.name, Money.parse("50"))), Money.ZERO)) }
            val imports = List(12) { async { repository.importSms(parsed) } }.awaitAll()
            snapshot.await()
            imports
        }
        assertEquals(1, results.count { it })
        val data = book()
        assertEquals(1, data.incomes.size)
        assertEquals(Money.parse("50"), data.snapshots.single().total)
        assertEquals(listOf(channel.id), data.settings.defaultChannelIds)
    }

    @Test fun failedAssetUpdateRollsBackChannelMemory() = runBlocking {
        val initial = book()
        val channel = initial.activeChannels.first()
        repository.saveAsset(MonthlyAssetSnapshot(now, listOf(ChannelBalance(channel.id, channel.name, Money(100))), Money.ZERO))
        val saved = book()
        try {
            repository.saveAsset(saved.snapshots.single().copy(balances = listOf(ChannelBalance("missing", "不存在", Money(200)))), saved.snapshots.single().month)
            fail("不存在的渠道应被拒绝")
        } catch (error: BookException) { assertEquals(BookError.CHANNEL_UNAVAILABLE, error.error) }
        assertEquals(saved, book())
    }

    @Test fun databaseAndSidecarsContainNoKnownPlaintextAndFrameworkSqliteCannotRead() = runBlocking {
        repository.addChannel("加密渠道验证")
        repository.saveIncome(Income("encryption-test", "加密收入验证项目", Money(12345), now))
        assertEquals("加密收入验证项目", book().incomes.single().title)
        val databaseFile = File(directory, "accountbook.db")
        assertFalse(databaseFile.readBytes().take(16).toByteArray().toString(Charsets.US_ASCII).startsWith("SQLite format 3"))
        val markers = listOf("加密收入验证项目", "加密渠道验证").map { it.toByteArray().toString(Charsets.ISO_8859_1) }
        directory.listFiles().orEmpty().filter { it.isFile }.forEach { file ->
            val binary = file.readBytes().toString(Charsets.ISO_8859_1)
            markers.forEach { assertFalse("文件中不应出现账务明文", binary.contains(it)) }
        }
        try {
            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { plain ->
                plain.rawQuery("SELECT name FROM sqlite_master", null).use { it.moveToFirst() }
            }
            fail("明文 SQLite 不应读取加密账本")
        } catch (_: SQLiteException) { }
    }

    @Test fun corruptedKeyPreservesDatabaseAndDoesNotGenerateAnotherKey() = runBlocking {
        repository.saveIncome(Income("preserve", "保留原数据", Money(1), now))
        database.close()
        val databaseFile = File(directory, "accountbook.db")
        val original = databaseFile.readBytes()
        val keyFile = DatabaseKeyStore(directory, alias).keyFile
        val damaged = keyFile.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        keyFile.writeBytes(damaged)
        try { EncryptedDatabaseFactory.open(context, directory, alias); fail("损坏密钥应被拒绝")
        } catch (error: BookException) { assertEquals(BookError.STORAGE_CORRUPTED, error.error) }
        assertArrayEquals(original, databaseFile.readBytes())
        assertArrayEquals(damaged, keyFile.readBytes())
    }

    @Test fun missingKeyPreservesExistingDatabase() = runBlocking {
        repository.saveIncome(Income("preserve", "保留原数据", Money(1), now))
        database.close()
        val file = File(directory, "accountbook.db")
        val original = file.readBytes()
        assertTrue(DatabaseKeyStore(directory, alias).keyFile.delete())
        try { EncryptedDatabaseFactory.open(context, directory, alias); fail("缺失密钥应被拒绝")
        } catch (error: BookException) { assertEquals(BookError.STORAGE_KEY_MISSING, error.error) }
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun missingKeystoreEntryDoesNotRecreateItForAnExistingEnvelope() = runBlocking {
        repository.saveIncome(Income("entry", "密钥条目验证", Money(1), now))
        database.close()
        val keyFile = DatabaseKeyStore(directory, alias).keyFile
        val original = keyFile.readBytes()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        try { EncryptedDatabaseFactory.open(context, directory, alias); fail("缺失封装密钥应被拒绝")
        } catch (error: BookException) { assertEquals(BookError.STORAGE_KEY_MISSING, error.error) }
        assertFalse(keyStore.containsAlias(alias))
        assertArrayEquals(original, keyFile.readBytes())
    }

    @Test fun missingDatabasePreservesItsEnvelopeAndDoesNotCreateAnEmptyBook() = runBlocking {
        repository.saveIncome(Income("missing-database", "数据库缺失验证", Money(1), now))
        database.close()
        val keyFile = DatabaseKeyStore(directory, alias).keyFile
        val originalKey = keyFile.readBytes()
        val file = File(directory, "accountbook.db")
        assertTrue(file.delete())
        try { EncryptedDatabaseFactory.open(context, directory, alias); fail("缺失数据库不得创建空账本")
        } catch (error: BookException) { assertEquals(BookError.STORAGE_DATABASE_MISSING, error.error) }
        assertFalse(file.exists())
        assertArrayEquals(originalKey, keyFile.readBytes())
    }

    @Test fun corruptedDatabaseIsNotDeletedOrRebuilt() = runBlocking {
        repository.saveIncome(Income("corrupt-database", "数据库损坏验证", Money(1), now))
        database.close()
        val file = File(directory, "accountbook.db")
        val damaged = file.readBytes().also { bytes -> bytes.fill(0, 0, minOf(4096, bytes.size)) }
        file.writeBytes(damaged)
        val keyFile = DatabaseKeyStore(directory, alias).keyFile
        val originalKey = keyFile.readBytes()
        try { reopen(); book(); fail("损坏的数据库不应作为空账本打开")
        } catch (_: SQLiteException) { }
        database.close()
        assertArrayEquals(damaged, file.readBytes())
        assertArrayEquals(originalKey, keyFile.readBytes())
    }

    @Test fun interruptedFirstCreationResumesWithTheOriginalPassword() = runBlocking {
        val interruptedDirectory = File(directory, "interrupted-setup")
        val interruptedAlias = "$alias.interrupted"
        try {
            val keyStore = DatabaseKeyStore(interruptedDirectory, interruptedAlias)
            val originalPassword = keyStore.loadOrCreate(databaseExists = false)
            try {
                assertTrue(keyStore.pendingKeyFile.exists())
                assertFalse(keyStore.keyFile.exists())
                val resumedPassword = keyStore.loadOrCreate(databaseExists = false)
                try { assertArrayEquals(originalPassword, resumedPassword) } finally { resumedPassword.fill(0) }
                val resumed = EncryptedDatabaseFactory.open(context, interruptedDirectory, interruptedAlias)
                try {
                    val resumedRepository = EncryptedBookRepository({ resumed }, listOf("银行", "支付宝", "微信"))
                    assertEquals(3, resumedRepository.observeBook().first().activeChannels.size)
                    assertTrue(keyStore.keyFile.exists())
                    assertFalse(keyStore.pendingKeyFile.exists())
                    val persistedPassword = keyStore.loadOrCreate(databaseExists = true)
                    try { assertArrayEquals(originalPassword, persistedPassword) } finally { persistedPassword.fill(0) }
                } finally { resumed.close() }
            } finally { originalPassword.fill(0) }
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(interruptedAlias) }
        }
    }

    @Test fun editingAndMovingMonthlyRecordsUpdatesSummariesWithoutDuplicates() = runBlocking {
        val bank = book().activeChannels.first()
        val previousTime = Instant.parse("2026-09-30T12:00:00Z")
        val previous = MonthlyAssetSnapshot(previousTime, listOf(ChannelBalance(bank.id, bank.name, Money(10000))), Money(1000))
        val current = MonthlyAssetSnapshot(now, listOf(ChannelBalance(bank.id, bank.name, Money(11000))), Money(2000))
        repository.saveAsset(previous)
        repository.saveAsset(current)
        repository.saveIncome(Income("editable", "汇总更新验证", Money(3000), now))
        assertEquals(Money(3000), book().summary(current.month).estimatedExpense)
        repository.saveIncome(Income("editable", "汇总更新验证", Money(5000), now))
        assertEquals(Money(5000), book().summary(current.month).estimatedExpense)
        try { repository.saveAsset(current); fail("同月不得重复登记")
        } catch (error: BookException) { assertEquals(BookError.MONTH_EXISTS, error.error) }
        val moved = previous.copy(registeredAt = previousTime.atZone(BOOK_ZONE).minusMonths(1).toInstant())
        repository.saveAsset(moved, previous.month)
        assertNull(book().snapshot(previous.month))
        assertNull(book().summary(current.month).estimatedExpense)
        repository.deleteAsset(moved.month)
        assertEquals(1, book().snapshots.size)
        database.close(); reopen()
        assertEquals(Money(5000), book().cumulativeIncome)
    }

    @Test fun ownJsonAndCsvBackupsMergeWithoutDuplicatePrimaryKeys() = runBlocking {
        val bank = book().activeChannels.first()
        repository.saveAsset(MonthlyAssetSnapshot(now, listOf(ChannelBalance(bank.id, bank.name, Money(12345))), Money(100)))
        repository.saveIncome(Income("round-trip", "导入往返验证", Money(1234), now))
        val original = book()
        for (copy in listOf(BookDecoder().fromJson(BookExporter.toJson(original, now)), BookDecoder().fromCsv(BookExporter.toCsv(original)))) {
            assertEquals(BookImportResult(ImportMode.MERGE, 0, 0, 0), repository.importBook(copy, ImportMode.MERGE))
            assertEquals(original, book())
        }
    }

    @Test fun mergeInsertsNewChannelsBeforeTheirBalancesAndKeepsLocalMonths() = runBlocking {
        val bank = book().activeChannels.first()
        val original = MonthlyAssetSnapshot(now, listOf(ChannelBalance(bank.id, bank.name, Money(100))), Money.ZERO)
        repository.saveAsset(original)
        val incoming = importedBook()
        assertEquals(BookImportResult(ImportMode.MERGE, 1, 1, 1), repository.importBook(incoming, ImportMode.MERGE))
        assertEquals(original, book().snapshot(original.month))
        assertEquals("旧渠道名称", book().snapshot(incoming.snapshots.single().month)!!.balances.single().channelName)
        assertEquals(BookImportResult(ImportMode.MERGE, 0, 0, 0), repository.importBook(incoming, ImportMode.MERGE))
    }

    @Test fun explicitReplacePreservesDevicePreferencesAndSmsReceipts() = runBlocking {
        repository.setSmsAutoImport(true)
        repository.setHideOnStartup(false)
        repository.setAllowScreenshots(true)
        assertTrue(repository.importSms(parsed()))
        val incoming = importedBook()
        assertEquals(BookImportResult(ImportMode.REPLACE, 1, 1, 1), repository.importBook(incoming, ImportMode.REPLACE))
        val replaced = book()
        assertEquals(incoming.channels, replaced.channels)
        assertEquals(incoming.snapshots, replaced.snapshots)
        assertEquals(incoming.incomes, replaced.incomes)
        assertFalse(replaced.settings.hideOnStartup)
        assertTrue(replaced.settings.allowScreenshots)
        assertTrue(replaced.settings.smsAutoImportEnabled)
        assertFalse(repository.importSms(parsed()))
        database.close(); reopen()
        assertEquals(replaced, book())
    }

    @Test fun invalidReplaceLeavesOriginalDataAndMemoryUntouched() = runBlocking {
        repository.saveIncome(Income("preserve-import", "覆盖失败保留验证", Money(100), now))
        val original = book()
        try {
            repository.importBook(importedBook().copy(channels = emptyList()), ImportMode.REPLACE)
            fail("缺少渠道的备份应拒绝覆盖")
        } catch (error: BookException) { assertEquals(BookError.IMPORT_INVALID_FIELD, error.error) }
        assertEquals(original, book())
    }

    @Test fun fileTransferRoundTripHonorsContentAndRecordsBothExportFormats() = runBlocking {
        repository.importBook(importedBook(), ImportMode.REPLACE)
        val transfer = BookTransfer(context, repository, clock = { now })
        for (format in ExportFormat.entries) {
            val file = File(directory, "round-trip.${format.extension}")
            val original = book()
            assertTrue(transfer.export(Uri.fromFile(file), format, original) > 0)
            assertEquals(now, book().settings.exportedAt)
            // 旧版本曾产生 .csv.json，实际内容必须比后缀有更高优先级。
            val outcome = transfer.import(ImportRequest(Uri.fromFile(file), "backup.csv.json"), ImportMode.MERGE)
            assertEquals(BookImportResult(ImportMode.MERGE, 0, 0, 0), outcome)
            assertEquals(original.copy(settings = original.settings.copy(exportedAt = now)), book())
            assertTrue(file.delete())
        }
    }

    @Test fun versionOneAndTwoMigrateWithoutChangingFinancialData() = runBlocking {
        for (version in listOf(2, 1)) {
            repository.saveIncome(Income("migration-$version", "迁移保留验证", Money(123), now))
            repository.setSmsAutoImport(true)
            val original = book()
            database.close()
            val password = DatabaseKeyStore(directory, alias).loadOrCreate(databaseExists = true)
            try {
                val factory = SupportOpenHelperFactory(password)
                val config = SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(File(directory, "accountbook.db").absolutePath)
                    .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    }).build()
                factory.create(config).use { helper ->
                    val db = helper.writableDatabase
                    db.execSQL("ALTER TABLE app_settings RENAME TO settings_before_migration")
                    val extraColumn = if (version == 2) ", lastExportAtMillis INTEGER" else ""
                    db.execSQL("CREATE TABLE app_settings (id INTEGER NOT NULL PRIMARY KEY, smsAutoImportEnabled INTEGER NOT NULL$extraColumn)")
                    db.execSQL("INSERT INTO app_settings (id, smsAutoImportEnabled) SELECT id, smsAutoImportEnabled FROM settings_before_migration")
                    db.execSQL("DROP TABLE settings_before_migration")
                    db.execSQL("PRAGMA user_version = $version")
                }
            } finally { password.fill(0) }
            reopen()
            val migrated = book()
            assertEquals(original.channels, migrated.channels)
            assertEquals(original.snapshots, migrated.snapshots)
            assertEquals(original.incomes, migrated.incomes)
            assertEquals(original.settings.defaultChannelIds, migrated.settings.defaultChannelIds)
            assertTrue(migrated.settings.hideOnStartup)
            assertFalse(migrated.settings.allowScreenshots)
            assertTrue(migrated.settings.smsAutoImportEnabled)
        }
    }

    @Test fun addChannelReturnsTheCreatedChannelForImmediateSelection() = runBlocking {
        val created = repository.addChannel("  招商银行  ")
        assertEquals("招商银行", created.name)
        assertTrue(created.active)
        assertEquals(3, created.position)
        assertEquals(created, book().channels.first { it.id == created.id })
    }

    @Test fun reorderChangesRegistrationOrderButNotHistoricalBalances() = runBlocking {
        val initial = book()
        val bank = initial.activeChannels[0]
        val alipay = initial.activeChannels[1]
        repository.saveAsset(MonthlyAssetSnapshot(now, listOf(
            ChannelBalance(bank.id, bank.name, Money(100)), ChannelBalance(alipay.id, alipay.name, Money(200))), Money.ZERO))
        val historical = book().snapshots.single()
        repository.reorderChannels(listOf(alipay.id, bank.id))
        assertEquals(listOf(alipay.id, bank.id), book().activeChannels.take(2).map { it.id })
        assertEquals(listOf(alipay.id, bank.id), book().nextRegistrationChannels().take(2).map { it.id })
        // 回看旧月份时渠道顺序保持登记当时的样子。
        assertEquals(historical, book().snapshots.single())
        database.close(); reopen()
        assertEquals(listOf(alipay.id, bank.id), book().activeChannels.take(2).map { it.id })
        assertEquals(listOf(alipay.id, bank.id), book().nextRegistrationChannels().take(2).map { it.id })
    }

    @Test fun reorderRejectsUnknownOrRepeatedChannelsWithoutChangingAnything() = runBlocking {
        val initial = book()
        val bank = initial.activeChannels[0]
        for (invalid in listOf(listOf("missing-channel"), listOf(bank.id, bank.id))) {
            try {
                repository.reorderChannels(invalid)
                fail("非法排序应被拒绝")
            } catch (error: BookException) { assertEquals(BookError.CHANNEL_UNAVAILABLE, error.error) }
        }
        assertEquals(initial, book())
    }

    @Test fun registrationSortIsRememberedAndDeletedChannelsAreExcludedFromFutureCards() = runBlocking {
        val alipay = book().activeChannels[1]
        repository.saveAsset(MonthlyAssetSnapshot(now, listOf(ChannelBalance(alipay.id, alipay.name, Money(200))), Money.ZERO))
        assertEquals(listOf(alipay.id), book().settings.defaultChannelIds)
        assertEquals(alipay.id, book().nextRegistrationChannels().first().id)
        // 删除渠道后，默认列表不再包含它。
        repository.deleteChannel(alipay.id)
        assertEquals(emptyList<String>(), book().settings.defaultChannelIds)
        assertEquals(2, book().nextRegistrationChannels().size)
        assertFalse(book().nextRegistrationChannels().any { it.id == alipay.id })
        database.close(); reopen()
        assertEquals(emptyList<String>(), book().settings.defaultChannelIds)
    }

    private fun importedBook(): BookData {
        val time = Instant.parse("2026-09-30T12:00:00Z")
        return BookData(channels = listOf(Channel("imported-channel", "导入渠道", true, 0)),
            snapshots = listOf(MonthlyAssetSnapshot(time, listOf(ChannelBalance("imported-channel", "旧渠道名称", Money(999))), Money(100))),
            incomes = listOf(Income("imported-income", "导入验证项目", Money(321), time)))
    }

    private fun reopen() {
        database = EncryptedDatabaseFactory.open(context, directory, alias)
        repository = EncryptedBookRepository({ database }, listOf("银行", "支付宝", "微信"))
    }
    private suspend fun book() = withTimeout(15_000) { repository.observeBook().first() }
    private fun parsed() = (IcbcSmsParser().parse("尾号1234卡10月6日12:34工商银行收入(工资)100.00元，余额5000.00元。【工商银行】", now) as SmsParseResult.Success).income
}
