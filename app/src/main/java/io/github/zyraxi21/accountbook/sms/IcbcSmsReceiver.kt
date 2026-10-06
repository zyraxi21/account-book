package io.github.zyraxi21.accountbook.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.zyraxi21.accountbook.AccountBookApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant

/** 只接受系统的短信广播，短时后台任务不将正文放入明文任务队列。 */
class IcbcSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent).map {
                SmsPart(it.originatingAddress.orEmpty(), it.messageBody.orEmpty(), Instant.ofEpochMilli(it.timestampMillis))
            }
        } catch (_: RuntimeException) { return }
        val envelope = SmsAssembler.assemble(parts) ?: return
        val application = context.applicationContext as AccountBookApplication
        val result = application.container.smsParser.parse(envelope.body, envelope.referenceTime)
        if (result !is SmsParseResult.Success) return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) { application.container.repository.importSms(result.income) }
            } catch (_: Exception) {
                // 入账失败不写入去重凭据，用户仍可通过粘贴短信再次登记。
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
