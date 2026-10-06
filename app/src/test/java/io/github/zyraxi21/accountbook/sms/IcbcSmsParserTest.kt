package io.github.zyraxi21.accountbook.sms

import io.github.zyraxi21.accountbook.domain.BOOK_ZONE
import io.github.zyraxi21.accountbook.domain.BookError
import io.github.zyraxi21.accountbook.domain.Money
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class IcbcSmsParserTest {
    private val parser = IcbcSmsParser()
    private val reference = local("2026-10-06T12:35:00")
    private val body = "尾号1234卡10月6日12:34工商银行收入(工资)12345.67元，余额54321.00元。【工商银行】"

    @Test fun parsesSpecifiedFormatAndDoesNotConfuseIncomeWithBalance() {
        val parsed = success(body, reference)
        assertEquals("工资", parsed.title)
        assertEquals(Money.parse("12345.67"), parsed.amount)
        assertEquals(local("2026-10-06T12:34:00"), parsed.receivedAt)
        assertEquals(64, parsed.fingerprint.length)
    }

    @Test fun supportsFullWidthPunctuationAndGroupedAmounts() {
        val variant = body.replace("(工资)", "（工资）").replace("12:34", "12：34").replace("12345.67", "12,345.67") + "\n"
        assertEquals(success(body, reference), success(variant, reference))
    }

    @Test fun decemberMessageReceivedInJanuaryUsesPreviousYear() {
        val parsed = success(body.replace("10月6日12:34", "12月31日23:59"), local("2026-01-01T00:01:00"))
        assertEquals(local("2025-12-31T23:59:00"), parsed.receivedAt)
    }

    @Test fun invalidDateAndTimeAreRejected() {
        listOf("13月1日12:00", "2月30日12:00", "10月6日24:00", "10月6日12:60").forEach { date ->
            assertEquals(SmsParseResult.Failure(BookError.SMS_DATE), parser.parse(body.replace("10月6日12:34", date), reference))
        }
    }

    @Test fun spendingDifferentBankAndMalformedAmountsAreRejected() {
        listOf(body.replace("收入", "支出"), body.replace("【工商银行】", "【招商银行】"), body.replace("12345.67", "12,34.67"),
            body.replace("12345.67", "1.234"), body.replace("工资", ""), "余额123.00元。【工商银行】").forEach {
            assertTrue(parser.parse(it, reference) is SmsParseResult.Failure)
        }
    }

    @Test fun zeroIncomeIsRejected() {
        assertEquals(SmsParseResult.Failure(BookError.INCOME_MUST_BE_POSITIVE), parser.parse(body.replace("12345.67", "0.00"), reference))
    }

    @Test fun spacesInsideProjectNameArePreserved() {
        assertEquals("工资 奖金", success(body.replace("(工资)", "(工资 奖金)"), reference).title)
    }

    @Test fun fingerprintDistinguishesBalanceCardAndYear() {
        val fingerprint = success(body, reference).fingerprint
        assertNotEquals(fingerprint, success(body.replace("54321.00", "54322.00"), reference).fingerprint)
        assertNotEquals(fingerprint, success(body.replace("1234卡", "4321卡"), reference).fingerprint)
        assertNotEquals(fingerprint, success(body, local("2025-10-06T12:35:00")).fingerprint)
    }

    @Test fun assemblesMultipartMessagesAndRejectsForeignSenders() {
        val parts = listOf(SmsPart("+8695588", body.take(25), reference), SmsPart("95588", body.drop(25), reference))
        assertEquals(body, SmsAssembler.assemble(parts)?.body)
        assertTrue(parser.parse(SmsAssembler.assemble(parts)!!.body, reference) is SmsParseResult.Success)
        assertNull(SmsAssembler.assemble(parts.map { it.copy(sender = "10086") }))
        assertNull(SmsAssembler.assemble(emptyList()))
        assertFalse(SmsAssembler.isIcbcSender("195588"))
        assertFalse(SmsAssembler.isIcbcSender("955880"))
    }

    private fun success(text: String, time: Instant) = (parser.parse(text, time) as SmsParseResult.Success).income
    private fun local(date: String) = LocalDateTime.parse(date).atZone(BOOK_ZONE).toInstant()
}
