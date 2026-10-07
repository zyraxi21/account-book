package io.github.zyraxi21.accountbook.ui

import androidx.lifecycle.ViewModelStore
import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModelTest {
    private val store = ViewModelStore()
    private lateinit var vm: BookViewModel
    private val clock = Clock.fixed(Instant.parse("2026-10-06T04:35:00Z"), BOOK_ZONE)

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = BookViewModel(ReadOnlyBookRepository(BookData(
            channels = listOf(Channel("bank", "银行", true, 0), Channel("alipay", "支付宝", true, 1)),
            settings = BookSettings(rememberedChannelIds = listOf("alipay", "bank")),
        )), clock = clock)
        store.put("ledger", vm)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun startsHiddenAndBackgroundHideIsIdempotent() {
        assertTrue(vm.privacyHidden.value)
        vm.togglePrivacy()
        assertFalse(vm.privacyHidden.value)
        vm.hidePrivateData()
        vm.hidePrivateData()
        assertTrue(vm.privacyHidden.value)
    }

    @Test fun hiddenModeCannotOpenOrSaveFinancialEditors() {
        vm.openAssets(); vm.openIncome(); vm.openChannel(); vm.openSmsInput()
        vm.saveAsset(); vm.saveIncome()
        assertNull(vm.assetDraft.value)
        assertNull(vm.incomeDraft.value)
        assertNull(vm.channelDraft.value)
        assertNull(vm.smsText.value)
    }

    @Test fun restoresChannelOrderWithoutCopyingOldAmounts() {
        vm.togglePrivacy(); vm.openAssets()
        assertEquals(listOf("alipay", "bank"), vm.assetDraft.value!!.balances.map { it.channelId })
        assertTrue(vm.assetDraft.value!!.balances.all { it.selected && it.amount.isEmpty() })
    }

    @Test fun hidingRetainsDraftInMemoryAndPreventsSubmission() {
        vm.togglePrivacy(); vm.openIncome()
        vm.updateIncome(title = "隐私项目", amount = "123.45")
        val draft = vm.incomeDraft.value
        vm.hidePrivateData(); vm.saveIncome()
        assertEquals(draft, vm.incomeDraft.value)
        vm.togglePrivacy()
        assertEquals("隐私项目", vm.incomeDraft.value!!.title)
    }

    @Test fun pastedSmsCreatesEditableDraftWithoutWritingIt() {
        vm.togglePrivacy(); vm.openSmsInput()
        vm.updateSmsInput("尾号1234卡10月6日12:34工商银行收入(工资)1000.00元，余额5000.00元。【工商银行】")
        vm.parseSms()
        assertEquals("工资", vm.incomeDraft.value!!.title)
        assertEquals("1000.00", vm.incomeDraft.value!!.amount)
        assertEquals(IncomeSource.SMS, vm.incomeDraft.value!!.source)
        assertNotNull(vm.incomeDraft.value!!.fingerprint)
        assertNull(vm.smsText.value)
    }

    @Test fun monthNavigationStopsAtCurrentMonthAndWorksAcrossYears() {
        vm.moveMonth(1)
        assertEquals(YearMonth.of(2026, 10), vm.selectedMonth.value)
        vm.moveMonth(-10)
        assertEquals(YearMonth.of(2025, 12), vm.selectedMonth.value)
        vm.moveMonth(1)
        assertEquals(YearMonth.of(2026, 1), vm.selectedMonth.value)
        vm.moveMonth(100)
        assertEquals(vm.thisMonth, vm.selectedMonth.value)
        vm.selectMonth(YearMonth.of(2027, 1))
        assertEquals(vm.thisMonth, vm.selectedMonth.value)
        vm.selectMonth(YearMonth.of(2025, 12))
        assertEquals(YearMonth.of(2025, 12), vm.selectedMonth.value)
    }

    @Test fun privacySettingsSaveImmediatelyWithoutSavedMessage() {
        vm.setHideOnStartup(false)
        assertFalse(vm.state.value.data.settings.hideOnStartup)
        assertNull(vm.message.value)
        vm.setAllowScreenshots(true)
        assertTrue(vm.state.value.data.settings.allowScreenshots)
        assertNull(vm.message.value)
        // 更改下次启动偏好不会立即展示已隐藏的账务。
        assertTrue(vm.privacyHidden.value)
    }

    @Test fun disablingStartupHideOnlyRevealsOnFirstLoadAndStillHidesInBackground() {
        val other = BookViewModel(ReadOnlyBookRepository(BookData(settings = BookSettings(hideOnStartup = false))), clock = clock)
        store.put("startup", other)
        assertFalse(other.privacyHidden.value)
        other.hidePrivateData()
        assertTrue(other.privacyHidden.value)
        other.reload()
        assertTrue(other.privacyHidden.value)
    }
}
