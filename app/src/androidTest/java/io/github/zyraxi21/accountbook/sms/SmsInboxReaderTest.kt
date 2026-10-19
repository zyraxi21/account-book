package io.github.zyraxi21.accountbook.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.zyraxi21.accountbook.domain.BOOK_ZONE
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.BookRepository
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/** 通过系统短信提供方验证真实的查询、权限及时间边界，而非只测试解析器替身。 */
@RunWith(AndroidJUnit4::class)
class SmsInboxReaderTest {
    @Test fun grantedRuntimePermissionWithBlockedAppOperationDoesNotAdvanceTheScan() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageName = context.packageName
        shell("pm grant $packageName ${Manifest.permission.READ_SMS}")
        val previousMode = Regex("READ_SMS: ([a-z_]+)").find(shell("cmd appops get --uid $packageName READ_SMS"))
            ?.groupValues?.get(1) ?: "default"
        try {
            shell("cmd appops set --uid $packageName READ_SMS ignore")
            assertEquals(PackageManager.PERMISSION_GRANTED, ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS))
            val reader = AndroidSmsInboxReader(context)
            assertFalse("系统拦截读取操作时不能只根据授权开关判断可读", reader.hasPermission())
            val repository = ReadOnlyBookRepository(BookData())
            assertNull(SmsInboxScanner(repository, reader).scan())
        } finally {
            shell("cmd appops set --uid $packageName READ_SMS $previousMode")
        }
    }

    @Test fun systemInboxFeedsTheStartupScannerIncludingTheBoundaryMessage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shell("pm grant ${context.packageName} ${Manifest.permission.READ_SMS}")
        val reader = AndroidSmsInboxReader(context)
        assertTrue("测试 APK 需由安装器放行读取短信权限", reader.hasPermission())
        val through = Instant.now()
        val messages = reader.read(null, through)
        val marker = InstrumentationRegistry.getArguments().getString("smsProbeMarker")
        val probe = if (marker == null) emptyList() else messages.filter { it.body.contains(marker) }
        if (marker != null) assertEquals("应找回应用停止期间收到的两条短信", 2, probe.size)
        var checkpoint = probe.minOfOrNull { it.receivedAt }
        var staged = emptyList<ParsedIcbcIncome>()
        val repository = object : BookRepository by ReadOnlyBookRepository(BookData()) {
            override suspend fun smsScanSince() = checkpoint
            override suspend fun stageSmsIncomes(incomes: List<ParsedIcbcIncome>, scannedThrough: Instant?) {
                staged = incomes
                checkpoint = scannedThrough
            }
        }
        SmsInboxScanner(repository, reader, clock = Clock.fixed(through, BOOK_ZONE)).scan()
        assertEquals(through, checkpoint)
        if (marker != null) {
            assertEquals(setOf("$marker-A", "$marker-B"), staged.filter { it.title.startsWith(marker) }.map { it.title }.toSet())
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).use { it.readBytes().toString(Charsets.UTF_8) }
}
