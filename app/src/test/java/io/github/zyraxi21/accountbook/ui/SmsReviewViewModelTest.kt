package io.github.zyraxi21.accountbook.ui

import androidx.lifecycle.ViewModelStore
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.sms.*
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SmsReviewViewModelTest {
    private val store = ViewModelStore()
    private val first = ParsedIcbcIncome("工资", Money(10000), Instant.parse("2026-10-19T04:00:00Z"), "first")
    private val second = first.copy(title = "奖金", fingerprint = "second")
    private val base = ReadOnlyBookRepository(BookData(settings = BookSettings(smsAutoImportEnabled = true)))
    private var resolved = emptySet<String>()
    private var imported: Boolean? = null
    private var failWrite = false
    private val repository = object : BookRepository by base {
        override suspend fun resolveSmsIncomes(fingerprints: Set<String>, import: Boolean): Int {
            if (failWrite) throw BookException(BookError.STORAGE_UNAVAILABLE)
            resolved = fingerprints
            imported = import
            base.pendingSms.value = base.pendingSms.value.filterNot { it.fingerprint in fingerprints }
            return fingerprints.size
        }
    }
    private lateinit var vm: BookViewModel

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        base.pendingSms.value = listOf(first, second)
        vm = BookViewModel(repository)
        store.put("sms", vm)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun startsWithAllSelectedButCannotSubmitHiddenFinancialData() {
        assertTrue(vm.smsReview.value.visible)
        assertEquals(setOf("first", "second"), vm.smsReview.value.selected)
        vm.resolveSmsReview(import = true)
        assertTrue(resolved.isEmpty())
        assertEquals(2, vm.smsReview.value.incomes.size)
    }

    @Test fun confirmsOnlyTheSelectedIncomeAndRetainsTheOtherForLater() {
        vm.togglePrivacy()
        vm.selectSmsIncome("second", false)
        vm.resolveSmsReview(import = true)
        assertEquals(setOf("first"), resolved)
        assertEquals(true, imported)
        assertEquals(listOf(second), vm.smsReview.value.incomes)
        assertFalse(vm.smsReview.value.visible)
        vm.openSmsReview()
        assertTrue(vm.smsReview.value.visible)
    }

    @Test fun dismissingDoesNotWriteAndOrdinaryBookRefreshDoesNotReopenTheDialog() {
        vm.dismissSmsReview()
        base.data.value = base.data.value.copy(channels = listOf(Channel("bank", "银行", true, 0)))
        assertFalse(vm.smsReview.value.visible)
        assertTrue(resolved.isEmpty())
        val reopened = BookViewModel(repository)
        store.put("reopened", reopened)
        assertEquals(listOf(first, second), reopened.smsReview.value.incomes)
        assertTrue(reopened.smsReview.value.visible)
    }

    @Test fun newlyArrivedIncomeIsAddedWithoutResettingExistingSelections() {
        vm.togglePrivacy()
        vm.selectSmsIncome("first", false)
        vm.dismissSmsReview()
        base.pendingSms.value = listOf(first, second, first.copy(title = "转账", fingerprint = "third"))
        assertTrue(vm.smsReview.value.visible)
        assertEquals(setOf("second", "third"), vm.smsReview.value.selected)
    }

    @Test fun failedBatchKeepsTheReviewAndItsSelectionForRetry() {
        vm.togglePrivacy()
        val original = vm.smsReview.value
        failWrite = true
        vm.resolveSmsReview(import = true)
        assertEquals(original, vm.smsReview.value)
        assertFalse(vm.busy.value)
        assertEquals(R.string.error_storage, vm.message.value)
    }

    @Test fun scanWaitsForSettingsAndDoesNotRepeatWhenLedgerDataChanges() {
        val delayed = MutableStateFlow<BookData?>(null)
        var reads = 0
        val reader = object : SmsInboxReader {
            override fun hasPermission() = true
            override suspend fun read(since: Instant?, through: Instant): List<InboxSms> { reads++; return emptyList() }
        }
        val repo = object : BookRepository by base {
            override fun observeBook() = delayed.filterNotNull()
            override suspend fun smsScanSince(): Instant? = null
            override suspend fun stageSmsIncomes(incomes: List<ParsedIcbcIncome>, scannedThrough: Instant?) = Unit
        }
        val scanned = BookViewModel(repo, smsScanner = SmsInboxScanner(repo, reader))
        store.put("scan", scanned)
        scanned.onForeground()
        assertEquals(0, reads)
        delayed.value = base.data.value
        assertEquals(1, reads)
        delayed.value = base.data.value.copy(channels = listOf(Channel("bank", "银行", true, 0)))
        assertEquals(1, reads)
        scanned.obscureInBackground(); scanned.onForeground()
        assertEquals(2, reads)
    }

    @Test fun returningWhileAQueryIsRunningDoesNotLoseTheNextScanRequest() {
        val query = CompletableDeferred<Unit>()
        var reads = 0
        val reader = object : SmsInboxReader {
            override fun hasPermission() = true
            override suspend fun read(since: Instant?, through: Instant): List<InboxSms> {
                if (++reads == 1) query.await()
                return emptyList()
            }
        }
        val repo = object : BookRepository by base {
            override suspend fun smsScanSince(): Instant? = null
            override suspend fun stageSmsIncomes(incomes: List<ParsedIcbcIncome>, scannedThrough: Instant?) = Unit
        }
        val scanned = BookViewModel(repo, smsScanner = SmsInboxScanner(repo, reader))
        store.put("scan-in-progress", scanned)
        scanned.onForeground()
        assertEquals(1, reads)
        scanned.obscureInBackground(); scanned.onForeground()
        assertEquals(1, reads)
        query.complete(Unit)
        assertEquals(2, reads)
    }

    @Test fun startupWithOnlyManualIncomeOpensReviewForTheReportedSms() {
        val through = Instant.parse("2026-10-19T09:13:00Z")
        base.data.value = base.data.value.copy(incomes = listOf(Income("manual", "手动登记", Money(100), through)))
        base.pendingSms.value = emptyList()
        val reader = object : SmsInboxReader {
            override fun hasPermission() = true
            override suspend fun read(since: Instant?, through: Instant): List<InboxSms> {
                assertNull(since)
                return listOf(InboxSms("95588", "尾号1234卡10月19日17:12工商银行收入(入账)100元，余额660.60元。【工商银行】",
                    through.minusSeconds(30)))
            }
        }
        val repo = object : BookRepository by base {
            override suspend fun smsScanSince(): Instant? = null
            override suspend fun stageSmsIncomes(incomes: List<ParsedIcbcIncome>, scannedThrough: Instant?) {
                base.pendingSms.value = incomes
            }
        }
        val scanned = BookViewModel(repo, smsScanner = SmsInboxScanner(repo, reader, clock = Clock.fixed(through, BOOK_ZONE)))
        store.put("manual-only-startup", scanned)
        scanned.onForeground()
        assertEquals(SmsScanUiState.Complete(1, 1), scanned.smsScanState.value)
        assertTrue(scanned.smsReview.value.visible)
        assertEquals("入账", scanned.smsReview.value.incomes.single().title)
        assertEquals(Money(10000), scanned.smsReview.value.incomes.single().amount)
        assertEquals(IncomeSource.MANUAL, scanned.state.value.data.incomes.single().source)
    }

    @Test fun unavailablePermissionAndFailedQueriesHaveDistinctStatusWithoutStartupNotifications() {
        var permission = false
        val reader = object : SmsInboxReader {
            override fun hasPermission() = permission
            override suspend fun read(since: Instant?, through: Instant): List<InboxSms> = throw IOException()
        }
        val repo = object : BookRepository by base { override suspend fun smsScanSince(): Instant? = null }
        val scanned = BookViewModel(repo, smsScanner = SmsInboxScanner(repo, reader))
        store.put("scan-failure", scanned)
        scanned.onForeground()
        assertEquals(SmsScanUiState.PermissionRequired, scanned.smsScanState.value)
        assertNull(scanned.message.value)
        permission = true
        scanned.onForeground()
        assertEquals(SmsScanUiState.Failed, scanned.smsScanState.value)
        assertNull(scanned.message.value)
    }

    @Test fun recheckBypassesTheOldCheckpointAndReopensDeferredItems() {
        val queriedSince = mutableListOf<Instant?>()
        val oldCheckpoint = Instant.parse("2026-10-19T09:00:00Z")
        val reader = object : SmsInboxReader {
            override fun hasPermission() = true
            override suspend fun read(since: Instant?, through: Instant): List<InboxSms> {
                queriedSince.add(since)
                return emptyList()
            }
        }
        val repo = object : BookRepository by base {
            override suspend fun smsScanSince() = oldCheckpoint
            override suspend fun stageSmsIncomes(incomes: List<ParsedIcbcIncome>, scannedThrough: Instant?) = Unit
        }
        val scanned = BookViewModel(repo, smsScanner = SmsInboxScanner(repo, reader))
        store.put("recheck", scanned)
        scanned.onForeground()
        scanned.dismissSmsReview()
        assertFalse(scanned.smsReview.value.visible)
        scanned.recheckSmsInbox()
        assertEquals(listOf(oldCheckpoint, null), queriedSince)
        assertTrue(scanned.smsReview.value.visible)
        assertEquals(SmsScanUiState.Complete(0, 0), scanned.smsScanState.value)
    }
}
