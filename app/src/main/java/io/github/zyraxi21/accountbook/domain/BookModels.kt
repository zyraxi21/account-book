package io.github.zyraxi21.accountbook.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

val BOOK_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

enum class BookError {
    INVALID_AMOUNT, INCOME_MUST_BE_POSITIVE, AMOUNT_OVERFLOW, TITLE_REQUIRED,
    CHANNEL_REQUIRED, CHANNEL_NAME_REQUIRED, CHANNEL_NAME_EXISTS, CHANNEL_UNAVAILABLE,
    MONTH_EXISTS, RECORD_NOT_FOUND, INVALID_DATE, SMS_FORMAT, SMS_DATE, SMS_DUPLICATE,
    STORAGE_UNAVAILABLE, STORAGE_KEY_MISSING, STORAGE_DATABASE_MISSING, STORAGE_CORRUPTED,
}

class BookException(val error: BookError, cause: Throwable? = null) : Exception(error.name, cause)

data class Channel(val id: String, val name: String, val active: Boolean, val position: Int)
data class ChannelBalance(val channelId: String, val channelName: String, val amount: Money)
data class MonthlyAssetSnapshot(
    val registeredAt: Instant,
    val balances: List<ChannelBalance>,
    val liability: Money,
) {
    val month: YearMonth get() = YearMonth.from(registeredAt.atZone(BOOK_ZONE))
    val total: Money get() = Money.sum(balances.map { it.amount })
    val net: Money get() = total - liability
}

enum class IncomeSource { MANUAL, SMS }
data class Income(
    val id: String,
    val title: String,
    val amount: Money,
    val receivedAt: Instant,
    val source: IncomeSource = IncomeSource.MANUAL,
)
data class BookSettings(
    val smsAutoImportEnabled: Boolean = false,
    val rememberedChannelIds: List<String> = emptyList(),
)
data class BookData(
    val channels: List<Channel> = emptyList(),
    val snapshots: List<MonthlyAssetSnapshot> = emptyList(),
    val incomes: List<Income> = emptyList(),
    val settings: BookSettings = BookSettings(),
) {
    val activeChannels: List<Channel> get() = channels.filter { it.active }
    val cumulativeIncome: Money get() = Money.sum(incomes.map { it.amount })
    fun snapshot(month: YearMonth) = snapshots.firstOrNull { it.month == month }

    fun summary(month: YearMonth): MonthlySummary {
        val current = snapshot(month)
        val previous = snapshot(month.minusMonths(1))
        val income = Money.sum(incomes.filter {
            YearMonth.from(it.receivedAt.atZone(BOOK_ZONE)) == month
        }.map { it.amount })
        return MonthlySummary(
            current, income,
            if (current != null && previous != null) current.total - previous.total else null,
            if (current != null && previous != null) previous.net + income - current.net else null,
        )
    }
}
data class MonthlySummary(
    val snapshot: MonthlyAssetSnapshot?,
    val monthlyIncome: Money,
    val assetDifference: Money?,
    val estimatedExpense: Money?,
)

/** 只有成功提交的渠道选择才作为下次登记的模板。 */
fun BookData.nextRegistrationChannels(): List<Channel> {
    val active = activeChannels.associateBy { it.id }
    return settings.rememberedChannelIds.mapNotNull(active::get)
}
