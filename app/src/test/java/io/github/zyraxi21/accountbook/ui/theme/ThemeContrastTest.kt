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
        for (dark in listOf(false, true)) verifyPalette(bookPalette(BookAliasTokens(null, dark), dark), dark)
        assertEquals(Color(0xFF0F6CBD), bookPalette(BookAliasTokens(null, false), false).brand)
    }

    @Test fun dynamicPaletteKeepsTextReadableForEverySeedAndMode() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            verifyPalette(bookPalette(BookAliasTokens(seed, dark), dark), dark)
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
            // 顶栏纯色延伸到状态栏：浅色主题的主题填充色与深色主题的中性表面色都承托浅色状态图标。
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

    @Test fun accentButtonsKeepReadableTextInEveryState() {
        for (seed in seeds + listOf<Int?>(null)) for (dark in listOf(false, true)) {
            val palette = bookPalette(BookAliasTokens(seed, dark), dark)
            // 填充按钮和 FAB 使用独立的主题填充色，深色主题下也不跟随顶栏转为中性色。
            assertEquals("按钮应使用主题填充色", palette.primary, palette.primaryColor())
            // 填充按钮的文字色来自 Fluent 的 ForegroundOnColor，与顶栏前景色无关。
            val buttonText = BookAliasTokens(seed, dark)
                .neutralForegroundColor[NeutralForegroundColorTokens.ForegroundOnColor]
                .let { if (dark) it.dark else it.light }
            for (pressed in listOf(false, true)) {
                val background = palette.primaryColor(pressed)
                assertContrast(buttonText, background, "seed=$seed dark=$dark pressed=$pressed")
                if (dark) assertTrue("深色填充按钮不应过亮", relativeLuminance(background) < 0.15)
            }
            // 按压态必须比常态明显更深，否则按下去没有反馈。
            assertTrue("按压态应深于常态",
                relativeLuminance(palette.primaryColor(pressed = true)) < relativeLuminance(palette.primaryColor()))
        }
    }

    private fun verifyPalette(palette: BookPalette, darkTheme: Boolean) {
        if (darkTheme) {
            assertTrue("深色 Snackbar 不应变为亮白色", relativeLuminance(palette.snackbarSurface) < 0.15)
            assertTrue("深色顶栏应使用中性表面色", palette.topBar != palette.primary)
        } else {
            assertTrue("浅色 Snackbar 应使用淡主题色背景", relativeLuminance(palette.snackbarSurface) > 0.5)
            assertEquals("浅色顶栏应使用主题填充色", palette.primary, palette.topBar)
        }
        assertContrast(palette.onSnackbar, palette.snackbarSurface)
        // 提示气泡两种主题都不应出现大块浅底，且正文保持可读；底色不能压到纯黑。
        assertTrue("提示气泡底色应保持深色", relativeLuminance(palette.tooltipSurface) < 0.3)
        assertTrue("提示气泡底色不应为纯黑", relativeLuminance(palette.tooltipSurface) > 0.01)
        assertTrue("提示气泡应保持半透明", palette.tooltipSurface.alpha < 1f)
        assertContrast(palette.onTooltip, palette.tooltipSurface)
        for (background in listOf(palette.background, palette.surface)) {
            for (foreground in listOf(palette.foreground, palette.secondary, palette.brand, palette.positive, palette.negative)) {
                assertContrast(foreground, background)
            }
        }
        assertContrast(palette.onTopBar, palette.topBar)
    }

    private fun assertContrast(foreground: Color, background: Color, context: String? = null) {
        val first = relativeLuminance(foreground)
        val second = relativeLuminance(background)
        val ratio = (max(first, second) + 0.05) / (min(first, second) + 0.05)
        val where = if (context == null) "" else "（$context）"
        assertTrue("正文和系统栏图标的对比度应至少为 4.5:1，实际为 $ratio$where", ratio >= 4.5)
    }
}
