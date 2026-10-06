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
    val topBar: Color, val onTopBar: Color,
)

val LocalBookPalette = staticCompositionLocalOf { bookPalette(AliasTokens(), darkTheme = false) }

internal fun bookPalette(tokens: AliasTokens, darkTheme: Boolean): BookPalette {
    fun FluentColor.resolve(): Color = if (darkTheme) dark else light
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
        topBar = if (darkTheme) tokens.neutralBackgroundColor[FluentAliasTokens.NeutralBackgroundColorTokens.Background3].dark
            else tokens.brandBackgroundColor[FluentAliasTokens.BrandBackgroundColorTokens.BrandBackground1].light,
        onTopBar = if (darkTheme) tokens.neutralForegroundColor[FluentAliasTokens.NeutralForegroundColorTokens.Foreground1].dark
            else Color.White,
    )
}

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
    FluentTheme(aliasTokens = tokens, themeMode = if (darkTheme) ThemeMode.Dark else ThemeMode.Light) {
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
