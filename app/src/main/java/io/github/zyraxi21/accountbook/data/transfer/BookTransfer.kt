package io.github.zyraxi21.accountbook.data.transfer

import android.content.Context
import android.net.Uri
import io.github.zyraxi21.accountbook.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

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
    suspend fun export(uri: Uri, format: ExportFormat, data: BookData): Int = withContext(Dispatchers.IO) {
        val text = when (format) {
            ExportFormat.JSON -> BookExporter.toJson(data, clock())
            ExportFormat.CSV -> BookExporter.toCsv(data)
        }
        val written = context.writeExportText(uri, text)
        // 只有在导出成功后"最近一次导出"的时间才会被记录，失败不写入任何偏好。
        repository.recordExport(clock())
        written
    }

    suspend fun import(request: ImportRequest, mode: ImportMode): BookImportResult = withContext(Dispatchers.IO) {
        val text = context.readImportText(request.uri)
        // 按内容识别，兼容文件提供方给历史文件追加了错误后缀的情况。
        val payload = if (text.trimStart().startsWith("{")) decoder.fromJson(text) else decoder.fromCsv(text)
        repository.importBook(payload, mode)
    }
}
