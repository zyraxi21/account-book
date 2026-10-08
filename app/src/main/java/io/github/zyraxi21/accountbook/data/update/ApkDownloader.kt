package io.github.zyraxi21.accountbook.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import io.github.zyraxi21.accountbook.R
import java.util.UUID

/** 下载失败原因，界面据此给出可执行的提示。 */
enum class DownloadError { NO_APK, NO_SPACE, NOT_ALLOWED, IO }

/** 下载任务当前状态。进度取 0..100，未知总量时为 -1。 */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val progress: Int) : DownloadState
    data class Failed(val error: DownloadError) : DownloadState
    /** 已下载完成，等待用户确认安装。 */
    data class Completed(val downloadId: Long) : DownloadState
}

/** 调起系统安装器的结果，供界面决定是否引导授权。 */
sealed interface InstallOutcome {
    /** 已成功拉起系统安装器。 */
    data object Started : InstallOutcome
    /** 尚未获得安装未知来源应用的授权，需先引导至系统设置。 */
    data object PermissionRequired : InstallOutcome
    /** 已授权但仍无法拉起安装器。 */
    data object Failed : InstallOutcome
}

/**
 * 通过系统 [DownloadManager] 下载安装包并调起系统安装器。
 *
 * 下载任务在系统下载界面可见，文件放在应用私有外部目录。
 * 安装时按下载 ID 取得系统提供的 content URI。
 */
class ApkDownloader(private val context: Context) {
    private val manager = context.getSystemService(DownloadManager::class.java)

    /**
     * 排队下载 [url]。返回下载 ID；无法入队时返回 null。
     * 不做安装权限检查——调用方应在用户确认后先检查再排队。
     */
    fun enqueue(url: String): Long? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        // 每次下载使用独立文件，重试及不同版本不会复用遗留安装包。
        val fileName = "account-book-update-${UUID.randomUUID()}.apk"
        val request = DownloadManager.Request(uri).apply {
            setTitle(context.getString(R.string.app_name))
            setDescription(context.getString(R.string.update_downloading))
            setMimeType(APK_MIME)
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
                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Completed(downloadId)
                DownloadManager.STATUS_FAILED -> DownloadState.Failed(reason.toDownloadError())
                else -> DownloadState.Running(-1)
            }
        }
    }

    /** 取消下载并清理已落盘的部分文件。 */
    fun cancel(downloadId: Long) {
        runCatching { manager.remove(downloadId) }
    }

    /** 仅使用当前下载任务的地址；任务失效时不回退到其他安装包。 */
    internal fun createInstallIntent(downloadId: Long): Intent? {
        val uri = runCatching { manager.getUriForDownloadedFile(downloadId) }.getOrNull() ?: return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 调起系统安装器安装 [downloadId] 对应的已完成任务。 */
    fun install(downloadId: Long): InstallOutcome {
        if (!canInstall()) return InstallOutcome.PermissionRequired
        val intent = createInstallIntent(downloadId) ?: return InstallOutcome.Failed
        return if (runCatching { context.startActivity(intent) }.isSuccess) InstallOutcome.Started
        else InstallOutcome.Failed
    }

    /** 是否已允许本应用请求安装未知来源应用。 */
    private fun canInstall(): Boolean {
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

    /** 打开未知来源安装的授权页；返回是否成功拉起。 */
    fun openInstallPermissionSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        // 部分厂商 ROM 不实现按包跳转的设置页，逐级回退到全局设置与安全页。
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) return true
        }
        return false
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
        const val APK_MIME = "application/vnd.android.package-archive"
        private const val INSTALL_NON_MARKET_APPS = "install_non_market_apps"
    }
}
