package io.github.zyraxi21.accountbook.data.transfer

import android.content.Context
import android.net.Uri
import io.github.zyraxi21.accountbook.domain.*
import java.time.Instant

/** 一次导入的结果，用于在界面上说明到底写入了什么。 */
data class ImportOutcome(
    val mode: ImportMode,
    val channels: Int,
    val snapshots: Int,
    val incomes: Int,
)

/**
 * 通过系统文件选择器（SAF）读写的导入导出入口。
 *
 * 导入顺序固定为"先完成全部解码，再进入单个数据库事务"，因此格式不合法的文件
 * 不会留下半份数据；覆盖模式下的事务也会在任一条记录失败时整体回滚。
 */
class BookTransfer(
    private val context: Context,
    private val repository: BookRepository,
    private val clock: () -> Instant = Instant::now,
    private val random: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private val decoder = BookDecoder(random)

    /** 导出到用户选定的位置，返回写入的字节数。 */
    suspend fun export(uri: Uri, format: ExportFormat, data: BookData): Int {
        val text = when (format) {
            ExportFormat.JSON -> BookExporter.toJson(data, clock())
            ExportFormat.CSV -> listOf(
                BookExporter.toAssetsCsv(data),
                "",
                BookExporter.toChannelsCsv(data),
                "",
                BookExporter.toIncomesCsv(data),
            ).joinToString("\r\n")
        }
        val written = context.writeExportText(uri, text)
        // 只有在导出成功后"最近一次导出"的时间才会被记录，失败不写入任何偏好。
        if (format.json) repository.recordExport(clock())
        return written
    }

    suspend fun import(request: ImportRequest): ImportOutcome {
        val text = context.readImportText(request.uri)
        val csv = looksLikeCsv(request, text)
        val payload = if (csv) decodeCsv(text) else decoder.fromJson(text)
        val mode = repository.importBook(payload)
        return ImportOutcome(mode, payload.channels.size, payload.snapshots.size, payload.incomes.size)
    }

    /** 文件来源不可靠时按内容判断：以 `{` 开头视为 JSON，否则按 CSV 解析。 */
    private fun looksLikeCsv(request: ImportRequest, text: String): Boolean = when (request.displayName.substringAfterLast('.', "").lowercase()) {
        "json" -> false
        "csv" -> true
        else -> text.trimStart().firstOrNull() != '{'
    }

    /**
     * 先按资产表解析；若文件其实是渠道表或收入明细，再依次尝试，
     * 避免要求用户手动选择"这个 CSV 是什么"。
     */
    private fun decodeCsv(text: String): BookData {
        val header = parseCsvRecords(text).firstOrNull()?.cells?.joinToString(",")?.replace("\uFEFF", "").orEmpty()
        val normalized = header.lowercase()
        return when {
            normalized.startsWith(BookExporter.ASSET_HEADER.substringBefore(",").lowercase()) -> decoder.fromAssetsCsv(text)
            normalized.startsWith("渠道id") -> decoder.fromChannelsCsv(text)
            normalized.startsWith("收入id") -> decoder.fromIncomesCsv(text)
            normalized.startsWith("month") -> decoder.fromAssetsCsv(text)
            normalized.startsWith("channelid") -> decoder.fromChannelsCsv(text)
            normalized.startsWith("incomeid") -> decoder.fromIncomesCsv(text)
            else -> throw BookException(
                BookError.IMPORT_UNKNOWN_FORMAT,
                IllegalArgumentException("无法识别的 CSV 表头：$header"),
            )
        }
    }
}
