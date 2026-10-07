package io.github.zyraxi21.accountbook.ui

import androidx.lifecycle.ViewModelStore
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
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
            settings = BookSettings(defaultChannelIds = listOf("alipay", "bank")),
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
        vm.openAssets(vm.thisMonth); vm.openIncome(); vm.openChannel(); vm.openSmsInput()
        vm.saveAsset(); vm.saveIncome()
        assertNull(vm.assetDraft.value)
        assertNull(vm.incomeDraft.value)
        assertNull(vm.channelDraft.value)
        assertNull(vm.smsText.value)
    }

    @Test fun restoresChannelOrderWithoutCopyingOldAmounts() {
        vm.togglePrivacy(); vm.openAssets(vm.thisMonth)
        assertEquals(listOf("bank", "alipay"), vm.assetDraft.value!!.balances.map { it.channelId })
        assertTrue(vm.assetDraft.value!!.balances.all { it.amount.isEmpty() })
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

    @Test fun assetSaveKeepsSelectedDateAndUsesSaveTime() {
        var saved: MonthlyAssetSnapshot? = null
        val repository = object : BookRepository by ReadOnlyBookRepository(BookData(
            channels = listOf(Channel("bank", "银行", true, 0)), settings = BookSettings(hideOnStartup = false))) {
            override suspend fun saveAsset(snapshot: MonthlyAssetSnapshot, originalMonth: YearMonth?) { saved = snapshot }
        }
        val other = BookViewModel(repository, clock = clock)
        store.put("save-date", other)
        other.openAssets(YearMonth.of(2025, 12))
        other.updateAssetDate(Instant.parse("2025-12-31T15:59:00Z"))
        other.updateBalance("bank", amount = "10.25")
        other.saveAsset()
        assertEquals(Instant.parse("2025-12-31T04:35:00Z"), saved!!.registeredAt)
        assertEquals(YearMonth.of(2025, 12), saved!!.month)
    }

    @Test fun manualIncomeSaveUsesSelectedShanghaiDateAndSaveTime() {
        var saved: Income? = null
        val repository = object : BookRepository by ReadOnlyBookRepository(BookData(settings = BookSettings(hideOnStartup = false))) {
            override suspend fun saveIncome(income: Income, importFingerprint: String?) { saved = income }
        }
        val other = BookViewModel(repository, clock = clock)
        store.put("income-date", other)
        other.openIncome()
        other.updateIncome(title = "补录收入", amount = "12.30", time = Instant.parse("2026-01-01T16:01:00Z"))
        other.saveIncome()
        assertEquals(Instant.parse("2026-01-02T04:35:00Z"), saved!!.receivedAt)
    }

    @Test fun smsIncomeSavePreservesBankTimeAndFingerprint() {
        var saved: Income? = null
        var fingerprint: String? = null
        val repository = object : BookRepository by ReadOnlyBookRepository(BookData(settings = BookSettings(hideOnStartup = false))) {
            override suspend fun saveIncome(income: Income, importFingerprint: String?) { saved = income; fingerprint = importFingerprint }
        }
        val other = BookViewModel(repository, clock = clock)
        store.put("sms-time", other)
        other.openSmsInput()
        other.updateSmsInput("尾号1234卡10月6日12:34工商银行收入(工资)1000.00元，余额5000.00元。【工商银行】")
        other.parseSms()
        val draft = other.incomeDraft.value!!
        other.saveIncome()
        assertEquals(Instant.parse("2026-10-06T04:34:00Z"), saved!!.receivedAt)
        assertEquals(IncomeSource.SMS, saved!!.source)
        assertEquals(draft.fingerprint, fingerprint)
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

    @Test fun disablingStartupHideRestoresUserChoiceAfterBackground() {
        val other = BookViewModel(ReadOnlyBookRepository(BookData(settings = BookSettings(hideOnStartup = false))), clock = clock)
        store.put("startup", other)
        assertFalse(other.privacyHidden.value)
        other.obscureInBackground()
        other.onBackgroundStopped()
        assertTrue(other.privacyHidden.value)
        other.onForeground()
        assertFalse(other.privacyHidden.value)
        other.hidePrivateData()
        other.obscureInBackground()
        other.onBackgroundStopped()
        other.onForeground()
        assertTrue(other.privacyHidden.value)
        other.reload()
        assertTrue(other.privacyHidden.value)
    }

    @Test fun autoHideAppliesToBackgroundReturnButNotConfigurationChanges() {
        vm.togglePrivacy()
        vm.obscureInBackground()
        assertTrue(vm.privacyHidden.value)
        // 配置变化只临时遮挡，不调用真正进入后台的通知。
        vm.onForeground()
        assertFalse(vm.privacyHidden.value)
        vm.obscureInBackground()
        vm.onBackgroundStopped()
        vm.onForeground()
        assertTrue(vm.privacyHidden.value)
    }

    @Test fun disablingAutoHideRestoresEditorAndDraftAfterBackground() {
        vm.setHideOnStartup(false)
        vm.togglePrivacy()
        vm.openIncome()
        vm.updateIncome(title = "未保存的收入", amount = "12.30")
        val draft = vm.incomeDraft.value
        vm.obscureInBackground()
        vm.onBackgroundStopped()
        vm.saveIncome()
        assertTrue(vm.privacyHidden.value)
        assertEquals(draft, vm.incomeDraft.value)
        vm.onForeground()
        assertFalse(vm.privacyHidden.value)
        assertEquals(draft, vm.incomeDraft.value)
    }

    @Test fun delayedPreferencesNeverRevealDataWhileBackgrounded() {
        val records = MutableStateFlow<BookData?>(null)
        val repository = object : BookRepository by ReadOnlyBookRepository(BookData()) {
            override fun observeBook() = records.filterNotNull()
        }
        val other = BookViewModel(repository, clock = clock)
        store.put("delayed", other)
        assertTrue(other.state.value.loading)
        assertTrue(other.privacyHidden.value)
        other.obscureInBackground()
        other.onBackgroundStopped()
        records.value = BookData(settings = BookSettings(hideOnStartup = false))
        assertFalse(other.state.value.loading)
        assertTrue(other.privacyHidden.value)
        other.onForeground()
        assertFalse(other.privacyHidden.value)
    }

    @Test fun coldStartUsesSavedPreferenceAfterPreviouslyHidingManually() {
        val repository = ReadOnlyBookRepository(BookData(settings = BookSettings(hideOnStartup = false)))
        val first = BookViewModel(repository, clock = clock)
        store.put("first", first)
        first.hidePrivateData()
        val restarted = BookViewModel(repository, clock = clock)
        store.put("restarted", restarted)
        assertFalse(restarted.privacyHidden.value)
    }

    @Test fun newChannelFromAssetEditorAddsAnEmptyCardAndClearsInput() {
        vm.togglePrivacy(); vm.openAssets(vm.thisMonth)
        vm.updateNewChannelName("  招商银行  ")
        vm.addChannelToDraft()
        val draft = vm.assetDraft.value!!
        val added = draft.balances.last()
        assertEquals("招商银行", added.name)
        assertTrue(added.active)
        assertEquals("", draft.newChannelName)
        assertEquals("招商银行", vm.state.value.data.activeChannels.last().name)
    }

    @Test fun blankNewChannelNameIsRejectedWithoutTouchingTheDraft() {
        vm.togglePrivacy(); vm.openAssets(vm.thisMonth)
        vm.updateNewChannelName("   ")
        vm.addChannelToDraft()
        assertEquals("   ", vm.assetDraft.value!!.newChannelName)
        assertEquals(2, vm.assetDraft.value!!.balances.size)
        assertEquals(2, vm.state.value.data.activeChannels.size)
        assertEquals(R.string.error_channel_name, vm.message.value)
    }

    @Test fun channelOrderIsWrittenThroughToTheRepository() {
        vm.togglePrivacy()
        vm.commitChannelOrder(listOf("alipay", "bank"))
        assertEquals(listOf("alipay", "bank"), vm.state.value.data.activeChannels.map { it.id })
        // 登记卡片按当前渠道顺序排列。
        vm.openAssets(vm.thisMonth)
        assertEquals(listOf("alipay", "bank"), vm.assetDraft.value!!.balances.map { it.channelId })
        assertEquals(2, vm.assetDraft.value!!.balances.size)
    }

    @Test fun assetCardOrderIsADraftUntilSaveAndEmptyAmountsBecomeZero() {
        var saved: MonthlyAssetSnapshot? = null
        val repository = object : BookRepository by ReadOnlyBookRepository(BookData(
            channels = listOf(Channel("bank", "银行", true, 0), Channel("alipay", "支付宝", true, 1)),
            settings = BookSettings(hideOnStartup = false))) {
            override suspend fun saveAsset(snapshot: MonthlyAssetSnapshot, originalMonth: YearMonth?) { saved = snapshot }
        }
        val other = BookViewModel(repository, clock = clock)
        store.put("card-order", other)
        other.openAssets(other.thisMonth)
        other.updateBalance("bank", "10.25")
        other.reorderAssetChannels(listOf("alipay", "bank"))
        assertEquals(listOf("bank", "alipay"), other.state.value.data.activeChannels.map { it.id })
        assertEquals("10.25", other.assetDraft.value!!.balances.last().amount)
        other.saveAsset()
        assertEquals(listOf("alipay", "bank"), saved!!.balances.map { it.channelId })
        assertEquals(Money.ZERO, saved!!.balances.first().amount)
        assertEquals(Money.parse("10.25"), saved!!.total)
    }

    @Test fun renamingAndDeletingChannelsRefreshesTheAssetCardsWithoutLosingAmounts() {
        vm.togglePrivacy(); vm.openAssets(vm.thisMonth)
        vm.updateBalance("bank", "10.25")
        vm.openChannel(vm.state.value.data.channels.first { it.id == "bank" })
        vm.updateChannelName("工商银行"); vm.saveChannel()
        assertEquals("工商银行", vm.assetDraft.value!!.balances.first().name)
        assertEquals("10.25", vm.assetDraft.value!!.balances.first().amount)
        vm.deleteChannel("alipay")
        assertEquals(listOf("bank"), vm.assetDraft.value!!.balances.map { it.channelId })
    }

    @Test fun historicalEditorUsesCurrentOrderAndRetainsDeletedBalancesAndHistoricalNames() {
        val month = YearMonth.of(2026, 9)
        val original = MonthlyAssetSnapshot(Instant.parse("2026-09-30T04:35:00Z"), listOf(
            ChannelBalance("bank", "旧银行名称", Money(1250)), ChannelBalance("alipay", "支付宝", Money(2500))), Money.ZERO)
        val repository = ReadOnlyBookRepository(BookData(channels = listOf(
            Channel("alipay", "支付宝", true, 0), Channel("bank", "新银行名称", true, 1)), snapshots = listOf(original)))
        val other = BookViewModel(repository, clock = clock)
        store.put("historical-cards", other)
        other.togglePrivacy(); other.openAssets(month)
        assertEquals(listOf("alipay", "bank"), other.assetDraft.value!!.balances.map { it.channelId })
        assertEquals("旧银行名称", other.assetDraft.value!!.balances.last().name)
        other.deleteChannel("bank")
        val retained = other.assetDraft.value!!.balances.last()
        assertFalse(retained.active)
        assertEquals("12.50", retained.amount)
        assertEquals(original, repository.data.value.snapshots.single())
    }

    @Test fun incomeIsSlicedByTheSelectedMonth() {
        val repository = ReadOnlyBookRepository(BookData(incomes = listOf(
            Income("february", "二月工资", Money.parse("100"), Instant.parse("2026-02-10T04:35:00Z")),
            Income("october", "十月工资", Money.parse("200"), Instant.parse("2026-10-01T04:35:00Z")),
        )))
        val other = BookViewModel(repository, clock = clock)
        store.put("months", other)
        assertEquals(listOf("october"), other.state.value.data.incomesIn(other.thisMonth).map { it.id })
        other.selectMonth(YearMonth.of(2026, 2))
        assertEquals(listOf("february"), other.state.value.data.incomesIn(other.selectedMonth.value).map { it.id })
    }

    @Test fun assetEditorsAndDelayedDeletionKeepTheirExplicitPageMonth() {
        val previous = YearMonth.of(2026, 9)
        val snapshot = MonthlyAssetSnapshot(Instant.parse("2026-09-30T12:00:00Z"),
            listOf(ChannelBalance("bank", "历史银行", Money(1250))), Money.ZERO)
        val deleted = mutableListOf<YearMonth>()
        val releaseDeletion = CompletableDeferred<Unit>()
        val repository = object : BookRepository by ReadOnlyBookRepository(BookData(
            channels = listOf(Channel("bank", "银行", true, 0)), snapshots = listOf(snapshot))) {
            override suspend fun deleteAsset(month: YearMonth) { releaseDeletion.await(); deleted.add(month) }
        }
        val other = BookViewModel(repository, clock = clock)
        store.put("page-actions", other)
        other.togglePrivacy()
        assertEquals(other.thisMonth, other.selectedMonth.value)
        other.openAssets(previous)
        assertEquals(previous, other.assetDraft.value!!.originalMonth)
        assertEquals("12.50", other.assetDraft.value!!.balances.single().amount)
        assertEquals("历史银行", other.assetDraft.value!!.balances.single().name)
        other.selectMonth(previous.minusMonths(1))
        other.deleteAsset(previous)
        other.currentMonth()
        releaseDeletion.complete(Unit)
        assertEquals(listOf(previous), deleted)
        other.closeAssetDraft()
        other.openAssets(previous.minusMonths(1))
        assertEquals(previous.minusMonths(1), YearMonth.from(other.assetDraft.value!!.registeredAt.atZone(BOOK_ZONE)))
    }
}
