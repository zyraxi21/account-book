package io.github.zyraxi21.accountbook.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.sms.ParsedIcbcIncome
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SmsReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<AppCompatActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = ViewModelStore()
    private lateinit var base: ReadOnlyBookRepository
    private lateinit var vm: BookViewModel
    private var dark by mutableStateOf(false)
    private var resolved = emptySet<String>()
    private var imported: Boolean? = null

    @After fun cleanup() { compose.runOnIdle { store.clear() } }

    @Test fun hiddenBatchRequiresRevealAndOnlySelectedIncomesAreSubmitted() {
        launch(2)
        compose.onNodeWithTag("sms_review_hidden").assertIsDisplayed()
        compose.onAllNodesWithText("测试收入1", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("¥ 100.00", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.sms_review_reveal)).performClick()
        compose.onNodeWithText("测试收入1").assertIsDisplayed()
        capture("sms-review-light")
        compose.onNodeWithTag("sms_review_2").performClick().assertIsOff()
        compose.onNodeWithText(context.getString(R.string.sms_review_import, 1)).performClick()
        compose.runOnIdle { assertEquals(setOf("1"), resolved); assertEquals(true, imported) }
        compose.onNodeWithTag("sms_review_list").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tab_settings)).performClick()
        compose.onAllNodesWithText(context.getString(R.string.sms_notification_permission_hint)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.sms_scan_recheck)).assertCountEquals(0)
        capture("sms-settings-compact", dialog = false)
        compose.onNodeWithText(context.getString(R.string.sms_history_entry_pending, 1)).performScrollTo().performClick()
        compose.onNodeWithTag("sms_filter_ALL").assertWidthIsAtLeast(48.dp)
        compose.onNodeWithTag("sms_ignore_2").performClick()
        compose.runOnIdle { assertEquals(setOf("2"), resolved); assertEquals(false, imported); assertTrue(base.pendingSms.value.isEmpty()) }
        compose.onNodeWithTag("sms_filter_IGNORED").performClick()
        compose.onNodeWithTag("sms_record_2").assertIsDisplayed()
        compose.onNodeWithTag("sms_record_1").assertDoesNotExist()
        capture("sms-history-light", dialog = false)
        compose.onNodeWithTag("sms_import_2").performClick()
        compose.runOnIdle { assertEquals(setOf("2"), resolved); assertEquals(true, imported) }
        compose.onNodeWithTag("sms_record_2").assertDoesNotExist()
        compose.onNodeWithTag("sms_filter_RECORDED").performClick()
        compose.onNodeWithTag("sms_record_2").assertIsDisplayed()
        compose.onAllNodesWithTag("sms_import_2").assertCountEquals(0)
    }

    @Test fun longBatchScrollsAndDeferralPreservesItsSelectionInDarkMode() {
        launch(50)
        compose.onNodeWithText(context.getString(R.string.sms_review_reveal)).performClick()
        compose.runOnIdle { dark = true }
        capture("sms-review-dark")
        compose.onNodeWithTag("sms_review_list").performScrollToKey("50")
        compose.onNodeWithTag("sms_review_50").performClick().assertIsOff()
        compose.onNodeWithText(context.getString(R.string.sms_review_later)).performClick()
        compose.runOnIdle { assertEquals(50, base.pendingSms.value.size); assertEquals(49, vm.smsReview.value.selected.size); assertTrue(resolved.isEmpty()) }
        compose.onNodeWithTag("sms_review_list").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tab_income)).performClick()
        compose.onNodeWithText(context.getString(R.string.sms_history_title)).performClick()
        compose.onNodeWithTag("sms_history_list").performScrollToKey("50")
        compose.onNodeWithTag("sms_ignore_50").performClick()
        compose.onNodeWithTag("sms_filter_IGNORED").performClick()
        compose.onNodeWithTag("sms_record_50").assertIsDisplayed()
        capture("sms-history-dark", dialog = false)
    }

    @Test fun hiddenHistoryHasNoFinancialSemanticsAndDoesNotReopenTheBatchOnReveal() {
        launch(2)
        compose.onNodeWithText(context.getString(R.string.sms_history_view_all)).performClick()
        compose.onNodeWithTag("sms_history_hidden").assertIsDisplayed()
        compose.onAllNodesWithText("测试收入1", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("¥ 100.00", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("2026-10-19 12:00", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("sms_history_list").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.sms_history_reveal)).performClick()
        compose.onNodeWithTag("sms_record_1").assertIsDisplayed()
        compose.onNodeWithTag("sms_review_list").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_hide)).performClick()
        compose.onNodeWithTag("sms_history_list").assertDoesNotExist()
        compose.onAllNodesWithText("测试收入1", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun launch(count: Int) {
        compose.runOnIdle {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            base = ReadOnlyBookRepository(BookData(settings = BookSettings(smsAutoImportEnabled = true)))
            base.pendingSms.value = (1..count).map { ParsedIcbcIncome("测试收入$it", Money(10000),
                Instant.parse("2026-10-19T04:00:00Z"), it.toString()) }
            val repository = object : BookRepository by base {
                override suspend fun resolveSmsIncomes(fingerprints: Set<String>, import: Boolean): SmsResolutionResult {
                    resolved = fingerprints
                    imported = import
                    val records = base.observeSmsIncomeRecords().first().filter { it.fingerprint in fingerprints && it.canImport }
                    base.pendingSms.value = base.pendingSms.value.filterNot { it.fingerprint in fingerprints }
                    base.smsRecords.value = base.smsRecords.value.filterNot { it.fingerprint in fingerprints } + records.map {
                        it.copy(status = if (import) SmsIncomeStatus.IMPORTED else SmsIncomeStatus.IGNORED)
                    }
                    return if (import) SmsResolutionResult(imported = records.size) else SmsResolutionResult(ignored = records.size)
                }
            }
            vm = BookViewModel(repository)
            store.put("review", vm)
        }
        compose.setContent { AccountBookTheme(darkTheme = dark) { BookApp(vm) } }
    }

    private fun capture(name: String, dialog: Boolean = true) {
        val file = File(context.getExternalFilesDir(null), "ui-verification/$name.png")
        file.parentFile!!.mkdirs()
        val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val bitmap = node.captureToImage().asAndroidBitmap()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
