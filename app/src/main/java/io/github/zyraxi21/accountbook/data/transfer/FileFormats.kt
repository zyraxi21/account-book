package io.github.zyraxi21.accountbook.data.transfer

import android.content.Context
import android.net.Uri
import io.github.zyraxi21.accountbook.domain.BookError
import io.github.zyraxi21.accountbook.domain.BookException

/** 用户通过系统文件选择器选定的导入文件。 */
data class ImportRequest(val uri: Uri, val displayName: String)

/** 导出格式及默认文件名。两者都写入用户选定的位置，不占用应用私有目录。 */
enum class ExportFormat(val json: Boolean, val extension: String, val baseName: String, val mimeType: String) {
    JSON(json = true, extension = "json", baseName = "accountbook", mimeType = "application/json"),
    CSV(json = false, extension = "csv", baseName = "accountbook", mimeType = "text/csv"),
}

/** 提示权限或提供方读取失败时抛出，由界面层翻译为统一文案。 */
internal fun unavailable(error: Throwable): Nothing = throw BookException(BookError.EXPORT_UNAVAILABLE, error)

/** 读取文件内容并做基础校验：空文件、超长文件都在解析前拦下。 */
internal fun Context.readImportText(uri: Uri, maxBytes: Int = 5 * 1024 * 1024): String {
    val bytes = try {
        contentResolver.openInputStream(uri)?.use { stream -> stream.readNBytes(maxBytes + 1) }
    } catch (error: Exception) {
        unavailable(error)
    } ?: unavailable(IllegalStateException("无法读取所选文件"))
    if (bytes.isEmpty()) throw BookException(BookError.IMPORT_EMPTY_FILE)
    if (bytes.size > maxBytes) throw BookException(BookError.FILE_TOO_LARGE)
    // 优先按 UTF-8 解码，并去掉 BOM，兼容 Windows 记事本与常见电子表格导出的 CSV。
    return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
}

/** 覆盖写入用户选定的文档，返回实际写入的字节数。 */
internal fun Context.writeExportText(uri: Uri, text: String): Int {
    val bytes = text.toByteArray(Charsets.UTF_8)
    try {
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
    } catch (error: Exception) {
        unavailable(error)
    } ?: unavailable(IllegalStateException("无法写入所选位置"))
    return bytes.size
}
