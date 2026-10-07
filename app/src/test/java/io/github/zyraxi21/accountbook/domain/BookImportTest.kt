package io.github.zyraxi21.accountbook.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class BookImportTest {
    private val time = Instant.parse("2026-10-07T04:00:00Z")
    private val bank = Channel("bank", "银行", true, 0)
    private val asset = MonthlyAssetSnapshot(time, listOf(ChannelBalance(bank.id, "登记时名称", Money(12345))), Money(100))
    private val income = Income("salary", "工资", Money(10000), time)
    private val data = BookData(listOf(bank), listOf(asset), listOf(income))

    @Test fun importingOwnBackupAgainProducesNoWrites() {
        val decoded = BookDecoder().fromJson(BookExporter.toJson(data, time))
        val changes = prepareBookImport(data, decoded, ImportMode.MERGE)
        assertTrue(changes.channels.isEmpty())
        assertTrue(changes.snapshots.isEmpty())
        assertTrue(changes.incomes.isEmpty())
    }

    @Test fun mergeKeepsExistingMonthAndIncomeAndAddsOnlyNewRecords() {
        val previous = asset.copy(registeredAt = time.atZone(BOOK_ZONE).minusMonths(1).toInstant())
        val changes = prepareBookImport(data, data.copy(snapshots = listOf(asset.copy(liability = Money(900)), previous),
            incomes = listOf(income.copy(amount = Money(1)), income.copy(id = "bonus"))), ImportMode.MERGE)
        assertEquals(listOf(previous), changes.snapshots)
        assertEquals(listOf("bonus"), changes.incomes.map { it.id })
    }

    @Test fun newChannelIsRenamedWithoutChangingHistoricalNames() {
        val name = "渠".repeat(40)
        val existing = BookData(channels = listOf(bank.copy(name = name)))
        val another = bank.copy(id = "new", name = name)
        val incoming = BookData(channels = listOf(another), snapshots = listOf(asset.copy(balances = listOf(ChannelBalance("new", name, Money(1))))))
        val changes = prepareBookImport(existing, incoming, ImportMode.MERGE)
        assertEquals(40, changes.channels.single().name.length)
        assertTrue(changes.channels.single().name.endsWith(" (2)"))
        assertEquals(name, changes.snapshots.single().balances.single().channelName)
    }

    @Test fun sameIdentifierDoesNotReactivateDeletedChannel() {
        val changes = prepareBookImport(data.copy(channels = listOf(bank.copy(active = false))), data, ImportMode.MERGE)
        assertTrue(changes.channels.isEmpty())
    }

    @Test fun replaceReturnsFileContentsAndRejectsUnknownReferencesBeforeWriting() {
        assertEquals(data, prepareBookImport(BookData(), data, ImportMode.REPLACE))
        try {
            prepareBookImport(data, data.copy(channels = emptyList()), ImportMode.REPLACE)
            fail("覆盖文件缺少引用的渠道时必须拒绝")
        } catch (error: BookException) { assertEquals(BookError.IMPORT_INVALID_FIELD, error.error) }
    }

    @Test fun fullCsvRoundTripPreservesAllTablesAndHistoricalChannelNames() {
        val complex = data.copy(channels = listOf(bank.copy(name = "当前名称,\"银行\"")),
            incomes = listOf(income.copy(title = "工资,\"奖金\"\n第二行")))
        val restored = BookDecoder().fromCsv(BookExporter.toCsv(complex))
        assertEquals(complex, restored)
    }

    @Test fun fullCsvSupportsEmptySectionsAndSingleAssetTable() {
        for (book in listOf(BookData(), BookData(channels = listOf(bank)), BookData(incomes = listOf(income)))) {
            assertEquals(book, BookDecoder().fromCsv(BookExporter.toCsv(book)))
        }
        val restored = BookDecoder().fromCsv(BookExporter.toAssetsCsv(data))
        assertEquals(data.snapshots, restored.snapshots)
        assertEquals(bank.id, restored.channels.single().id)
    }

    @Test fun csvColumnOrderDoesNotDetermineTableType() {
        val restored = BookDecoder().fromCsv("日期时间,金额,项目,收入ID,来源\r\n$time,12.34,工资,salary,MANUAL\r\n")
        assertEquals(Money(1234), restored.incomes.single().amount)
        assertEquals(time, restored.incomes.single().receivedAt)
    }

    @Test fun jsonRoundTripAllowsSoftDeletedChannelNameToBeReused() {
        val data = BookData(channels = listOf(bank.copy(active = false), bank.copy(id = "new-bank", position = 1)))
        assertEquals(data, BookDecoder().fromJson(BookExporter.toJson(data, time)))
    }
}
