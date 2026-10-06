package io.github.zyraxi21.accountbook.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PrivacyUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = ViewModelStore()
    private lateinit var vm: BookViewModel
    private val now = Instant.parse("2026-10-06T04:35:00Z")

    @After fun cleanup() { compose.runOnIdle { store.clear() } }

    @Test fun hiddenModeRemovesFinancialTextFromMergedAndUnmergedSemantics() {
        launchBook()
        compose.onAllNodesWithText("隐私银行", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("¥ 1,234.56", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_show)).performClick()
        compose.onNodeWithText("隐私银行").assertExists()
        // 此用例只有一个渠道，其余额与总资产相同，界面应同时显示这两个值。
        compose.onAllNodesWithText("¥ 1,234.56").assertCountEquals(2)
        compose.onNodeWithContentDescription(context.getString(R.string.privacy_hide)).performClick()
        compose.onAllNodesWithText("隐私银行", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("¥ 1,234.56", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun hidingRemovesEditorTextAndKeepsDraftForLater() {
        launchBook()
        compose.runOnIdle {
            vm.togglePrivacy(); vm.openIncome(); vm.updateIncome(title = "编辑中的隐私项目", amount = "432.10")
        }
        compose.onNode(hasSetTextAction() and hasText("编辑中的隐私项目")).assertExists()
        compose.runOnIdle { vm.hidePrivateData() }
        compose.onAllNodesWithText("编辑中的隐私项目", useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { assertTrue(vm.privacyHidden.value); vm.togglePrivacy() }
        compose.onNode(hasSetTextAction() and hasText("编辑中的隐私项目")).assertExists()
    }

    private fun launchBook() {
        compose.runOnIdle {
            vm = BookViewModel(ReadOnlyBookRepository(BookData(channels = listOf(Channel("bank", "隐私银行", true, 0)),
                snapshots = listOf(MonthlyAssetSnapshot(now, listOf(ChannelBalance("bank", "隐私银行", Money(123456))), Money(10000))),
                incomes = listOf(Income("salary", "隐私收入项目", Money(10000), now)))), clock = Clock.fixed(now, BOOK_ZONE))
            store.put("privacy", vm)
        }
        compose.setContent { AccountBookTheme { BookApp(vm) } }
        compose.waitForIdle()
    }
}
