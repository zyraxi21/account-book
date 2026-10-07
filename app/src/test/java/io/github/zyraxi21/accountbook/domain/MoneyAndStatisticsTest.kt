package io.github.zyraxi21.accountbook.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

class MoneyAndStatisticsTest {
    @Test fun decimalArithmeticIsExact() {
        assertEquals(Money(30), Money.parse("0.10") + Money.parse("0.20"))
        assertEquals("0.30", (Money.parse("0.10") + Money.parse("0.20")).inputText())
        assertEquals("12,345.60", Money.parse("12345.6").formatted())
        assertEquals("-0.01", Money(-1).formatted())
    }

    @Test fun invalidAmountsNeverRoundSilently() {
        listOf("", "-1", "+1", "1.001", "1.000", "NaN", "1e3", "1,000.00", ".5", "1.").forEach {
            assertBookError(BookError.INVALID_AMOUNT) { Money.parse(it) }
        }
        assertBookError(BookError.INCOME_MUST_BE_POSITIVE) { Money.parse("0.00", positive = true) }
    }

    @Test fun overflowIsRejected() {
        assertEquals(Long.MAX_VALUE, Money.parse("92233720368547758.07").fen)
        assertBookError(BookError.AMOUNT_OVERFLOW) { Money.parse("92233720368547758.08") }
        assertBookError(BookError.AMOUNT_OVERFLOW) { Money(Long.MAX_VALUE) + Money(1) }
        assertBookError(BookError.AMOUNT_OVERFLOW) { Money(Long.MIN_VALUE) - Money(1) }
    }

    @Test fun differencesAndExpensesIncludeDebtAndCalendarMonthIncome() {
        val december = snapshot("2025-12-31T23:59:00", "10000", "1000")
        val january = snapshot("2026-01-31T23:59:00", "10500", "500")
        val data = BookData(snapshots = listOf(december, january), incomes = listOf(
            income("january", "1500", "2026-01-01T00:00:00"),
            income("december", "2000", "2025-12-31T23:59:59"),
        ))
        val summary = data.summary(YearMonth.of(2026, 1))
        assertEquals(Money.parse("500"), summary.assetDifference)
        assertEquals(Money.parse("500"), summary.estimatedExpense)
        assertEquals(Money.parse("1500"), summary.monthlyIncome)
        assertEquals(Money.parse("3500"), data.cumulativeIncome)
        assertEquals(Money.parse("10000"), january.net)
    }

    @Test fun missingAdjacentMonthDoesNotCompareAcrossAGap() {
        val data = BookData(snapshots = listOf(snapshot("2026-01-31T12:00:00", "100", "0"), snapshot("2026-03-31T12:00:00", "50", "0")))
        assertNull(data.summary(YearMonth.of(2026, 3)).assetDifference)
        assertNull(data.summary(YearMonth.of(2026, 3)).estimatedExpense)
    }

    @Test fun negativeNetAndNegativeEstimatedExpenseRemainSigned() {
        assertEquals(Money(-10000), snapshot("2026-01-31T12:00:00", "100", "200").net)
        val data = BookData(snapshots = listOf(snapshot("2026-01-31T12:00:00", "100", "0"), snapshot("2026-02-28T12:00:00", "200", "0")))
        assertEquals(Money(-10000), data.summary(YearMonth.of(2026, 2)).estimatedExpense)
    }

    @Test fun updatedIncomeChangesTotalsAndExpense() {
        val data = BookData(snapshots = listOf(snapshot("2026-01-31T12:00:00", "100", "0"), snapshot("2026-02-28T12:00:00", "200", "0")),
            incomes = listOf(income("salary", "300", "2026-02-15T12:00:00")))
        assertEquals(Money.parse("200"), data.summary(YearMonth.of(2026, 2)).estimatedExpense)
        val edited = data.copy(incomes = listOf(data.incomes.single().copy(amount = Money.parse("400"))))
        assertEquals(Money.parse("300"), edited.summary(YearMonth.of(2026, 2)).estimatedExpense)
        assertEquals(Money.parse("400"), edited.cumulativeIncome)
    }

    @Test fun rememberedChannelsKeepOrderAndExcludeDeletedChannels() {
        val channels = listOf(Channel("bank", "银行", true, 0), Channel("alipay", "支付宝", true, 1), Channel("wechat", "微信", false, 2))
        val data = BookData(channels = channels, settings = BookSettings(defaultChannelIds = listOf("alipay", "wechat", "bank")))
        assertEquals(listOf("alipay", "bank"), data.nextRegistrationChannels().map { it.id })
    }

    @Test fun incomeIsGroupedByMonthUsingTheBookZone() {
        val data = BookData(incomes = listOf(
            income("salary", "300", "2026-02-15T12:00:00"),
            income("bonus", "100", "2026-02-20T12:00:00"),
            income("january", "50", "2026-01-31T12:00:00"),
        ))
        val february = data.incomesIn(YearMonth.of(2026, 2))
        assertEquals(listOf("bonus", "salary"), february.map { it.id })
        assertEquals(Money.parse("400"), data.incomeIn(YearMonth.of(2026, 2)))
        assertEquals(Money.parse("50"), data.incomeIn(YearMonth.of(2026, 1)))
        assertEquals(Money.ZERO, data.incomeIn(YearMonth.of(2026, 3)))
        // 按月小计与汇总口径一致。
        assertEquals(Money.parse("400"), data.summary(YearMonth.of(2026, 2)).monthlyIncome)
    }

    private fun snapshot(date: String, amount: String, debt: String) = MonthlyAssetSnapshot(
        LocalDateTime.parse(date).atZone(BOOK_ZONE).toInstant(), listOf(ChannelBalance("bank", "银行", Money.parse(amount))), Money.parse(debt))
    private fun income(id: String, amount: String, date: String) = Income(id, "工资", Money.parse(amount), LocalDateTime.parse(date).atZone(BOOK_ZONE).toInstant())
    private fun assertBookError(expected: BookError, block: () -> Unit) {
        try { block(); fail("应当拒绝无效金额") } catch (error: BookException) { assertEquals(expected, error.error) }
    }
}
