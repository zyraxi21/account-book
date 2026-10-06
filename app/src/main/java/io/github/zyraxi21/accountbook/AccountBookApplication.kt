package io.github.zyraxi21.accountbook

import android.app.Application
import io.github.zyraxi21.accountbook.data.local.EncryptedDatabaseFactory
import io.github.zyraxi21.accountbook.data.repository.EncryptedBookRepository
import io.github.zyraxi21.accountbook.data.transfer.BookTransfer
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
}
