package io.github.zyraxi21.accountbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode

data class BookPalette(
    val background: Color, val surface: Color, val foreground: Color,
    val secondary: Color, val brand: Color, val positive: Color, val negative: Color, val stroke: Color,
)

private val LightPalette = BookPalette(Color(0xFFF5F5F5), Color.White, Color(0xFF242424),
    Color(0xFF616161), Color(0xFF0F6CBD), Color(0xFF107C10), Color(0xFFC50F1F), Color(0xFFE0E0E0))
private val DarkPalette = BookPalette(Color(0xFF141414), Color(0xFF242424), Color(0xFFF5F5F5),
    Color(0xFFBDBDBD), Color(0xFF479EF5), Color(0xFF6CCB5F), Color(0xFFFF9999), Color(0xFF424242))
val LocalBookPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun AccountBookTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBookPalette provides if (darkTheme) DarkPalette else LightPalette) {
        FluentTheme(themeMode = if (darkTheme) ThemeMode.Dark else ThemeMode.Light, content = content)
    }
}
