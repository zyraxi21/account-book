package io.github.zyraxi21.accountbook.ui

import androidx.lifecycle.ViewModelStore
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.data.update.UpdateError
import io.github.zyraxi21.accountbook.data.update.UpdateRepository
import io.github.zyraxi21.accountbook.data.update.UpdateResult
import io.github.zyraxi21.accountbook.domain.AppVersion
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.ReleaseInfo
import io.github.zyraxi21.accountbook.domain.ReleaseSelector
import io.github.zyraxi21.accountbook.testing.ReadOnlyBookRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 更新仓库使用替身，验证启动静默检查与手动检查的反馈区别。 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookUpdateTest {
    private val store = ViewModelStore()
    private val current = AppVersion.parse("2026.10.07.2")!!
    private val release = ReleaseInfo("v2026.10.08.1", "2026.10.08.1", "改进更新体验",
        AppVersion.parse("2026.10.08.1")!!,
        "https://github.com/zyraxi21/account-book/releases/download/v2026.10.08.1/app-release.apk",
        prerelease = false, draft = false, publishedAt = null)

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun startupCheckRunsOnceAcrossRecreationAndForegroundReturn() {
        var calls = 0
        val result = CompletableDeferred<UpdateResult>()
        val vm = createViewModel {
            calls++
            assertEquals(current, it)
            result.await()
        }
        vm.checkForUpdatesOnStartup()
        vm.checkForUpdatesOnStartup()
        vm.checkForUpdates()
        vm.obscureInBackground(); vm.onBackgroundStopped(); vm.onForeground()
        vm.checkForUpdatesOnStartup()
        assertEquals(1, calls)
        assertEquals(UpdateUiState.Checking, vm.updateState.value)
        assertNull(vm.message.value)
        result.complete(UpdateResult.Available(release))
        vm.dismissUpdate()
        vm.checkForUpdatesOnStartup()
        assertEquals(1, calls)
        assertEquals(UpdateUiState.Idle, vm.updateState.value)
    }

    @Test fun startupWithoutNewVersionOrWithNetworkFailureStaysSilent() {
        val results = listOf(
            UpdateResult.UpToDate(ReleaseSelector.Failure.UP_TO_DATE),
            UpdateResult.UpToDate(ReleaseSelector.Failure.NO_RELEASE),
            UpdateResult.UpToDate(ReleaseSelector.Failure.ALL_PRERELEASE),
            UpdateResult.Failed(UpdateError.OFFLINE),
            UpdateResult.Failed(UpdateError.TIMEOUT),
        )
        for (result in results) {
            val vm = createViewModel { result }
            vm.checkForUpdatesOnStartup()
            assertEquals(UpdateUiState.Idle, vm.updateState.value)
            assertNull(vm.message.value)
        }
    }

    @Test fun startupPublishesNewReleaseWithoutAutomaticallyDownloading() {
        val vm = createViewModel { UpdateResult.Available(release) }
        vm.checkForUpdatesOnStartup()
        assertEquals(UpdateUiState.Available(release), vm.updateState.value)
        assertNull(vm.message.value)
        vm.dismissUpdate()
        assertEquals(UpdateUiState.Idle, vm.updateState.value)
    }

    @Test fun manualCheckStillReportsResultsAfterSilentStartupFailure() {
        val results = ArrayDeque(listOf(
            UpdateResult.Failed(UpdateError.OFFLINE),
            UpdateResult.UpToDate(ReleaseSelector.Failure.UP_TO_DATE),
            UpdateResult.Failed(UpdateError.TIMEOUT),
        ))
        val vm = createViewModel { results.removeFirst() }
        vm.checkForUpdatesOnStartup()
        assertNull(vm.message.value)
        vm.checkForUpdates()
        assertEquals(R.string.update_up_to_date, vm.message.value)
        vm.dismissMessage()
        vm.checkForUpdates()
        assertEquals(R.string.update_error_timeout, vm.message.value)
        assertTrue(results.isEmpty())
    }

    private fun createViewModel(result: suspend (AppVersion) -> UpdateResult): BookViewModel {
        val updater = object : UpdateRepository() {
            override suspend fun check(current: AppVersion): UpdateResult = result(current)
        }
        return BookViewModel(ReadOnlyBookRepository(BookData()), updater = updater, currentVersion = current).also {
            store.put("update-${store.keys().size}", it)
        }
    }
}
