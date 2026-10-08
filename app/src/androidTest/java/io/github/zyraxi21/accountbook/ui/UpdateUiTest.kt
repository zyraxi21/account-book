package io.github.zyraxi21.accountbook.ui

import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.data.transfer.BookTransfer
import io.github.zyraxi21.accountbook.data.update.ApkDownloader
import io.github.zyraxi21.accountbook.data.update.UpdateError
import io.github.zyraxi21.accountbook.data.update.UpdateRepository
import io.github.zyraxi21.accountbook.data.update.UpdateResult
import io.github.zyraxi21.accountbook.domain.AppVersion
import io.github.zyraxi21.accountbook.domain.BOOK_ZONE
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.BookRepository
import io.github.zyraxi21.accountbook.domain.ReleaseInfo
import io.github.zyraxi21.accountbook.domain.ReleaseSelector
import io.github.zyraxi21.accountbook.sms.IcbcSmsParser
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import io.github.zyraxi21.accountbook.ui.settings.ABOUT_SHEET_TAG
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred

/**
 * 检查更新的界面回归。
 *
 * 重点覆盖曾出现的层级问题：BottomSheet 铺满全屏并覆盖在主界面之上，
 * 提示宿主若只挂在主界面上，“当前已是最新版本”等提示将不可见。
 * 该用例断言提示文本在弹层打开时确实存在于可交互的语义树中。
 */
@RunWith(AndroidJUnit4::class)
class UpdateUiTest {
    @get:Rule val compose = createAndroidComposeRule<AppCompatActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = ViewModelStore()
    private lateinit var vm: BookViewModel
    private val now = Instant.parse("2026-10-07T04:35:00Z")
    private val current = AppVersion.parse("2026.10.07.1")!!

    @After fun cleanup() { compose.runOnIdle { store.clear() } }

    /** 手动检查仓库为空时，具体原因应在关于弹层内可见。 */
    @Test fun emptyRepositoryShowsReasonAboveBottomSheet() {
        launchBook { emptyRepository() }
        openAbout()
        compose.onNodeWithText(context.getString(R.string.update_check)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(context.getString(R.string.update_no_release))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // 断言提示真实存在于语义树，而不是仅在 ViewModel 状态里。
        compose.onNodeWithText(context.getString(R.string.update_no_release)).assertIsDisplayed()
        assertTrue("提示应位于关于弹层之内", snackbarInsideSheet())
    }

    /** 有更高版本时弹出更新说明，宿主仍需可见。 */
    @Test fun newerReleaseShowsDialogWithNotes() {
        val release = ReleaseInfo("2026.10.9.1", "2026.10.9.1", "修复若干问题",
            AppVersion.parse("2026.10.9.1")!!, null, false, false, null)
        launchBook { UpdateResult.Available(release) }
        openAbout()
        compose.onNodeWithText(context.getString(R.string.update_check)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(context.getString(R.string.update_release_notes)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.update_available_title)).assertExists()
        // 更新说明原文应完整展示。
        compose.onNodeWithText("修复若干问题").assertExists()
        // 没有 APK 资源时禁用下载按钮。
        compose.onNode(hasText(context.getString(R.string.update_download_now)) and hasAnyAncestor(isDialog()))
            .assertIsNotEnabled()
    }

    @Test fun startupNewReleaseShowsDownloadDialogWithoutOpeningAbout() {
        var checks = 0
        val release = ReleaseInfo("v2026.10.9.1", "2026.10.9.1", "改进更新体验",
            AppVersion.parse("2026.10.9.1")!!,
            "https://github.com/zyraxi21/account-book/releases/download/v2026.10.9.1/app-release.apk",
            false, false, null)
        launchBook(startup = true) { checks++; UpdateResult.Available(release) }
        compose.onNodeWithTag(ABOUT_SHEET_TAG).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.update_available_title)).assertIsDisplayed()
        compose.onNodeWithText("改进更新体验").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.update_download_now)).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.update_later)).performClick()
        compose.onNodeWithText(context.getString(R.string.update_available_title)).assertDoesNotExist()
        compose.runOnIdle { vm.checkForUpdatesOnStartup(); assertEquals(1, checks) }
        compose.onNodeWithText(context.getString(R.string.update_available_title)).assertDoesNotExist()
    }

    @Test fun startupUpToDateDoesNotShowSnackbarOrDialog() {
        launchBook(startup = true) { UpdateResult.UpToDate(ReleaseSelector.Failure.UP_TO_DATE) }
        compose.onNodeWithText(context.getString(R.string.update_up_to_date)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.update_available_title)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(UpdateUiState.Idle, vm.updateState.value) }
    }

    @Test fun startupNetworkFailureDoesNotShowSnackbarOrDialog() {
        launchBook(startup = true) { UpdateResult.Failed(UpdateError.OFFLINE) }
        compose.onNodeWithText(context.getString(R.string.update_error_offline)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.update_available_title)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(UpdateUiState.Idle, vm.updateState.value) }
    }

    /** 网络失败时按原因给出可执行文案，而不是笼统的“检查失败”。 */
    @Test fun networkFailureShowsSpecificHint() {
        launchBook { UpdateResult.Failed(UpdateError.OFFLINE) }
        openAbout()
        compose.onNodeWithText(context.getString(R.string.update_check)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(context.getString(R.string.update_error_offline))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.update_error_offline)).assertIsDisplayed()
    }

    /**
     * 检查进行中时按钮应切换为加载文案并禁用，避免重复请求触发限流。
     * 用挂起的方式让检查停在进行中，断言完成后释放。
     */
    @Test fun buttonShowsCheckingAndDisabledWhileInFlight() {
        val gate = CompletableDeferred<Unit>()
        launchBook {
            gate.await()
            UpdateResult.UpToDate(ReleaseSelector.Failure.UP_TO_DATE)
        }
        openAbout()
        compose.onNodeWithText(context.getString(R.string.update_check)).assertIsEnabled().performClick()
        // 检查尚未返回，按钮应已切换为加载文案且不可点击。
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(context.getString(R.string.update_checking)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.update_checking)).assertIsNotEnabled()
        gate.complete(Unit)
        // 检查结束后恢复为可点击的“检查更新”。
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(context.getString(R.string.update_check)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 提示必须位于关于弹层之内。
     * 用 `hasAnyAncestor` 匹配：若提示在弹层子树中，祖先里能找到弹层根节点。
     */
    private fun snackbarInsideSheet(): Boolean =
        compose.onAllNodesWithText(context.getString(R.string.update_no_release))
            .fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodes(
                hasText(context.getString(R.string.update_no_release)) and
                    hasAnyAncestor(hasTestTag(ABOUT_SHEET_TAG))
            ).fetchSemanticsNodes().isNotEmpty()

    private fun openAbout() {
        compose.onNodeWithText(context.getString(R.string.tab_settings)).performClick()
        compose.waitForIdle()
        // 关于入口在设置页底部，需要先滚动才能点击。
        val matcher = hasText(context.getString(R.string.about_title))
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        compose.onNode(matcher).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun emptyRepository(): UpdateResult = UpdateResult.UpToDate(ReleaseSelector.Failure.NO_RELEASE)

    /**
     * 启动应用并注入假的更新结果。
     * 用仓库替身返回固定结果，避免测试依赖真实网络。
     */
    private fun launchBook(startup: Boolean = false, updateResult: suspend (AppVersion) -> UpdateResult) {
        compose.runOnIdle {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val readOnly = ReadOnlyBookRepository(BookData())
            val repository: BookRepository = readOnly
            val updater = object : UpdateRepository() {
                override suspend fun check(current: AppVersion): UpdateResult = updateResult(current)
            }
            vm = BookViewModel(repository, IcbcSmsParser(), clock = Clock.fixed(now, BOOK_ZONE),
                transfer = BookTransfer(context, repository), updater = updater,
                downloader = ApkDownloader(context), currentVersion = current)
            store.put("update", vm)
            if (startup) vm.checkForUpdatesOnStartup()
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides compose.activity) {
                AccountBookTheme { BookApp(vm) }
            }
        }
        compose.waitForIdle()
    }
}
