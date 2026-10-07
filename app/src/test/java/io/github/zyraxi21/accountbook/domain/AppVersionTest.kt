package io.github.zyraxi21.accountbook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun parsesDottedDateVersion() {
        val version = AppVersion.parse("2026.10.07.1")!!
        assertEquals(listOf(2026, 10, 7, 1), version.segments)
        assertEquals("2026.10.7.1", version.toString())
    }

    @Test
    fun acceptsPrefixVAndShorterForms() {
        assertEquals(listOf(2026, 10, 7, 1), AppVersion.parse("v2026.10.7.1")?.segments)
        assertEquals(listOf(2026, 10, 7), AppVersion.parse("2026.10.7")?.segments)
        assertEquals(listOf(2026), AppVersion.parse("2026")?.segments)
    }

    @Test
    fun rejectsMalformedInput() {
        // 后缀、`rc` 等非纯数字段都不能进入比较，避免把预发布当正式版。
        assertNull(AppVersion.parse("2026.10.7.1-rc1"))
        assertNull(AppVersion.parse("2026.10.7.beta"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("   "))
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("2026..7"))
        // 段数超过上限，视为异常标签。
        assertNull(AppVersion.parse("1.2.3.4.5"))
        assertNull(AppVersion.parse("2026.10.7.1.2.3"))
    }

    @Test
    fun comparesNumericallyNotLexicographically() {
        // 纯字符串比较会把 "9" 判成大于 "10"，这里必须按数值。
        assertTrue(AppVersion.parse("2026.10.9.1")!! < AppVersion.parse("2026.10.10.1")!!)
        assertTrue(AppVersion.parse("2026.9.30.1")!! < AppVersion.parse("2026.10.1.1")!!)
        assertTrue(AppVersion.parse("2026.10.7.2")!! > AppVersion.parse("2026.10.7.1")!!)
    }

    @Test
    fun missingTrailingSegmentsAreZero() {
        assertEquals(0, AppVersion.parse("2026.10.7")!!.compareTo(AppVersion.parse("2026.10.7.0")!!))
        assertTrue(AppVersion.parse("2026.10.7.1")!! > AppVersion.parse("2026.10.7")!!)
    }
}

class ReleaseSelectorTest {
    /** `2026.11.1.1-rc1` 这类标签解析不出纯数字版本，用占位版本构造条目；
     *  真实解析路径会在 `parseReleases`阶段就跳过它，选择逻辑只依赖标签判定。 */
    private val FALLBACK_VERSION = AppVersion.parse("0.0.0.1")!!

    private fun release(tag: String, pre: Boolean = false, draft: Boolean = false, apk: String? = "https://example/app.apk") =
        ReleaseInfo(tag, tag, "说明", AppVersion.parse(tag.removePrefix("v")) ?: FALLBACK_VERSION, apk, pre, draft, null)

    @Test
    fun picksNewerFormalRelease() {
        val outcome = ReleaseSelector.select(
            listOf(release("2026.10.7.1"), release("2026.10.9.1")), AppVersion.parse("2026.10.7.1")!!)
        assertTrue(outcome is ReleaseSelector.Outcome.NewVersion)
        assertEquals("2026.10.9.1", (outcome as ReleaseSelector.Outcome.NewVersion).release.tag)
    }

    @Test
    fun treatsSameVersionAsUpToDate() {
        val outcome = ReleaseSelector.select(listOf(release("2026.10.7.1")), AppVersion.parse("2026.10.7.1")!!)
        assertTrue(outcome is ReleaseSelector.Outcome.UpToDate)
        // 仓库有正式版本且不低于当前版本，文案应为“当前已是最新版本”。
        assertEquals(ReleaseSelector.Failure.UP_TO_DATE, (outcome as ReleaseSelector.Outcome.UpToDate).reason)
    }

    @Test
    fun treatsOlderRemoteAsUpToDate() {
        // 本地版本高于仓库时不提示降级。
        val outcome = ReleaseSelector.select(listOf(release("2026.10.1.1")), AppVersion.parse("2026.10.7.1")!!)
        assertTrue(outcome is ReleaseSelector.Outcome.UpToDate)
        assertEquals(ReleaseSelector.Failure.UP_TO_DATE, (outcome as ReleaseSelector.Outcome.UpToDate).reason)
    }

    @Test
    fun emptyRepositoryIsUpToDateNotFailure() {
        // 当前 releases 为空：属于“无可比较版本”，不应报网络错误。
        val outcome = ReleaseSelector.select(emptyList(), AppVersion.parse("2026.10.7.1")!!)
        assertTrue(outcome is ReleaseSelector.Outcome.UpToDate)
        assertEquals(ReleaseSelector.Failure.NO_RELEASE, (outcome as ReleaseSelector.Outcome.UpToDate).reason)
    }

    @Test
    fun excludesDraftAndPrerelease() {
        val outcome = ReleaseSelector.select(
            listOf(release("2026.11.1.1", pre = true), release("2026.12.1.1", draft = true)),
            AppVersion.parse("2026.10.7.1")!!)
        assertTrue(outcome is ReleaseSelector.Outcome.UpToDate)
        assertEquals(ReleaseSelector.Failure.ALL_PRERELEASE, (outcome as ReleaseSelector.Outcome.UpToDate).reason)
    }

    @Test
    fun excludesPrereleaseByTagSuffix() {
        // 标签带 -rc 但 GitHub 的 prerelease 标记可能为 false，仍须排除。
        val outcome = ReleaseSelector.select(listOf(release("2026.11.1.1-rc1")), AppVersion.parse("2026.10.7.1")!!)
        assertTrue(outcome is ReleaseSelector.Outcome.UpToDate)
        assertEquals(ReleaseSelector.Failure.ALL_PRERELEASE, (outcome as ReleaseSelector.Outcome.UpToDate).reason)
    }

    @Test
    fun detectsPrereleaseTags() {
        assertTrue(ReleaseSelector.isPrereleaseTag("2026.10.7.1-rc1"))
        assertTrue(ReleaseSelector.isPrereleaseTag("v2026.10.7.1-beta"))
        assertTrue(ReleaseSelector.isPrereleaseTag("2026.10.7.1_build2"))
        assertFalse(ReleaseSelector.isPrereleaseTag("2026.10.7.1"))
        assertFalse(ReleaseSelector.isPrereleaseTag("v2026.10.7.1"))
    }

    @Test
    fun newVersionWithoutApkStillReported() {
        // 没有 APK 资源时仍告知有新版本，由界面决定是否禁用下载。
        val outcome = ReleaseSelector.select(listOf(release("2026.10.9.1", apk = null)), AppVersion.parse("2026.10.7.1")!!)
        assertTrue(outcome is ReleaseSelector.Outcome.NewVersion)
        assertNull((outcome as ReleaseSelector.Outcome.NewVersion).release.apkUrl)
    }
}