package io.github.zyraxi21.accountbook.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.AliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentColor

data class BookPalette(
    val background: Color, val surface: Color, val foreground: Color,
    val secondary: Color, val brand: Color, val positive: Color, val negative: Color, val stroke: Color,
    /** 顶栏底色与前景色：浅色主题用品牌填充色，深色主题用中性表面色。 */
    val topBar: Color, val onTopBar: Color,
    /** 填充按钮与 FAB 的主题色：由系统壁纸的莫奈取色派生，不跟随顶栏改为中性。 */
    val primary: Color,
    /** 提示气泡的半透明深灰底、描边与前景色，两种主题都不使用纯黑或大块浅底。 */
    val tooltipSurface: Color, val tooltipBorder: Color, val onTooltip: Color,
    val snackbarSurface: Color, val onSnackbar: Color,
)

val LocalBookPalette = staticCompositionLocalOf { bookPalette(AliasTokens(), darkTheme = false) }

internal fun bookPalette(tokens: AliasTokens, darkTheme: Boolean): BookPalette {
    fun FluentColor.resolve(): Color = if (darkTheme) dark else light
    // Fluent 的 token 仍叫 BrandBackground1，但它由系统壁纸取色派生，语义上就是主题填充色。
    val primaryFill = tokens.brandBackgroundColor[FluentAliasTokens.BrandBackgroundColorTokens.BrandBackground1].resolve()
    return BookPalette(
        background = tokens.neutralBackgroundColor[FluentAliasTokens.NeutralBackgroundColorTokens.CanvasBackground].resolve(),
        surface = tokens.neutralBackgroundColor[FluentAliasTokens.NeutralBackgroundColorTokens.Background2].resolve(),
        foreground = tokens.neutralForegroundColor[FluentAliasTokens.NeutralForegroundColorTokens.Foreground1].resolve(),
        secondary = tokens.neutralForegroundColor[FluentAliasTokens.NeutralForegroundColorTokens.Foreground2].resolve(),
        brand = tokens.brandForegroundColor[FluentAliasTokens.BrandForegroundColorTokens.BrandForeground1].resolve(),
        // 涨跌颜色保留固定语义，避免随壁纸改变财务信息的含义。
        positive = if (darkTheme) Color(0xFF6CCB5F) else Color(0xFF107C10),
        negative = if (darkTheme) Color(0xFFFF9999) else Color(0xFFC50F1F),
        stroke = tokens.neutralStrokeColor[FluentAliasTokens.NeutralStrokeColorTokens.Stroke1].resolve(),
        // 深色主题改用中性表面色承托顶栏，浅色主题保留品牌填充色。
        topBar = if (darkTheme) tokens.neutralBackgroundColor[FluentAliasTokens.NeutralBackgroundColorTokens.Background2].dark
            else primaryFill,
        onTopBar = if (darkTheme) tokens.neutralForegroundColor[FluentAliasTokens.NeutralForegroundColorTokens.Foreground1].dark
            else tokens.neutralForegroundColor[FluentAliasTokens.NeutralForegroundColorTokens.ForegroundOnColor].light,
        primary = primaryFill,
        // 气泡用半透明深灰而不是纯黑：能透出一点下层页面，也不会在深色主题里沉成一块死黑。
        // Popup 是独立窗口，Compose 的 Modifier.blur 只模糊自身内容，模糊不了背后的页面，
        // 所以这里只做半透明，不宣称高斯模糊。
        tooltipSurface = if (darkTheme) Color(0xFF3A3A3A).copy(alpha = 0.86f)
            else Color(0xFF2E2E2E).copy(alpha = 0.86f),
        // 深灰底上的细描边用低透明度白色勾边，比跟随主题的 stroke 更贴合悬浮感。
        tooltipBorder = if (darkTheme) Color(0x59FFFFFF) else Color(0x40FFFFFF),
        onTooltip = tokens.neutralForegroundColor[FluentAliasTokens.NeutralForegroundColorTokens.Foreground1].dark,
        snackbarSurface = tokens.brandBackgroundColor[FluentAliasTokens.BrandBackgroundColorTokens.BrandBackgroundTint].resolve(),
        onSnackbar = tokens.brandForegroundColor[FluentAliasTokens.BrandForegroundColorTokens.BrandForegroundTint].resolve(),
    )
}

/**
 * 填充按钮和 FAB 共用的主题填充色。按压时整体加深，不再使用渐变。
 * 纯色让文字对比度只有一档需要保证，不必再为渐变最亮端预留余量。
 */
internal fun BookPalette.primaryColor(pressed: Boolean = false): Color =
    if (pressed) lerp(primary, Color.Black, 0.12f) else primary

@Composable
fun AccountBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // minSdk 为 34，所有受支持设备均可使用系统动态取色。
    val seed = remember(context, configuration, darkTheme, dynamicColor) {
        if (!dynamicColor) null else {
            val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            scheme.primary.toArgb()
        }
    }
    val tokens = remember(seed, darkTheme) { BookAliasTokens(seed, darkTheme) }
    val palette = remember(tokens, darkTheme) { bookPalette(tokens, darkTheme) }
    val controlTokens = remember { BookControlTokens() }
    FluentTheme(aliasTokens = tokens, controlTokens = controlTokens, themeMode = if (darkTheme) ThemeMode.Dark else ThemeMode.Light) {
        CompositionLocalProvider(
            LocalBookPalette provides palette,
            LocalContentColor provides palette.foreground,
            LocalTextStyle provides FluentTheme.aliasTokens.typography[FluentAliasTokens.TypographyTokens.Body1],
        ) {
            SystemBarAppearance(palette)
            content()
        }
    }
}

@Composable
private fun SystemBarAppearance(palette: BookPalette) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() ?: return
    SideEffect {
        val window = activity.window
        WindowCompat.getInsetsController(window, view).apply {
            // 状态栏由顶栏底色承托，前景明暗按该底色的实际对比度决定。
            isAppearanceLightStatusBars = useDarkSystemBarIcons(palette.topBar)
            isAppearanceLightNavigationBars = useDarkSystemBarIcons(palette.surface)
        }
        // 底栏已承托系统导航按钮，关闭系统额外的灰色遮罩。
        window.isNavigationBarContrastEnforced = false
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
