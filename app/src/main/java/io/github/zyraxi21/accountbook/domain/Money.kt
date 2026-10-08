package io.github.zyraxi21.accountbook.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 金额以分存储，禁止浮点运算和静默舍入。 */
@JvmInline
value class Money(val fen: Long) : Comparable<Money> {
    operator fun plus(other: Money) = checked { Money(Math.addExact(fen, other.fen)) }
    operator fun minus(other: Money) = checked { Money(Math.subtractExact(fen, other.fen)) }
    override fun compareTo(other: Money) = fen.compareTo(other.fen)
    fun inputText(): String = BigDecimal.valueOf(fen, 2).toPlainString()
    fun formatted(useWanGrouping: Boolean = false): String = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.CHINA)).apply {
        groupingSize = if (useWanGrouping) 4 else 3
        roundingMode = RoundingMode.UNNECESSARY
    }.format(BigDecimal.valueOf(fen, 2))

    companion object {
        val ZERO = Money(0)
        private val inputPattern = Regex("^[0-9]+(?:\\.[0-9]{1,2})?$")

        fun parse(text: String, positive: Boolean = false): Money {
            val value = text.trim()
            if (!inputPattern.matches(value)) throw BookException(BookError.INVALID_AMOUNT)
            val money = checked { Money(BigDecimal(value).movePointRight(2).longValueExact()) }
            if (positive && money.fen <= 0) throw BookException(BookError.INCOME_MUST_BE_POSITIVE)
            return money
        }

        fun sum(values: Iterable<Money>): Money = values.fold(ZERO, Money::plus)

        private inline fun <T> checked(block: () -> T): T = try {
            block()
        } catch (_: ArithmeticException) {
            throw BookException(BookError.AMOUNT_OVERFLOW)
        }
    }
}
