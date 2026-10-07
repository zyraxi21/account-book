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
    IMPORT_EMPTY_FILE, IMPORT_UNKNOWN_FORMAT, IMPORT_MALFORMED, IMPORT_UNSUPPORTED_VERSION,
    IMPORT_MISSING_FIELD, IMPORT_INVALID_FIELD, IMPORT_INVALID_DATE, IMPORT_DUPLICATE_MONTH,
    IMPORT_DUPLICATE_TITLE, IMPORT_TOO_LARGE, IMPORT_LIMIT_EXCEEDED,
    EXPORT_UNAVAILABLE, FILE_TOO_LARGE,
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
    /** 下次登记的默认勾选渠道，顺序即登记顺序；仅由设置页和上一次成功登记写入。 */
    val defaultChannelIds: List<String> = emptyList(),
    /** 最近一次成功导出的时间，仅用于在设置页说明数据去向。 */
    val exportedAt: Instant? = null,
    val hideOnStartup: Boolean = true,
    val allowScreenshots: Boolean = false,
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

    /** 指定月份的收入，按发生时间倒序；资产页与本页共用同一套月份口径。 */
    fun incomesIn(month: YearMonth): List<Income> = incomes
        .filter { YearMonth.from(it.receivedAt.atZone(BOOK_ZONE)) == month }
        .sortedByDescending { it.receivedAt }

    fun incomeIn(month: YearMonth): Money = Money.sum(incomesIn(month).map { it.amount })

    fun summary(month: YearMonth): MonthlySummary {
        val current = snapshot(month)
        val previous = snapshot(month.minusMonths(1))
        val income = incomeIn(month)
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
    return settings.defaultChannelIds.mapNotNull(active::get)
}
