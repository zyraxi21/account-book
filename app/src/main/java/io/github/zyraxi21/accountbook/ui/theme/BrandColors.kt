package io.github.zyraxi21.accountbook.ui.theme

import androidx.compose.ui.graphics.Color
import com.microsoft.fluentui.theme.token.AliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.BrandColorTokens
import com.microsoft.fluentui.theme.token.TokenSet
import kotlin.math.pow

/** 与参考项目一样，只替换品牌色阶，保留 Fluent 的语义颜色与控件状态。 */
internal class BookAliasTokens(seed: Int?, darkTheme: Boolean) : AliasTokens() {
    private val ramp = seed?.let { generateBrandRamp(it, if (darkTheme) 0.55f else 1f) }
    private val defaultRamp = super.brandColor

    override val brandColor = TokenSet<BrandColorTokens, Color> { token ->
        ramp?.getValue(token) ?: defaultRamp[token]
    }
}

/** Fluent 官方蓝色阶的相对亮度，避免浅黄、灰色等动态种子生成低对比度按钮。 */
private val BrandLuminance = doubleArrayOf(
    0.0078, 0.0154, 0.0251, 0.0401,
    0.0588, 0.0834, 0.1077, 0.1450,
    0.2278, 0.3239, 0.3832, 0.4450,
    0.5377, 0.6470, 0.7565, 0.8879,
)

internal fun relativeLuminance(color: Color): Double {
    fun linear(component: Float): Double {
        val value = component.toDouble()
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
}

/** 系统图标按实际承托表面的对比度选择明暗，而非只按系统主题判断。 */
internal fun useDarkSystemBarIcons(background: Color): Boolean {
    val luminance = relativeLuminance(background)
    return (luminance + 0.05) / 0.05 > 1.05 / (luminance + 0.05)
}

internal fun generateBrandRamp(seed: Int, saturationScale: Float = 1f): Map<BrandColorTokens, Color> {
    val base = Color(seed or 0xFF000000.toInt())
    val maximum = maxOf(base.red, base.green, base.blue)
    val minimum = minOf(base.red, base.green, base.blue)
    val chroma = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    val saturation = when {
        chroma == 0f -> 0f
        lightness < 0.5f -> chroma / (maximum + minimum)
        else -> chroma / (2f - maximum - minimum)
    }.let { (it * saturationScale).coerceIn(0f, 1f) }
    val hue = when {
        chroma == 0f -> 0f
        maximum == base.red -> ((base.green - base.blue) / chroma).let { if (it < 0f) it + 6f else it } / 6f
        maximum == base.green -> ((base.blue - base.red) / chroma + 2f) / 6f
        else -> ((base.red - base.green) / chroma + 4f) / 6f
    }
    return BrandColorTokens.entries.mapIndexed { index, token ->
        var low = 0f
        var high = 1f
        repeat(24) {
            val middle = (low + high) / 2f
            if (relativeLuminance(hslColor(hue, saturation, middle)) > BrandLuminance[index]) high = middle
            else low = middle
        }
        token to hslColor(hue, saturation, (low + high) / 2f)
    }.toMap()
}

private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    if (saturation == 0f) return Color(lightness, lightness, lightness, 1f)
    val upper = if (lightness < 0.5f) lightness * (1f + saturation)
        else lightness + saturation - lightness * saturation
    val lower = 2f * lightness - upper
    fun channel(position: Float): Float {
        val wrapped = when {
            position < 0f -> position + 1f
            position > 1f -> position - 1f
            else -> position
        }
        return when {
            wrapped < 1f / 6f -> lower + (upper - lower) * 6f * wrapped
            wrapped < 1f / 2f -> upper
            wrapped < 2f / 3f -> lower + (upper - lower) * (2f / 3f - wrapped) * 6f
            else -> lower
        }
    }
    return Color(channel(hue + 1f / 3f), channel(hue), channel(hue - 1f / 3f), 1f)
}
