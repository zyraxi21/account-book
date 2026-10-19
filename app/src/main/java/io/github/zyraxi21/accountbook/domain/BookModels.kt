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
    /** 债权：借出或应收、尚未回到渠道余额中的钱。旧版数据与旧备份按 0 处理。 */
    val receivable: Money = Money.ZERO,
) {
    val month: YearMonth get() = YearMonth.from(registeredAt.atZone(BOOK_ZONE))

    /** 各渠道余额合计，不含债权。 */
    val channelTotal: Money get() = Money.sum(balances.map { it.amount })

    /** 总资产 = 渠道余额 + 债权。 */
    val total: Money get() = channelTotal + receivable

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

enum class SmsIncomeStatus { PENDING, IMPORTED, IGNORED, MATCHED, PROCESSED }

/** 识别结果和处理状态均在本机加密保存；PROCESSED 表示旧版只留下了去重凭据。 */
data class SmsIncomeRecord(
    val fingerprint: String,
    val title: String,
    val amount: Money,
    val receivedAt: Instant,
    val status: SmsIncomeStatus,
    val incomeId: String? = null,
) {
    val canImport: Boolean get() = status == SmsIncomeStatus.PENDING || status == SmsIncomeStatus.IGNORED || status == SmsIncomeStatus.PROCESSED
}

data class SmsResolutionResult(val imported: Int = 0, val ignored: Int = 0, val matched: Int = 0)

data class BookSettings(
    val smsAutoImportEnabled: Boolean = false,
    /** 上一次成功登记的渠道顺序快照，兼容已有备份；当前登记界面使用启用渠道的排序。 */
    val defaultChannelIds: List<String> = emptyList(),
    /** 最近一次成功导出的时间，仅用于在设置页说明数据去向。 */
    val exportedAt: Instant? = null,
    val hideOnStartup: Boolean = true,
    val allowScreenshots: Boolean = false,
    /** 默认每三位分隔，开启后按中文习惯每四位分隔。 */
    val useWanGrouping: Boolean = false,
    /** 与上月差额的口径：`true` 按净资产计算，`false` 按总资产计算。 */
    val differenceUsesNet: Boolean = true,
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

    /** 累计截至所选月份月底，上海时间下月第一天的收入不计入。 */
    fun cumulativeIncomeThrough(month: YearMonth): Money {
        val endExclusive = month.plusMonths(1).atDay(1).atStartOfDay(BOOK_ZONE).toInstant()
        return Money.sum(incomes.filter { it.receivedAt < endExclusive }.map { it.amount })
    }

    fun summary(month: YearMonth): MonthlySummary {
        val current = snapshot(month)
        val previous = snapshot(month.minusMonths(1))
        val income = incomeIn(month)
        // 差额口径由设置决定；前两个月任一缺失时无从比较，保持为空。
        val difference = if (current != null && previous != null) {
            if (settings.differenceUsesNet) current.net - previous.net else current.total - previous.total
        } else null
        return MonthlySummary(
            current, income, difference,
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

/** 登记使用所有启用渠道及当前排序，旧版勾选记忆不再覆盖设置中的顺序。 */
fun BookData.nextRegistrationChannels(): List<Channel> = activeChannels.sortedBy { it.position }
