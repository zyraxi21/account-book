package io.github.zyraxi21.accountbook

import android.app.Application
import android.content.pm.PackageManager
import io.github.zyraxi21.accountbook.data.local.EncryptedDatabaseFactory
import io.github.zyraxi21.accountbook.data.repository.EncryptedBookRepository
import io.github.zyraxi21.accountbook.data.transfer.BookTransfer
import io.github.zyraxi21.accountbook.data.update.ApkDownloader
import io.github.zyraxi21.accountbook.data.update.UpdateRepository
import io.github.zyraxi21.accountbook.domain.AppVersion
import io.github.zyraxi21.accountbook.domain.BookRepository
import io.github.zyraxi21.accountbook.sms.IcbcSmsParser

class AccountBookApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    private val database by lazy { EncryptedDatabaseFactory.open(application) }
    val repository: BookRepository = EncryptedBookRepository(
        databaseProvider = { database },
        defaultChannelNames = listOf(R.string.channel_bank, R.string.channel_alipay, R.string.channel_wechat).map(application::getString),
    )
    val smsParser = IcbcSmsParser()
    val transfer = BookTransfer(application, repository)
    val updater = UpdateRepository()
    val downloader = ApkDownloader(application)

    /**
     * 当前版本号从安装包信息读取，其来源是构建配置的 `versionName`，不在代码中硬编码。
     * 读取失败时返回 null，界面会禁用检查更新而不是给出错误版本。
     */
    val currentVersion: AppVersion? by lazy {
        val raw = runCatching {
            application.packageManager.getPackageInfo(application.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        }.getOrNull()
        AppVersion.parse(raw)
    }
}
