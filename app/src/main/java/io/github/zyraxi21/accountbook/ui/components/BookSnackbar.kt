package io.github.zyraxi21.accountbook.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralForegroundColorTokens

val LocalBookSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }
val LocalAllowScreenshots = staticCompositionLocalOf { false }

/** 用 Fluent 的反色表面呈现短暂提示，弹窗内也提供同一个提示宿主。 */
@Composable
fun BookSnackbarHost(modifier: Modifier = Modifier) {
    val state = LocalBookSnackbar.current ?: return
    val foreground = FluentTheme.aliasTokens.neutralForegroundColor[NeutralForegroundColorTokens.Foreground1]
        .value(FluentTheme.themeMode)
    val background = FluentTheme.aliasTokens.neutralBackgroundColor[NeutralBackgroundColorTokens.Background2]
        .value(FluentTheme.themeMode)
    SnackbarHost(state, modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { data ->
        Snackbar(snackbarData = data, containerColor = foreground, contentColor = background,
            dismissActionContentColor = background, actionContentColor = background)
    }
}
