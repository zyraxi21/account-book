package io.github.zyraxi21.accountbook.domain

import io.github.zyraxi21.accountbook.sms.ParsedIcbcIncome
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.YearMonth

interface BookRepository {
    fun observeBook(): Flow<BookData>
    suspend fun saveAsset(snapshot: MonthlyAssetSnapshot, originalMonth: YearMonth? = null)
    suspend fun deleteAsset(month: YearMonth)
    suspend fun saveIncome(income: Income, importFingerprint: String? = null)
    suspend fun deleteIncome(id: String)
    /** 新建渠道并返回它，供登记弹窗立即插入勾选行。 */
    suspend fun addChannel(name: String): Channel
    suspend fun renameChannel(id: String, name: String)
    suspend fun deleteChannel(id: String)

    /** 按给定顺序重排启用渠道；未列出的启用渠道保持相对顺序排在其后。 */
    suspend fun reorderChannels(orderedIds: List<String>)
    suspend fun setSmsAutoImport(enabled: Boolean)
    suspend fun setHideOnStartup(enabled: Boolean)
    suspend fun setAllowScreenshots(enabled: Boolean)
    suspend fun importSms(parsed: ParsedIcbcIncome, requireAutoEnabled: Boolean = true): Boolean

    /** 记录一次成功导出的时间。 */
    suspend fun recordExport(exportedAt: Instant)

    /** 按用户选定的模式一次性写入，返回实际新增条数。 */
    suspend fun importBook(data: BookData, mode: ImportMode = ImportMode.MERGE): BookImportResult
}

/** 导入模式：全量替换，或与现有数据合并。合并时渠道和收入按标识去重，资产表按月份跳过。 */
enum class ImportMode { MERGE, REPLACE }

data class BookImportResult(val mode: ImportMode, val channels: Int, val snapshots: Int, val incomes: Int)
