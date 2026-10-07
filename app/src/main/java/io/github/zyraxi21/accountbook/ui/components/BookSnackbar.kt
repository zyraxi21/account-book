package io.github.zyraxi21.accountbook.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zyraxi21.accountbook.R
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralForegroundColorTokens

val LocalBookSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }
val LocalAllowScreenshots = staticCompositionLocalOf { false }

/** 短暂提示使用 Fluent 反色表面；编辑弹窗内也提供同一提示宿主。 */
@Composable
fun BookSnackbarHost(modifier: Modifier = Modifier) {
    val state = LocalBookSnackbar.current ?: return
    val foreground = FluentTheme.aliasTokens.neutralForegroundColor[NeutralForegroundColorTokens.Foreground1].value(FluentTheme.themeMode)
    val background = FluentTheme.aliasTokens.neutralBackgroundColor[NeutralBackgroundColorTokens.Background2].value(FluentTheme.themeMode)
    SnackbarHost(state, modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { data ->
        Snackbar(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            containerColor = foreground, contentColor = background, dismissAction = {
                IconButton(onClick = data::dismiss) {
                    Image(painterResource(R.drawable.ic_close), stringResource(R.string.close),
                        Modifier.size(20.dp), colorFilter = ColorFilter.tint(background))
                }
            }) { BookText(data.visuals.message, size = 14.sp, color = background) }
    }
}
