package io.github.zyraxi21.accountbook.sms

import android.Manifest
import android.content.Context
import android.provider.Telephony
import androidx.core.content.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

/** 同时检查运行时授权和系统对读取操作的限制，避免授权开关开启却实际不可读。 */
internal fun hasSmsReadPermission(context: Context): Boolean =
    PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_SMS) == PermissionChecker.PERMISSION_GRANTED

/** 只查询工行号码、时间范围和解析必需列，不修改已读状态或保存短信原文。 */
class AndroidSmsInboxReader(context: Context) : SmsInboxReader {
    private val context = context.applicationContext

    override fun hasPermission() = hasSmsReadPermission(context)

    override suspend fun read(since: Instant?, through: Instant): List<InboxSms> = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw SecurityException()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val selection = "${Telephony.Sms.ADDRESS} IN (?, ?, ?) AND ${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"
        val args = arrayOf("95588", "+8695588", "008695588", (since?.toEpochMilli() ?: 0L).toString(), through.toEpochMilli().toString())
        val cursor = context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args,
            "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC") ?: throw IOException("SMS query returned no cursor")
        cursor.use {
            buildList {
                while (it.moveToNext()) add(InboxSms(it.getString(0).orEmpty(), it.getString(1).orEmpty(), Instant.ofEpochMilli(it.getLong(2))))
            }
        }
    }
}
