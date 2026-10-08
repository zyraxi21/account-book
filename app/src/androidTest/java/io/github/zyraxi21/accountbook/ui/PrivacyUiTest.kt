package io.github.zyraxi21.accountbook.ui

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import com.microsoft.fluentui.tokenized.bottomsheet.BOTTOMSHEET_HANDLE_TAG
import com.microsoft.fluentui.tokenized.bottomsheet.BOTTOMSHEET_CONTENT_TAG
import com.microsoft.fluentui.calendar.CalendarView
import com.microsoft.fluentui.view.WrapContentViewPager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zyraxi21.accountbook.ui.components.CurrentMonthButton
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import io.github.zyraxi21.accountbook.data.transfer.BookTransfer
import org.hamcrest.Matcher
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.io.File

@RunWith(AndroidJUnit4::class)
class PrivacyUiTest {
    @get:Rule val compose = createAndroidComposeRule<AppCompatActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = ViewModelStore()
    private lateinit var vm: BookViewModel
    private var darkTheme by mutableStateOf(false)
    private val now = Instant.parse("2026-10-06T04:35:00Z")

    @After fun cleanup() { compose.runOnIdle { store.clear() } }

    @Test fun hiddenModeRemovesFinancialTextFromMergedAndUnmergedSemantics() {
        launchBook()
        compose.onNodeWithTag("register_assets_${vm.thisMonth}").assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onNodeWithText(context.getString(R.string.privacy_reveal_first)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(null, vm.assetDraft.value) }
        compose.onAllNodesWithText("隐私银行", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("¥ 1,234.56", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithTag("register_assets_${vm.thisMonth}").assertIsEnabled()
        compose.onNodeWithText("隐私银行").assertExists()
        // 此用例只有一个渠道，其余额与总资产相同，界面应同时显示这两个值。
        compose.onAllNodesWithText("¥ 1,234.56").assertCountEquals(2)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_hide)).performClick()
        compose.onNodeWithTag("register_assets_${vm.thisMonth}").assertIsNotEnabled()
        compose.onAllNodesWithText("隐私银行", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("¥ 1,234.56", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun hidingRemovesEditorTextAndKeepsDraftForLater() {
        launchBook()
        compose.runOnIdle {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            vm.togglePrivacy(); vm.openIncome(); vm.updateIncome(title = "编辑中的隐私项目", amount = "432.10")
        }
        compose.onNode(hasSetTextAction() and hasText("编辑中的隐私项目")).assertExists()
        compose.runOnIdle { vm.hidePrivateData() }
        compose.onAllNodesWithText("编辑中的隐私项目", useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { assertTrue(vm.privacyHidden.value); vm.togglePrivacy() }
        compose.onNode(hasSetTextAction() and hasText("编辑中的隐私项目")).assertExists()
    }

    @Test fun registeringAssetsWithCardsDoesNotCrashAndRestoresDraft() {
        launchBook(hasSnapshot = false)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithText(context.getString(R.string.register_assets)).performClick()
        compose.onNodeWithText(context.getString(R.string.save_assets)).assertExists()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        compose.runOnIdle { vm.updateBalance("bank", amount = "10.25") }
        compose.onNode(hasSetTextAction() and hasText("10.25")).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.select_date)).performClick()
        closeNativeDatePicker()
        compose.onNodeWithText(context.getString(R.string.save_assets)).assertExists()
        compose.runOnIdle { vm.hidePrivateData() }
        compose.onAllNodes(hasSetTextAction() and hasText("10.25"), useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { vm.togglePrivacy() }
        compose.onNode(hasSetTextAction() and hasText("10.25")).assertExists()
        compose.runOnIdle { darkTheme = true }
        compose.onNodeWithContentDescription(context.getString(R.string.select_date)).performClick()
        closeNativeDatePicker()
    }

    @Test fun registeringAssetBalanceKeepsTheCaretWhileTyping() {
        assertAssetBalanceCaret(editing = false)
    }

    @Test fun editingAssetBalanceKeepsTheCaretWhileTyping() {
        assertAssetBalanceCaret(editing = true)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun assertAssetBalanceCaret(editing: Boolean) {
        launchBook(hasSnapshot = editing)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithTag("register_assets_${vm.thisMonth}").performClick()
        val balance = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("channel_amount_bank")),
            useUnmergedTree = true)
        balance.performTextClearance()
        var expected = ""
        // 分次输入，检查每一位之后的光标，避免整段粘贴掩盖输入回退问题。
        for (digit in "1234.56") {
            balance.performTextInput(digit.toString())
            expected += digit
            balance.assertTextEquals(expected)
            balance.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(expected.length)))
        }
        // 用户移动光标后应继续在选定位置编辑，不能强行跳到末尾。
        balance.performTextInputSelection(TextRange(2))
        balance.performTextInput("9")
        balance.assertTextEquals("12934.56")
        balance.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(3)))
        balance.performKeyInput { pressKey(Key.Backspace) }
        balance.assertTextEquals("1234.56")
        balance.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(2)))
        compose.runOnIdle { assertEquals("1234.56", vm.assetDraft.value!!.balances.single().amount) }
    }

    @Test fun swipesNavigateMonthsAndCurrentMonthCannotAdvance() {
        launchBook()
        compose.onNodeWithContentDescription(context.getString(R.string.next_month)).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(vm.thisMonth, vm.selectedMonth.value) }
        compose.onAllNodesWithContentDescription(context.getString(R.string.current_month)).assertCountEquals(0)
        val label = context.getString(R.string.month_format, vm.thisMonth.year, vm.thisMonth.monthValue)
        val originalX = compose.onNodeWithText(label).fetchSemanticsNode().positionInRoot.x
        val statement = compose.onNodeWithTag("statement_${vm.thisMonth}")
        val originalBodyX = statement.fetchSemanticsNode().positionInRoot.x
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("month_statement_pager").performTouchInput {
                down(center)
                moveBy(Offset(width * 0.12f, 0f))
                moveBy(Offset(width * 0.18f, 0f))
            }
            compose.mainClock.advanceTimeByFrame()
            val draggedX = compose.onNodeWithText(label).fetchSemanticsNode().positionInRoot.x
            assertEquals("月份栏应始终保持原位", originalX, draggedX, 0.1f)
            assertTrue("账单应在手指未松开时跟随拖动", statement.fetchSemanticsNode().positionInRoot.x > originalBodyX + 20f)
            compose.onNodeWithContentDescription(context.getString(R.string.previous_month)).assertIsEnabled()
            compose.onNodeWithContentDescription(context.getString(R.string.next_month)).assertIsNotEnabled()
            compose.onNodeWithTag("register_assets_${vm.thisMonth}").assertIsEnabled()
            assertEquals("拖动结束后才同步停靠月份", vm.thisMonth, vm.selectedMonth.value)
            compose.onNodeWithTag("month_statement_pager").performTouchInput { advanceEventTime(300); up() }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("短距离拖动应吸附回原月份", vm.thisMonth, vm.selectedMonth.value) }
        compose.onNodeWithTag("month_statement_pager").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(vm.thisMonth, vm.selectedMonth.value) }
        compose.onNodeWithTag("month_statement_pager").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(vm.thisMonth.minusMonths(1), vm.selectedMonth.value) }
        compose.onNodeWithContentDescription(context.getString(R.string.current_month)).assertExists().performClick()
        compose.runOnIdle { assertEquals(vm.thisMonth, vm.selectedMonth.value) }
        compose.onAllNodesWithContentDescription(context.getString(R.string.current_month)).assertCountEquals(0)
        capturePreview("assets-light")
    }

    @Test fun eachVisiblePageCanOpenItsOwnEditorDuringDragging() {
        launchBook(hasSnapshot = false)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        val current = vm.thisMonth
        val previous = current.minusMonths(1)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("month_statement_pager").performTouchInput {
                down(center)
                moveBy(Offset(width * 0.15f, 0f))
                moveBy(Offset(width * 0.25f, 0f))
            }
            compose.mainClock.advanceTimeByFrame()
            for (pageMonth in listOf(previous, current)) {
                compose.onNodeWithTag("register_assets_$pageMonth").assertIsEnabled()
                    .performSemanticsAction(SemanticsActions.OnClick) { it() }
                compose.runOnIdle {
                    assertEquals(pageMonth, YearMonth.from(vm.assetDraft.value!!.registeredAt.atZone(BOOK_ZONE)))
                    vm.closeAssetDraft()
                }
            }
            compose.onNodeWithTag("month_statement_pager").performTouchInput { advanceEventTime(300); up() }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
    }

    @Test fun monthButtonsAccumulateAndCanReverseOrReturnWhileAnimating() {
        launchBook(hasSnapshot = false)
        compose.mainClock.autoAdvance = false
        try {
            val previous = compose.onNodeWithContentDescription(context.getString(R.string.previous_month))
            previous.performClick()
            compose.mainClock.advanceTimeBy(48)
            previous.assertIsEnabled().performClick()
            previous.performClick()
            compose.mainClock.advanceTimeBy(128)
            compose.onNodeWithContentDescription(context.getString(R.string.next_month)).assertIsEnabled().performClick()
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(vm.thisMonth.minusMonths(2), vm.selectedMonth.value) }
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription(context.getString(R.string.previous_month)).performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithContentDescription(context.getString(R.string.current_month)).assertIsEnabled().performClick()
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(vm.thisMonth, vm.selectedMonth.value) }
    }

    @Test fun deletionConfirmationKeepsOriginalMonthAfterNavigation() {
        val deleted = mutableListOf<YearMonth>()
        launchBook(onDeleteAsset = { deleted.add(it) })
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        val original = vm.thisMonth
        compose.onNodeWithTag("statement_$original").performScrollToNode(hasTestTag("delete_assets_$original"))
        compose.onNodeWithTag("delete_assets_$original").performClick()
        compose.runOnIdle { vm.selectMonth(original.minusMonths(1)) }
        compose.onNode(hasText(context.getString(R.string.delete)) and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(listOf(original), deleted) }
    }

    @Test fun backgroundMaskRemovesSemanticsAndRestoresEditorWhenAutomaticHideIsOff() {
        launchBook()
        compose.runOnIdle {
            vm.setHideOnStartup(false)
            vm.togglePrivacy()
            vm.openIncome()
            vm.updateIncome(title = "后台草稿项目", amount = "123.45")
        }
        compose.onNode(hasSetTextAction() and hasText("后台草稿项目")).assertExists()
        compose.runOnIdle { vm.obscureInBackground(); vm.onBackgroundStopped() }
        compose.onAllNodesWithText("后台草稿项目", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("隐私银行", useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { vm.onForeground() }
        compose.onNode(hasSetTextAction() and hasText("后台草稿项目")).assertExists()
    }

    @Test fun systemHintUsesSnackbarAndPrivacyControlHasNoVisibleTextLabel() {
        launchBook(hasSnapshot = false)
        compose.onAllNodesWithText("隐私").assertCountEquals(0)
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("book_top_bar_card"))).assertCountEquals(1)
        compose.onNodeWithText(context.getString(R.string.register_assets)).assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onNodeWithText(context.getString(R.string.privacy_reveal_first)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(null, vm.assetDraft.value); vm.notifyMessage(R.string.sms_grant_failed) }
        compose.onNodeWithText(context.getString(R.string.sms_grant_failed)).assertExists()
        compose.onNodeWithTag("book_snackbar").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithText(context.getString(R.string.register_assets)).assertIsEnabled()
        capturePreview("gradient-light", includeSystemBars = true)
        val closeAction = compose.onNodeWithTag("book_snackbar").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single { it.label == context.getString(R.string.close) }
        compose.runOnIdle { assertTrue(closeAction.action()) }
        compose.onNodeWithText(context.getString(R.string.sms_grant_failed)).assertDoesNotExist()
        compose.runOnIdle { darkTheme = true; vm.notifyMessage(R.string.sms_grant_failed) }
        // 原生提示队列在关闭与下一条之间保留短暂间隔，等待新提示完成布局。
        compose.waitUntil(timeoutMillis = 5_000) { compose.onNodeWithTag("book_snackbar").isDisplayed() }
        compose.onNodeWithTag("book_snackbar").assertIsDisplayed()
        capturePreview("gradient-dark", includeSystemBars = true)
    }

    @Test fun startupAndScreenshotSwitchesSaveWithoutSuccessMessageAndAboutContainsLocalDataInfo() {
        val openedLinks = mutableListOf<String>()
        launchBook(uriHandler = object : UriHandler {
            override fun openUri(uri: String) { openedLinks.add(uri) }
        })
        compose.onNodeWithText(context.getString(R.string.tab_settings)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.default_privacy_title)).performScrollTo().performClick()
        compose.runOnIdle { assertFalse(vm.state.value.data.settings.hideOnStartup); assertEquals(null, vm.message.value) }
        compose.onNodeWithContentDescription(context.getString(R.string.allow_screenshots_title)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(vm.state.value.data.settings.allowScreenshots); assertEquals(null, vm.message.value) }
        capturePreview("settings-light")
        compose.runOnIdle { darkTheme = true }
        compose.waitForIdle()
        capturePreview("settings-dark")
        compose.onAllNodesWithText(context.getString(R.string.encrypted_local_title)).assertCountEquals(0)
        scrollToText(R.string.about_title).performClick()
        compose.onNodeWithText(context.getString(R.string.update_check)).assertIsDisplayed()
        compose.onAllNodesWithText("版本更新").assertCountEquals(0)
        compose.runOnIdle { darkTheme = false }
        capturePreview("about-light")
        compose.runOnIdle { darkTheme = true }
        capturePreview("about-dark")
        for (text in listOf(R.string.about_author_info, R.string.about_license_info, R.string.about_source)) {
            compose.onNodeWithText(context.getString(text)).performScrollTo().performClick()
        }
        compose.runOnIdle {
            assertEquals(listOf(context.getString(R.string.about_author_url), context.getString(R.string.about_license_url),
                context.getString(R.string.about_repository_url)), openedLinks)
        }
        compose.onNodeWithTag("about_app_icon").performScrollTo()
        compose.onNodeWithText(context.getString(R.string.encrypted_local_title)).assertExists()
        compose.onNodeWithTag(BOTTOMSHEET_HANDLE_TAG).assertExists()
        // 正文拖动走嵌套滚动，与把手的关闭回调不同；两种方式都必须允许再次打开。
        compose.onNodeWithTag(BOTTOMSHEET_CONTENT_TAG).performTouchInput { swipeDown() }
        compose.onAllNodesWithText(context.getString(R.string.encrypted_local_title)).assertCountEquals(0)
        scrollToText(R.string.about_title).performClick()
        compose.onNodeWithText(context.getString(R.string.encrypted_local_title)).assertExists()
        compose.onNodeWithTag(BOTTOMSHEET_HANDLE_TAG).performTouchInput {
            swipe(start = center, end = Offset(center.x, height.toFloat() + 450f), durationMillis = 180)
        }
        compose.onAllNodesWithText(context.getString(R.string.encrypted_local_title)).assertCountEquals(0)
        scrollToText(R.string.about_title).performClick()
        compose.onNodeWithText(context.getString(R.string.encrypted_local_title)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.onAllNodesWithText(context.getString(R.string.encrypted_local_title)).assertCountEquals(0)
    }

    @Test fun alternatingExportsLaunchDocumentPickerWithMatchingMimeAndExtension() {
        val registry = CapturingRegistry()
        launchBook(registry = registry, enableTransfer = true)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithText(context.getString(R.string.tab_settings)).performClick()
        for (label in listOf(R.string.export_json, R.string.export_csv, R.string.export_json)) {
            scrollToText(label).performClick()
            compose.waitForIdle()
        }
        assertEquals(listOf("application/json", "text/csv", "application/json"), registry.intents.map { it.type })
        assertEquals(listOf("accountbook.json", "accountbook.csv", "accountbook.json"), registry.intents.map { it.getStringExtra(Intent.EXTRA_TITLE) })
    }

    @Test fun importOffersMergeAndRequiresSecondConfirmationForReplacement() {
        val registry = CapturingRegistry()
        launchBook(registry = registry, enableTransfer = true)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithText(context.getString(R.string.tab_settings)).performClick()
        scrollToText(R.string.import_button).performClick()
        compose.onNodeWithText(context.getString(R.string.import_merge_button)).performClick()
        compose.waitForIdle()
        assertEquals(1, registry.intents.size)
        scrollToText(R.string.import_button).performClick()
        compose.onNodeWithText(context.getString(R.string.import_confirm_replace)).performClick()
        compose.onNodeWithText(context.getString(R.string.import_replace_title)).assertExists()
        assertEquals(1, registry.intents.size)
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertEquals(1, registry.intents.size)
    }

    @Test fun tappingTheMonthOpensAPickerAndJumpsStraightToTheChosenMonth() {
        launchBook(hasSnapshot = false)
        val target = vm.thisMonth.minusMonths(5)
        compose.onNodeWithTag("month_picker_button").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.month_format, target.year, target.monthValue)).performClick()
        compose.runOnIdle { assertEquals(target, vm.selectedMonth.value) }
        compose.onNodeWithText(context.getString(R.string.month_format, target.year, target.monthValue)).assertExists()
    }

    @Test fun monthPickerCannotReachFutureMonths() {
        launchBook(hasSnapshot = false)
        compose.onNodeWithTag("month_picker_button").performClick()
        val future = vm.thisMonth.plusMonths(1)
        // 未来月份可能落在下一年，届时年份步进本身就该被禁用。
        if (future.year != vm.thisMonth.year) {
            compose.onNodeWithContentDescription(context.getString(R.string.next_year)).assertIsNotEnabled()
        } else {
            compose.onNodeWithContentDescription(context.getString(R.string.month_format, future.year, future.monthValue))
                .assertExists().performClick()
            compose.runOnIdle { assertEquals(vm.thisMonth, vm.selectedMonth.value) }
        }
    }

    @Test fun incomeScreenOnlyListsTheSelectedMonth() {
        val previous = YearMonth.from(now.atZone(BOOK_ZONE)).minusMonths(1)
        launchBook(incomes = listOf(
            Income("current", "本月收入项目", Money(10000), now),
            Income("previous", "上月收入项目", Money(20000), previous.atDay(15).atStartOfDay(BOOK_ZONE).toInstant()),
        ))
        compose.onNodeWithText(context.getString(R.string.tab_income)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithText("本月收入项目").assertExists()
        compose.onAllNodesWithText("上月收入项目", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription(context.getString(R.string.previous_month)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("上月收入项目").assertExists()
        compose.onAllNodesWithText("本月收入项目", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun swipingTheIncomeListMovesBetweenMonths() {
        launchBook(hasSnapshot = false)
        compose.onNodeWithText(context.getString(R.string.tab_income)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithTag("income_month_pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(vm.thisMonth.minusMonths(1), vm.selectedMonth.value) }
        // 本月不能再往后，滑回去应回到本月。
        compose.onNodeWithTag("income_month_pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(vm.thisMonth, vm.selectedMonth.value) }
    }

    @Test fun draggingAssetCardsKeepsOrderWhenPrivacyHidesAndRestoresTheDraft() {
        launchAssetCards()
        dragChannel("a")
        val savedOrder = vm.assetDraft.value!!.balances.map { it.channelId }
        compose.runOnIdle {
            assertEquals(setOf("a", "b", "c"), savedOrder.toSet())
            assertTrue(savedOrder.indexOf("a") > 0)
        }
        assertChannelOrder(savedOrder)
        compose.runOnIdle { vm.hidePrivateData() }
        compose.onAllNodesWithTag("channel_handle_a").assertCountEquals(0)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithTag("channel_handle_a").performScrollTo()
        compose.runOnIdle { assertEquals(savedOrder, vm.assetDraft.value!!.balances.map { it.channelId }) }
        assertChannelOrder(savedOrder)
    }

    @Test fun channelIconActionsRenameAndDeleteWithoutLosingTheAmountDraft() {
        launchAssetCards()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        compose.runOnIdle { vm.updateBalance("a", "10.25") }
        compose.onNodeWithTag("channel_edit_a").performClick()
        compose.onNode(hasSetTextAction() and hasText("渠道甲")).performTextReplacement("工商银行")
        compose.onNodeWithText(context.getString(R.string.save_channel)).performClick()
        compose.onNodeWithText("工商银行").assertExists()
        compose.onNode(hasSetTextAction() and hasText("10.25")).assertExists()
        capturePreview("asset-cards-light", isDialog())
        compose.runOnIdle { darkTheme = true }
        capturePreview("asset-cards-dark", isDialog())
        compose.onNodeWithTag("channel_delete_c").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.onNodeWithTag("channel_delete_c").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.delete)).performClick()
        compose.runOnIdle { assertEquals(listOf("a", "b"), vm.assetDraft.value!!.balances.map { it.channelId }) }
        compose.onNode(hasSetTextAction() and hasText("10.25")).assertExists()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.onNodeWithText(context.getString(R.string.tab_settings)).performClick()
        compose.onAllNodesWithTag("channel_handle_a").assertCountEquals(0)
    }

    @Test fun cancellingAnotherDragKeepsTheAcceptedAssetCardOrder() {
        launchAssetCards()
        dragChannel("a")
        val savedOrder = vm.assetDraft.value!!.balances.map { it.channelId }
        assertTrue(savedOrder.indexOf("a") > 0)
        dragChannel(savedOrder.first(), cancel = true)
        compose.runOnIdle { assertEquals(savedOrder, vm.assetDraft.value!!.balances.map { it.channelId }) }
        assertChannelOrder(savedOrder)
    }

    @Test fun addingAChannelUsesASeparateDialogAndRestoresTheAssetDraft() {
        launchAssetCards()
        compose.runOnIdle { vm.updateBalance("a", "10.25") }
        compose.onNodeWithTag("add_channel_button").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("招商银行")
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle {
            assertEquals(3, vm.assetDraft.value!!.balances.size)
            assertEquals("10.25", vm.assetDraft.value!!.balances.first().amount)
        }
        compose.onNodeWithTag("add_channel_button").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("招商银行")
        compose.onNodeWithText(context.getString(R.string.save_channel)).performClick()
        compose.onNodeWithText("招商银行").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(4, vm.assetDraft.value!!.balances.size)
            assertEquals("10.25", vm.assetDraft.value!!.balances.first().amount)
            assertEquals("", vm.assetDraft.value!!.balances.last().amount)
        }
    }

    @Test fun assetAndIncomeIconActionsOpenTheCorrectEditorsAndConfirmations() {
        launchBook()
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.runOnIdle { darkTheme = true }
        capturePreview("statement-dark")
        compose.onNodeWithTag("register_assets_${vm.thisMonth}").performClick()
        compose.onNode(hasSetTextAction() and hasText("1234.56")).assertExists()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.onNodeWithTag("delete_assets_${vm.thisMonth}").performClick()
        compose.onNodeWithText(context.getString(R.string.delete_asset_hint)).assertExists()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.onNodeWithText(context.getString(R.string.tab_income)).performClick()
        capturePreview("income-dark")
        compose.onNodeWithTag("edit_income_salary").performClick()
        compose.onNode(hasSetTextAction() and hasText("隐私收入项目")).assertExists()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.onNodeWithTag("delete_income_salary").performClick()
        compose.onNodeWithText(context.getString(R.string.delete_income_hint)).assertExists()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
    }

    @Test fun groupingSwitchUpdatesBothPagesWithoutASuccessMessage() {
        launchBook(incomes = listOf(Income("salary", "收入项目", Money(1234567), now)))
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithText(context.getString(R.string.tab_income)).performClick()
        val total = compose.onNodeWithTag("cumulative_income_${vm.thisMonth}")
        total.assertTextEquals(context.getString(R.string.currency_value, "12,345.67"))
        compose.onNodeWithText(context.getString(R.string.tab_settings)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.wan_grouping_title)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(vm.state.value.data.settings.useWanGrouping); assertEquals(null, vm.message.value) }
        compose.runOnIdle { darkTheme = true }
        capturePreview("grouping-settings-dark")
        compose.onNodeWithText(context.getString(R.string.tab_income)).performClick()
        total.assertTextEquals(context.getString(R.string.currency_value, "1,2345.67"))
        compose.onNodeWithText(context.getString(R.string.tab_assets)).performClick()
        compose.onAllNodesWithText(context.getString(R.string.currency_value, "1234.56")).assertCountEquals(2)
        compose.onNodeWithText(context.getString(R.string.tab_settings)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.wan_grouping_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.tab_income)).performClick()
        total.assertTextEquals(context.getString(R.string.currency_value, "12,345.67"))
    }

    @Test fun currentMonthTransitionShowsAndRemovesTheButtonWithItsShadow() {
        var visible by mutableStateOf(false)
        compose.setContent {
            AccountBookTheme(darkTheme = true, dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(LocalBookPalette.current.background).padding(24.dp)) {
                    CurrentMonthButton(visible, {}, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { visible = true }
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithTag("current_month_button").assertExists()
            capturePreview("current-month-enter-dark")
            compose.mainClock.advanceTimeBy(200)
            compose.runOnIdle { visible = false }
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("current_month_button").assertExists()
            capturePreview("current-month-exit-dark")
            compose.mainClock.advanceTimeBy(200)
            compose.onAllNodesWithTag("current_month_button").assertCountEquals(0)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun neighbouringCardsAnimateIntoTheirNewPositions() {
        launchAssetCards()
        val initialA = compose.onNodeWithTag("channel_card_a").fetchSemanticsNode().positionInRoot.y
        val initialB = compose.onNodeWithTag("channel_card_b").fetchSemanticsNode().positionInRoot.y
        val move = compose.onNodeWithTag("channel_row_a").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == context.getString(R.string.move_down) }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { assertTrue(move.action()) }
            compose.mainClock.advanceTimeBy(80)
            val intermediateB = compose.onNodeWithTag("channel_card_b").fetchSemanticsNode().positionInRoot.y
            assertTrue("相邻卡片应平滑让位，不能直接跳到终点", intermediateB > initialA + 1f && intermediateB < initialB - 1f)
            compose.mainClock.advanceTimeBy(1_500)
            val finalB = compose.onNodeWithTag("channel_card_b").fetchSemanticsNode().positionInRoot.y
            assertEquals(initialA, finalB, 1f)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun cumulativeIncomeUsesTheDisplayedMonthAndCurrentMonthCannotAdvance() {
        val month = YearMonth.from(now.atZone(BOOK_ZONE))
        launchBook(hasSnapshot = false, incomes = listOf(
            Income("previous", "历史收入", Money(10000), month.minusMonths(1).atDay(15).atStartOfDay(BOOK_ZONE).toInstant()),
            Income("current", "本月收入", Money(20000), now),
            Income("future", "未来收入", Money(40000), month.plusMonths(1).atDay(1).atStartOfDay(BOOK_ZONE).toInstant()),
        ))
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.next_month)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.tab_income)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.next_month)).assertIsNotEnabled()
        compose.onNodeWithTag("cumulative_income_$month").assertTextEquals(context.getString(R.string.currency_value, Money(30000).formatted()))
        compose.onNodeWithContentDescription(context.getString(R.string.previous_month)).performClick()
        compose.onNodeWithTag("cumulative_income_${month.minusMonths(1)}").assertTextEquals(context.getString(R.string.currency_value, Money(10000).formatted()))
        compose.onNodeWithContentDescription(context.getString(R.string.next_month)).assertIsEnabled()
        compose.onNodeWithTag("current_month_button").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.next_month)).assertIsNotEnabled()
        compose.onAllNodesWithTag("current_month_button").assertCountEquals(0)
    }

    private fun launchAssetCards() {
        launchBook(hasSnapshot = false, channels = listOf(
            Channel("a", "渠道甲", true, 0), Channel("b", "渠道乙", true, 1), Channel("c", "渠道丙", true, 2)))
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithText(context.getString(R.string.register_assets)).performClick()
    }

    private fun dragChannel(id: String, cancel: Boolean = false) {
        compose.onNodeWithTag("channel_handle_$id").performScrollTo()
        compose.onNodeWithTag("channel_handle_$id").performTouchInput {
            down(center)
            // 长按检测需要超过系统长按时长的静止时间才会开始拖动。
            advanceEventTime(1_000)
            moveBy(Offset(0f, 60f))
            moveBy(Offset(0f, 120f))
            moveBy(Offset(0f, 200f))
            if (cancel) cancel() else up()
        }
        compose.waitForIdle()
    }

    private fun assertChannelOrder(expected: List<String>) {
        val displayed = expected.sortedBy { id ->
            compose.onNodeWithTag("channel_row_$id").fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(expected, displayed)
    }

    private fun closeNativeDatePicker() {
        val description = context.getString(com.microsoft.fluentui.calendar.R.string.date_time_picker_accessibility_close_dialog_button)
        onView(withContentDescription(description)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()
            override fun getDescription() = "点击日期选择器关闭图标"
            override fun perform(uiController: UiController, view: View) {
                assertTrue((view.rootView.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                val pager = view.rootView.findViewById<WrapContentViewPager>(com.microsoft.fluentui.calendar.R.id.view_pager)
                val calendar = pager.currentObject as CalendarView
                assertEquals("日期选择器不应出现时间页", 1, pager.adapter!!.count)
                assertEquals(View.GONE, view.rootView.findViewById<View>(com.microsoft.fluentui.calendar.R.id.tab_container).visibility)
                assertTrue(calendar.height > 0)
                assertEquals("日历下方不应保留时间页的空白", calendar.height, pager.height)
                val weekHeading = calendar.getChildAt(0) as ViewGroup
                assertEquals("日历必须显示七个星期标题", 7, weekHeading.childCount)
                fun themeColor(attribute: Int): Int = TypedValue().let {
                    assertTrue(view.context.theme.resolveAttribute(attribute, it, true))
                    it.data
                }
                val foreground = if (darkTheme) Color.WHITE else Color.rgb(36, 36, 36)
                val surface = themeColor(com.microsoft.fluentui.calendar.R.attr.fluentuiDialogBackgroundColor)
                for (attribute in listOf(
                    com.microsoft.fluentui.calendar.R.attr.fluentuiDateTimePickerToolbarTitleTextColor,
                    com.microsoft.fluentui.calendar.R.attr.fluentuiDialogCloseIconColor,
                    com.microsoft.fluentui.calendar.R.attr.fluentuiCalendarDayTextDefaultColor,
                )) {
                    assertEquals(foreground, themeColor(attribute))
                    assertTrue("日期弹窗的标题、图标及日期必须清晰可读", ColorUtils.calculateContrast(themeColor(attribute), surface) >= 4.5)
                }
                // 保存独立日历的预览，内容来自测试替身，不涉及真实账务。
                val card = view.rootView.findViewById<View>(com.microsoft.fluentui.calendar.R.id.card_view_container)
                val preview = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
                card.draw(Canvas(preview))
                val file = File(context.getExternalFilesDir(null), "ui-verification/calendar-${if (darkTheme) "dark" else "light"}.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                preview.recycle()
                // 同进程调用实际关闭图标，避免真机的 INJECT_EVENTS 限制阻挡返回键注入。
                assertTrue(view.performClick())
            }
        })
        compose.waitForIdle()
    }

    private fun scrollToText(resource: Int): SemanticsNodeInteraction {
        val matcher = hasText(context.getString(resource))
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }

    private fun capturePreview(name: String, matcher: SemanticsMatcher = isRoot(), includeSystemBars: Boolean = false) {
        // 仅导出测试替身界面，截图中不包含手机上的实际账务。
        val file = File(context.getExternalFilesDir(null), "ui-verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = if (includeSystemBars) {
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        } else compose.onNode(matcher).captureToImage().asAndroidBitmap()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private inner class CapturingRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        val intents = mutableListOf<Intent>()
        override val activityResultRegistry: ActivityResultRegistry get() = this
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            intents.add(contract.createIntent(context, input))
            dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
        }
    }

    private fun launchBook(hasSnapshot: Boolean = true, registry: ActivityResultRegistryOwner? = null, enableTransfer: Boolean = false,
                           onDeleteAsset: ((YearMonth) -> Unit)? = null,
                           channels: List<Channel> = listOf(Channel("bank", "隐私银行", true, 0)),
                           incomes: List<Income> = listOf(Income("salary", "隐私收入项目", Money(10000), now)),
                           uriHandler: UriHandler? = null) {
        compose.runOnIdle {
            // 仅测试活动保持亮屏，避免厂商在回归过程中冻结测试进程。
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val readOnly = ReadOnlyBookRepository(BookData(channels = channels,
                snapshots = if (hasSnapshot) listOf(MonthlyAssetSnapshot(now, listOf(ChannelBalance("bank", "隐私银行", Money(123456))), Money(10000))) else emptyList(),
                incomes = incomes,
                settings = BookSettings(defaultChannelIds = listOf("bank"))))
            val repository = if (onDeleteAsset == null) readOnly else object : BookRepository by readOnly {
                override suspend fun deleteAsset(month: YearMonth) = onDeleteAsset(month)
            }
            vm = BookViewModel(repository, clock = Clock.fixed(now, BOOK_ZONE), transfer = if (enableTransfer) BookTransfer(context, repository) else null)
            store.put("privacy", vm)
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides (registry ?: compose.activity),
                LocalUriHandler provides (uriHandler ?: LocalUriHandler.current)) {
                AccountBookTheme(darkTheme = darkTheme) { BookApp(vm) }
            }
        }
        compose.waitForIdle()
    }
}
