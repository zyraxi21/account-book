package io.github.zyraxi21.accountbook.sms

import io.github.zyraxi21.accountbook.domain.BOOK_ZONE
import io.github.zyraxi21.accountbook.domain.BookError
import io.github.zyraxi21.accountbook.domain.BookException
import io.github.zyraxi21.accountbook.domain.Money
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class ParsedIcbcIncome(
    val title: String,
    val amount: Money,
    val receivedAt: Instant,
    val fingerprint: String,
)
sealed interface SmsParseResult {
    data class Success(val income: ParsedIcbcIncome) : SmsParseResult
    data class Failure(val error: BookError) : SmsParseResult
}

/** 正文仅在内存中使用，余额只用于区分同分钟内的不同收入。 */
class IcbcSmsParser {
    private val pattern = Regex(
        "^尾号([0-9]{4})卡([0-9]{1,2})月([0-9]{1,2})日([0-9]{2}):([0-9]{2})" +
            "工商银行收入\\(([^()]{1,120})\\)([0-9,]+(?:\\.[0-9]{1,2})?)元," +
            "余额([0-9,]+(?:\\.[0-9]{1,2})?)元[。.]?【工商银行】$",
    )
    private val groupedAmount = Regex("^(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?$")

    fun parse(body: String, referenceTime: Instant, zone: ZoneId = BOOK_ZONE): SmsParseResult {
        if (body.length > 4096) return SmsParseResult.Failure(BookError.SMS_FORMAT)
        val punctuation = body.trim().replace('（', '(').replace('）', ')').replace('，', ',').replace('：', ':')
        val normalized = punctuation.replace(Regex("\\s+"), "")
        val match = pattern.matchEntire(normalized) ?: return SmsParseResult.Failure(BookError.SMS_FORMAT)
        val (card, month, day, hour, minute, _, amountText, balanceText) = match.destructured
        val title = Regex("工商银行\\s*收入\\s*\\(([^()]{1,120})\\)").find(punctuation)?.groupValues?.get(1)
            ?.trim()?.replace(Regex("\\s+"), " ") ?: return SmsParseResult.Failure(BookError.SMS_FORMAT)
        if (!groupedAmount.matches(amountText) || !groupedAmount.matches(balanceText)) {
            return SmsParseResult.Failure(BookError.SMS_FORMAT)
        }
        return try {
            val amount = Money.parse(amountText.replace(",", ""), positive = true)
            val balance = Money.parse(balanceText.replace(",", ""))
            val year = referenceTime.atZone(zone).year
            val candidates = listOf(year, year - 1).mapNotNull { candidateYear ->
                try {
                    LocalDateTime.of(candidateYear, month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
                        .atZone(zone).toInstant()
                } catch (_: DateTimeException) { null }
            }.filter { it <= referenceTime.plusSeconds(300) }
            val receivedAt = candidates.maxOrNull() ?: return SmsParseResult.Failure(BookError.SMS_DATE)
            val canonical = "$card|${receivedAt.atZone(zone).toLocalDateTime()}|$title|${amount.fen}|${balance.fen}"
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            SmsParseResult.Success(ParsedIcbcIncome(title, amount, receivedAt, fingerprint))
        } catch (error: BookException) {
            SmsParseResult.Failure(error.error)
        }
    }
}

data class SmsPart(val sender: String, val body: String, val sentAt: Instant)
data class SmsEnvelope(val body: String, val referenceTime: Instant)

object SmsAssembler {
    fun isIcbcSender(sender: String): Boolean = sender.trim().replace(" ", "")
        .removePrefix("+86").removePrefix("0086") == "95588"

    fun assemble(parts: List<SmsPart>): SmsEnvelope? {
        if (parts.isEmpty() || parts.size > 64 || parts.any { !isIcbcSender(it.sender) }) return null
        val body = parts.joinToString("") { it.body }
        if (body.length > 4096) return null
        return SmsEnvelope(body, parts.minOf { it.sentAt })
    }
}
