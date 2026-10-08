package io.github.zyraxi21.accountbook.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** 在真实下载管理器中登记测试文件，验证安装入口始终对应当前任务。 */
@RunWith(AndroidJUnit4::class)
class ApkDownloaderTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var manager: DownloadManager
    private lateinit var downloader: ApkDownloader
    private val downloadIds = mutableListOf<Long>()

    @Before fun setup() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        directory = File(application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "apk-install-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(application) {
            override fun getExternalFilesDir(type: String?): File = directory
        }
        manager = context.getSystemService(DownloadManager::class.java)
        downloader = ApkDownloader(context)
    }

    @After fun cleanup() {
        downloadIds.forEach { manager.remove(it) }
        directory.deleteRecursively()
    }

    @Test fun currentDownloadIsInstalledEvenWhenOldFixedFileExists() {
        val oldFile = fixture("account-book-update.apk", "old-release")
        val oldId = completedDownload(oldFile)
        val currentFile = fixture("account-book-update-current.apk", "current-release")
        val currentId = completedDownload(currentFile)

        assertEquals(DownloadState.Completed(currentId), downloader.query(currentId))
        val intent = requireNotNull(downloader.createInstallIntent(currentId))
        assertEquals(manager.getUriForDownloadedFile(currentId), intent.data)
        assertNotEquals(manager.getUriForDownloadedFile(oldId), intent.data)
        assertEquals("content", intent.data?.scheme)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(ApkDownloader.APK_MIME, intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val contents = context.contentResolver.openInputStream(requireNotNull(intent.data))!!.use {
            it.bufferedReader().readText()
        }
        assertEquals("current-release", contents)
        assertEquals("old-release", oldFile.readText())
    }

    @Test fun removedTaskDoesNotFallBackToOldFixedFile() {
        val oldFile = fixture("account-book-update.apk", "old-release")
        completedDownload(oldFile)
        val currentId = completedDownload(fixture("account-book-update-current.apk", "current-release"))
        downloader.cancel(currentId)

        assertEquals(DownloadState.Failed(DownloadError.IO), downloader.query(currentId))
        assertNull(downloader.createInstallIntent(currentId))
        assertEquals("old-release", oldFile.readText())
    }

    private fun fixture(name: String, contents: String): File =
        File(directory, name).apply { writeText(contents) }

    /** 此 API 仅用于构造已完成任务，不发起网络请求或安装测试文件。 */
    @Suppress("DEPRECATION")
    private fun completedDownload(file: File): Long = manager.addCompletedDownload(
        file.name, file.name, false, ApkDownloader.APK_MIME, file.absolutePath, file.length(), true,
    ).also { downloadIds += it }
}
