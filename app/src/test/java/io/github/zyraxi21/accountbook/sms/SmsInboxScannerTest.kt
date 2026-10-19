package io.github.zyraxi21.accountbook.sms

import io.github.zyraxi21.accountbook.domain.BOOK_ZONE
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import io.github.zyraxi21.accountbook.domain.BookRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant

class SmsInboxScannerTest {
    private val through = Instant.parse("2026-10-19T05:00:00Z")
    private val since = through.minusSeconds(3600)
    private var checkpoint: Instant? = since
    private var staged = emptyList<ParsedIcbcIncome>()
    private var stageCalls = 0
    private var queried = false
    private val repository = object : BookRepository by ReadOnlyBookRepository(BookData()) {
        override suspend fun smsScanSince() = checkpoint
        override suspend fun stageSmsIncomes(incomes: List<ParsedIcbcIncome>, scannedThrough: Instant?) {
            stageCalls++
            staged = incomes
            checkpoint = scannedThrough
        }
    }

    @Test fun scansTheInclusiveBoundaryAndKeepsEveryValidIncomeAtTheSameTime() = runTest {
        val messages = listOf(
            sms("工资", since), sms("奖金", since), sms("跨行转入", since.plusSeconds(1)),
            sms("旧短信", since.minusMillis(1)), sms("未来短信", through.plusMillis(1)),
            sms("其他号码", since).copy(sender = "10086"), sms("支出短信", since).copy(body = "工商银行支出100元"),
        )
        val result = scanner(messages).scan()
        assertTrue(queried)
        assertEquals(listOf("工资", "奖金", "跨行转入"), staged.map { it.title })
        assertEquals(3, staged.map { it.fingerprint }.distinct().size)
        assertEquals(through, checkpoint)
        assertEquals(SmsScanSummary(messages.size, 3), result)
    }

    @Test fun noPermissionDoesNotQueryOrAdvanceTheCheckpoint() = runTest {
        assertNull(scanner(listOf(sms("工资", since)), permission = false).scan())
        assertFalse(queried)
        assertEquals(0, stageCalls)
        assertEquals(since, checkpoint)
    }

    @Test fun failedQueryDoesNotConsumeTheScanWindow() = runTest {
        val reader = object : SmsInboxReader {
            override fun hasPermission() = true
            override suspend fun read(since: Instant?, through: Instant): List<InboxSms> = throw IOException()
        }
        try {
            SmsInboxScanner(repository, reader, clock = Clock.fixed(through, BOOK_ZONE)).scan()
            fail("查询失败应保留原进度")
        } catch (_: IOException) { }
        assertEquals(since, checkpoint)
        assertEquals(0, stageCalls)
    }

    @Test fun receivedTimeDeterminesTheYearForDelayedCrossYearSms() = runTest {
        checkpoint = null
        val sms = InboxSms("+8695588", "尾号1234卡12月31日23:59工商银行收入(跨年收入)100.00元，余额500.00元。【工商银行】",
            Instant.parse("2026-01-01T00:01:00Z"))
        scanner(listOf(sms)).scan()
        assertEquals(Instant.parse("2025-12-31T15:59:00Z"), staged.single().receivedAt)
    }

    @Test fun explicitRecheckFindsIncomeOlderThanTheSavedScanCheckpoint() = runTest {
        val received = since.minusSeconds(60)
        var queriedSince: Instant? = checkpoint
        val reader = object : SmsInboxReader {
            override fun hasPermission() = true
            override suspend fun read(since: Instant?, through: Instant): List<InboxSms> {
                queriedSince = since
                return listOf(sms("之前漏读的收入", received))
            }
        }
        val result = SmsInboxScanner(repository, reader, clock = Clock.fixed(through, BOOK_ZONE)).scan(recheck = true)
        assertNull(queriedSince)
        assertEquals("之前漏读的收入", staged.single().title)
        assertEquals(SmsScanSummary(1, 1), result)
        assertEquals(through, checkpoint)
    }

    @Test fun recognizedCountDistinguishesNoMessagesFromUnrecognizedOrDuplicateMessages() = runTest {
        val valid = sms("工资", since)
        val rejected = valid.copy(body = "工商银行支出100元")
        assertEquals(SmsScanSummary(3, 1), scanner(listOf(valid, valid, rejected)).scan())
        assertEquals(1, staged.size)
        assertEquals(SmsScanSummary(0, 0), scanner(emptyList()).scan())
    }

    private fun scanner(messages: List<InboxSms>, permission: Boolean = true): SmsInboxScanner {
        val reader = object : SmsInboxReader {
            override fun hasPermission() = permission
            override suspend fun read(since: Instant?, through: Instant): List<InboxSms> {
                queried = true
                assertEquals(checkpoint, since)
                assertEquals(this@SmsInboxScannerTest.through, through)
                return messages
            }
        }
        return SmsInboxScanner(repository, reader, clock = Clock.fixed(through, BOOK_ZONE))
    }

    private fun sms(title: String, time: Instant) = InboxSms("95588",
        "尾号1234卡10月19日12:00工商银行收入($title)100.00元，余额500.00元。【工商银行】", time)
}
