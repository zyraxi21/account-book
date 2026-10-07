package io.github.zyraxi21.accountbook.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.zyraxi21.accountbook.R
import java.io.File

/** 下载失败原因，界面据此给出可执行的提示。 */
enum class DownloadError { NO_APK, NO_SPACE, NOT_ALLOWED, IO }

/** 下载任务当前状态。进度取 0..100，未知总量时为 -1。 */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val progress: Int) : DownloadState
    data class Failed(val error: DownloadError) : DownloadState
    /** 已下载完成，等待用户确认安装。 */
    data class Completed(val file: File) : DownloadState
}

/**
 * 通过系统 [DownloadManager] 下载安装包并调起系统安装器。
 *
 * 选DownloadManager 而非自行下载：它能跨进程存活、在系统下载界面可见，
 * 用户可随时取消，且无需自行处理断点与通知权限。
 * 下载文件放在应用私有外部目录，不申请存储权限。
 */
class ApkDownloader(private val context: Context) {
    private val manager = context.getSystemService(DownloadManager::class.java)

    /**
     * 排队下载 [url]。返回下载 ID；无法入队时返回 null。
     * 不做安装权限检查——调用方应在用户确认后先检查再排队。
     */
    fun enqueue(url: String, fileName: String): Long? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val request = DownloadManager.Request(uri).apply {
            setTitle(fileName)
            setDescription(context.getString(R.string.update_downloading))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        return runCatching { manager.enqueue(request) }.getOrNull()
    }

    /** 查询进度；已失败时返回带原因的失败状态。 */
    fun query(downloadId: Long): DownloadState {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = runCatching { manager.query(query) }.getOrNull() ?: return DownloadState.Failed(DownloadError.IO)
        cursor.use {
            if (!it.moveToFirst()) return DownloadState.Failed(DownloadError.IO)
            val status = it.getIntOrZero(DownloadManager.COLUMN_STATUS)
            val reason = it.getIntOrZero(DownloadManager.COLUMN_REASON)
            return when (status) {
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                    val total = it.getLongOrZero(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val done = it.getLongOrZero(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    // 总量未就绪时给 -1，界面显示不确定进度而不是 0%。
                    DownloadState.Running(if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1)
                }
                DownloadManager.STATUS_SUCCESSFUL -> locateFile(downloadId)?.let { DownloadState.Completed(it) }
                    ?: DownloadState.Failed(DownloadError.IO)
                DownloadManager.STATUS_FAILED -> DownloadState.Failed(reason.toDownloadError())
                else -> DownloadState.Running(-1)
            }
        }
    }

    /** 取消下载并清理已落盘的部分文件。 */
    fun cancel(downloadId: Long) {
        runCatching { manager.remove(downloadId) }
    }

    /** 删除已下载的安装包，避免长期占用外部存储。 */
    fun cleanup(file: File) {
        runCatching { file.delete() }
    }

    /**
     * 构造可交给系统安装器的 [Uri]。
     * Android 7.0 起禁止跨应用传递 `file://`，必须走 FileProvider 的 content URI。
     */
    fun installUri(file: File): Uri? {
        val authority = "${context.packageName}.fileprovider"
        // 外部私有目录可能尚未创建，先确保父目录存在。
        if (!file.exists()) file.parentFile?.mkdirs()
        return runCatching {
            FileProvider.getUriForFile(context, authority, file)
        }.getOrNull()
    }

    /** 调起系统安装器安装 [file]；返回是否成功拉起。 */
    fun startInstall(file: File): Boolean {
        val uri = installUri(file) ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 该文件是否允许被安装器读取；不允许时需要引导用户开启未知来源安装。 */
    fun canInstall(file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val packageName = context.packageName
        // 读取自身是否被允许安装未知来源应用的设置。
        return runCatching {
            context.packageManager.canRequestPackageInstalls()
        }.getOrElse {
            // 取不到设置值时用降级判断，避免直接判定不可安装。
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver, INSTALL_NON_MARKET_APPS, 0) == 1 && packageName.isNotEmpty()
        }
    }

    /** 打开未知来源安装的设置页；返回是否成功拉起。 */
    fun openInstallPermissionSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    /** 按约定路径定位已下载的安装包；不存在时返回 null。 */
    private fun locateFile(downloadId: Long): File? {
        // 先确认系统侧确实记录为下载完成，避免未完成时误判。
        val uri = runCatching { manager.getUriForDownloadedFile(downloadId) }.getOrNull() ?: return null
        if (uri == Uri.EMPTY) return null
        val expected = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), CURRENT_FILE_NAME)
        return if (expected.isFile && expected.length() > 0L) expected else null
    }

    private fun Cursor.getIntOrZero(column: String): Int {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getInt(index) else 0
    }

    private fun Cursor.getLongOrZero(column: String): Long {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else 0L
    }

    /**
 * 把 DownloadManager 的失败原因归类。
     * 空间不足单独提示，因为用户清理存储即可解决；其余归为网络或 IO 类错误。
     * 未列举的原因码统一按 IO 处理——DownloadManager 可能新增原因码，
     * 未识别时不应让界面崩溃。
     */
    private fun Int.toDownloadError(): DownloadError = when (this) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> DownloadError.NO_SPACE
        DownloadManager.ERROR_HTTP_DATA_ERROR -> DownloadError.IO
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> DownloadError.IO
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> DownloadError.IO
        DownloadManager.ERROR_CANNOT_RESUME -> DownloadError.IO
        DownloadManager.ERROR_FILE_ERROR -> DownloadError.IO
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> DownloadError.IO
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> DownloadError.IO
        else -> DownloadError.IO
    }

    companion object {
        /** 下载文件名固定，便于安装前定位并清理。 */
        const val CURRENT_FILE_NAME = "account-book-update.apk"
        const val APK_MIME = "application/vnd.android.package-archive"
        private const val INSTALL_NON_MARKET_APPS = "install_non_market_apps"
    }
}