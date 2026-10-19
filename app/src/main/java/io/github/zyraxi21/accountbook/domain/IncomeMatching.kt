package io.github.zyraxi21.accountbook.domain

/**
 * 比对收入是否重复时使用的项目名写法：全角半角标点、空白和大小写差异都不算不同项目。
 *
 * 只用于比较，账本里保存的仍是用户或短信提供的原始写法。
 */
internal fun normalizedIncomeTitle(title: String): String = buildString(title.length) {
    title.forEach { origin ->
        val ch = foldWidth(origin)
        if (!ch.isWhitespace()) append(ch.lowercaseChar())
    }
}

/** 全角字符统一按半角处理，中文句读映射到最接近的 ASCII 标点。 */
private fun foldWidth(ch: Char): Char = when {
    ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
    ch == '。' -> '.'
    ch == '、' -> ','
    ch == '【' -> '['
    ch == '】' -> ']'
    ch == '「' || ch == '」' -> '"'
    ch == '—' || ch == '–' -> '-'
    ch == '…' -> '.'
    else -> ch
}

/**
 * 理财赎回、提现和银证转账这类到账不是收入的短信，识别后默认记为忽略。
 *
 * 关键词只覆盖“转入本卡的闲置资金”，不含“收益”“利息”等真正的收入项目。
 */
private val NonIncomeMarkers = listOf("赎回", "提现", "银证", "证券", "转存")

internal fun looksLikeNonIncome(title: String): Boolean {
    val folded = normalizedIncomeTitle(title)
    return NonIncomeMarkers.any { folded.contains(it) }
}
