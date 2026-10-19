package io.github.zyraxi21.accountbook.sms

import io.github.zyraxi21.accountbook.domain.BOOK_ZONE
import io.github.zyraxi21.accountbook.domain.BookRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

data class InboxSms(val sender: String, val body: String, val receivedAt: Instant)

data class SmsScanSummary(val checkedCount: Int, val recognizedCount: Int)

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

    /** 主动重新补查不使用旧进度，已处理短信仍由加密凭据去重。无权限时返回 null。 */
    suspend fun scan(recheck: Boolean = false): SmsScanSummary? = mutex.withLock {
        if (!reader.hasPermission()) return@withLock null
        val since = if (recheck) null else repository.smsScanSince()
        val through = clock.instant()
        val messages = reader.read(since, through)
        val parsed = messages.mapNotNull { sms ->
            if (!SmsAssembler.isIcbcSender(sms.sender) || sms.receivedAt > through ||
                (since != null && sms.receivedAt < since)) return@mapNotNull null
            (parser.parse(sms.body, sms.receivedAt) as? SmsParseResult.Success)?.income
        }.distinctBy { it.fingerprint }
        // 清单写入失败或权限被收回时，进度不会越过尚未保存的短信。
        repository.stageSmsIncomes(parsed, through)
        SmsScanSummary(messages.size, parsed.size)
    }
}
