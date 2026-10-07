package io.github.zyraxi21.accountbook.ui.theme

import androidx.compose.ui.graphics.Color
import com.microsoft.fluentui.theme.token.FluentAliasTokens.BrandColorTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.BrandBackgroundColorTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralForegroundColorTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** 检查最终颜色角色的可读性，覆盖直接使用动态种子时容易出问题的浅黄和灰色。 */
class ThemeContrastTest {
    private val seeds = listOf(
        0xFF0F6CBD.toInt(), 0xFFFFD54F.toInt(), 0xFF9A9A9A.toInt(),
        0xFF001E30.toInt(), 0xFF6750A4.toInt(), 0xFFFF0000.toInt(),
        0xFF00FF00.toInt(), 0xFF00FFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
    )

    @Test fun defaultPaletteHasReadableTextInBothModes() {
        for (dark in listOf(false, true)) verifyPalette(bookPalette(BookAliasTokens(null, dark), dark))
        assertEquals(Color(0xFF0F6CBD), bookPalette(BookAliasTokens(null, false), false).brand)
    }

    @Test fun dynamicPaletteKeepsTextReadableForEverySeedAndMode() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            verifyPalette(bookPalette(BookAliasTokens(seed, dark), dark))
        }
    }

    @Test fun brandRampsAreOpaqueAndIncreaseInLuminance() {
        for (seed in seeds) for (saturationScale in listOf(1f, 0.55f)) {
            val ramp = generateBrandRamp(seed, saturationScale)
            assertEquals(16, ramp.size)
            var previous = -1.0
            for (token in BrandColorTokens.entries) {
                val color = ramp.getValue(token)
                assertEquals(1f, color.alpha, 0f)
                val luminance = relativeLuminance(color)
                assertTrue("品牌色阶亮度必须递增", luminance > previous)
                previous = luminance
            }
        }
    }

    @Test fun systemIconsContrastWithTheActualBarBackgrounds() {
        for (seed in seeds + listOf<Int?>(null)) for (dark in listOf(false, true)) {
            val palette = bookPalette(BookAliasTokens(seed, dark), dark)
            // 浅色模式的品牌顶栏也需要浅色图标，不能仅按系统是否深色来判断。
            assertFalse(useDarkSystemBarIcons(palette.topBar))
            assertEquals(!dark, useDarkSystemBarIcons(palette.surface))
            for (background in listOf(palette.topBar, palette.surface)) {
                val foreground = if (useDarkSystemBarIcons(background)) Color.Black else Color.White
                assertContrast(foreground, background)
            }
        }
    }

    @Test fun darkAccentButtonsAreSubduedAndReadableInEveryState() {
        for (seed in seeds + listOf<Int?>(null)) {
            val tokens = BookAliasTokens(seed, true)
            val foreground = tokens.neutralForegroundColor[NeutralForegroundColorTokens.ForegroundOnColor].dark
            for (state in listOf(BrandBackgroundColorTokens.BrandBackground1,
                BrandBackgroundColorTokens.BrandBackground1Pressed, BrandBackgroundColorTokens.BrandBackground1Selected)) {
                val background = tokens.brandBackgroundColor[state].dark
                assertTrue("深色填充按钮不应使用高亮品牌色阶", relativeLuminance(background) < 0.15)
                assertContrast(foreground, background)
            }
        }
    }

    private fun verifyPalette(palette: BookPalette) {
        for (background in listOf(palette.background, palette.surface)) {
            for (foreground in listOf(palette.foreground, palette.secondary, palette.brand, palette.positive, palette.negative)) {
                assertContrast(foreground, background)
            }
        }
        assertContrast(palette.onTopBar, palette.topBar)
    }

    private fun assertContrast(foreground: Color, background: Color) {
        val first = relativeLuminance(foreground)
        val second = relativeLuminance(background)
        val ratio = (max(first, second) + 0.05) / (min(first, second) + 0.05)
        assertTrue("正文和系统栏图标的对比度应至少为 4.5:1，实际为 $ratio", ratio >= 4.5)
    }
}
