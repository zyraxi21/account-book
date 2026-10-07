package io.github.zyraxi21.accountbook.domain

/**
 * 应用版本号。项目采用 `YYYY.M.D.N` 日期版本号，比较时按各段数值逐段比较，
 * 因此 `2026.10.7.2` 高于 `2026.10.7.1`，`2026.9.12.1` 低于 `2026.10.1.1`。
 * 末尾段可省略，省略时视为 0，以兼容只写到日的标签。
 */
data class AppVersion(val segments: List<Int>) : Comparable<AppVersion> {
    init {
        require(segments.isNotEmpty() && segments.size <= MAX_SEGMENTS) { "版本号段数需在 1..$MAX_SEGMENTS 之间" }
        require(segments.all { it >= 0 }) { "版本号各段不可为负" }
    }

    override fun compareTo(other: AppVersion): Int {
        // 缺失的高位段补 0，保证 `2026.10.7` 与 `2026.10.7.0` 视为同一版本。
        for (index in 0 until maxOf(segments.size, other.segments.size)) {
            val mine = segments.getOrElse(index) { 0 }
            val theirs = other.segments.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return 0
    }

    override fun toString(): String = segments.joinToString(".")

    companion object {
        private const val MAX_SEGMENTS = 4

        /** 解析形如 `2026.10.07.1` 的版本号；无法解析或段数越界时返回 null。 */
        fun parse(raw: String?): AppVersion? {
            val text = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
            if (text.isEmpty() || text.length > 32) return null
            val segments = text.split('.')
            if (segments.isEmpty() || segments.size > MAX_SEGMENTS) return null
            val numbers = segments.map { part ->
                // 只接受纯数字段，避免 `2026.10.07-beta` 之类混入比较。
                if (part.isEmpty() || part.length > 9 || !part.all(Char::isDigit)) return null
                part.toIntOrNull() ?: return null
            }
            return AppVersion(numbers)
        }
    }
}

/**
 * GitHub Releases 中的一条发布记录。
 * [apkUrl] 为该发布下第一个 `.apk` 资源地址，缺失时表示没有可安装包。
 * [notes] 为更新说明原文，界面直接展示，不做富文本解析。
 */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val version: AppVersion,
    val apkUrl: String?,
    val prerelease: Boolean,
    val draft: Boolean,
    val publishedAt: String?,
)

/** 从 GitHub Releases 的 JSON 响应中挑选目标版本，并附带无法解析的原因分类。 */
object ReleaseSelector {
    /** 结果分类，便于界面映射到不同文案，而不是把所有失败混成一句“检查失败”。 */
    enum class Failure {
        /** 已有正式版本且不高于当前版本，即“当前已是最新版本”。 */
        UP_TO_DATE,
        /** 仓库中没有任何发布记录。 */
        NO_RELEASE,
        /** 有发布记录，但全部是预发布或草稿。 */
        ALL_PRERELEASE,
        UNPARSEABLE, NETWORK, UNKNOWN
    }

    sealed interface Outcome {
        /** 已是最新，或没有可比较的正式版本。 */
        data class UpToDate(val reason: Failure) : Outcome

        /** 存在更高版本。 */
        data class NewVersion(val release: ReleaseInfo) : Outcome

        data class Failed(val reason: Failure) : Outcome
    }

    /**
     * 选取 [current] 之后的最新正式版本。
     * 预发布与草稿一律排除；即使标签带 `-rc` 这类后缀也按预发布处理。
     */
    fun select(releases: List<ReleaseInfo>, current: AppVersion): Outcome {
        val formal = releases.filter { !it.draft && !it.prerelease && !isPrereleaseTag(it.tag) }
        if (formal.isEmpty()) {
            // 区分“仓库确实没有发布”与“有发布但都是预发布”，提示文案不同。
            val anyDraftOrPre = releases.any { it.draft || it.prerelease || isPrereleaseTag(it.tag) }
            return Outcome.UpToDate(if (anyDraftOrPre) Failure.ALL_PRERELEASE else Failure.NO_RELEASE)
        }
        val newest = formal.maxWithOrNull(compareBy({ it.version }, { it.tag }))
            ?: return Outcome.UpToDate(Failure.NO_RELEASE)
        // 有正式版本但不低于当前版本，才算“已是最新”。
        return if (newest.version > current) Outcome.NewVersion(newest) else Outcome.UpToDate(Failure.UP_TO_DATE)
    }

    /** 标签含预发布后缀时按预发布处理，如 `2026.10.7.1-rc1`、`2026.10.7.1-beta`。 */
    fun isPrereleaseTag(tag: String): Boolean {
        val text = tag.trim().removePrefix("v").removePrefix("V")
        return text.contains('-') || text.contains('_')
    }
}