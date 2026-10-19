package io.github.zyraxi21.accountbook.sms

import io.github.zyraxi21.accountbook.domain.BOOK_ZONE
import io.github.zyraxi21.accountbook.domain.BookRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

data class InboxSms(val sender: String, val body: String, val receivedAt: Instant)

interface SmsInboxReader {
    fun hasPermission(): Boolean
    suspend fun read(since: Instant?, through: Instant): List<InboxSms>
}

/** 补查以短信接收时间推进，正文中的银行时间只用于收入日期与指纹。 */
class SmsInboxScanner(
    private val repository: BookRepository,
    private val reader: SmsInboxReader,
    private val parser: IcbcSmsParser = IcbcSmsParser(),
    private val clock: Clock = Clock.system(BOOK_ZONE),
) {
    private val mutex = Mutex()

    suspend fun scan() = mutex.withLock {
        if (!reader.hasPermission()) return@withLock
        val since = repository.smsScanSince()
        val through = clock.instant()
        val parsed = reader.read(since, through).mapNotNull { sms ->
            if (!SmsAssembler.isIcbcSender(sms.sender) || sms.receivedAt > through ||
                (since != null && sms.receivedAt < since)) return@mapNotNull null
            (parser.parse(sms.body, sms.receivedAt) as? SmsParseResult.Success)?.income
        }
        // 清单写入失败或权限被收回时，进度不会越过尚未保存的短信。
        repository.stageSmsIncomes(parsed, through)
    }
}
