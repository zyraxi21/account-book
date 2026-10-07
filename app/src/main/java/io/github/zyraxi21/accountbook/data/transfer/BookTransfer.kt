package io.github.zyraxi21.accountbook.data.transfer

import android.content.Context
import android.net.Uri
import io.github.zyraxi21.accountbook.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/** 文件读写和解码在后台完成；整本导入由仓库以单一事务提交。 */
class BookTransfer(
    private val context: Context,
    private val repository: BookRepository,
    private val clock: () -> Instant = Instant::now,
    random: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private val decoder = BookDecoder(random)

    suspend fun export(uri: Uri, format: ExportFormat, data: BookData): Int = withContext(Dispatchers.IO) {
        val text = when (format) {
            ExportFormat.JSON -> BookExporter.toJson(data, clock())
            ExportFormat.CSV -> BookExporter.toCsv(data)
        }
        val written = context.writeExportText(uri, text)
        repository.recordExport(clock())
        written
    }

    suspend fun import(request: ImportRequest, mode: ImportMode): BookImportResult = withContext(Dispatchers.IO) {
        val text = context.readImportText(request.uri)
        // 内容优先，兼容提供方给历史文件追加了错误后缀的情况。
        val payload = if (text.trimStart().startsWith("{")) decoder.fromJson(text) else decoder.fromCsv(text)
        repository.importBook(payload, mode)
    }
}
