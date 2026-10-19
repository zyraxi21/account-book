package io.github.zyraxi21.accountbook.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
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
        compose.onNodeWithText(context.getString(R.string.sms_review_pending, 1)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.sms_review_select_all)).performClick()
        compose.onNodeWithText(context.getString(R.string.sms_review_ignore, 1)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(setOf("2"), resolved); assertEquals(false, imported); assertTrue(base.pendingSms.value.isEmpty()) }
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
    }

    private fun launch(count: Int) {
        compose.runOnIdle {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            base = ReadOnlyBookRepository(BookData(settings = BookSettings(smsAutoImportEnabled = true)))
            base.pendingSms.value = (1..count).map { ParsedIcbcIncome("测试收入$it", Money(10000),
                Instant.parse("2026-10-19T04:00:00Z"), it.toString()) }
            val repository = object : BookRepository by base {
                override suspend fun resolveSmsIncomes(fingerprints: Set<String>, import: Boolean): Int {
                    resolved = fingerprints
                    imported = import
                    base.pendingSms.value = base.pendingSms.value.filterNot { it.fingerprint in fingerprints }
                    return fingerprints.size
                }
            }
            vm = BookViewModel(repository)
            store.put("review", vm)
        }
        compose.setContent { AccountBookTheme(darkTheme = dark) { BookApp(vm) } }
    }

    private fun capture(name: String) {
        val file = File(context.getExternalFilesDir(null), "ui-verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
