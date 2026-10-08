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
import androidx.compose.ui.platform.testTag
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

val LocalBookSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }
val LocalAllowScreenshots = staticCompositionLocalOf { false }

/** 浅深色模式均使用沉稳的深色提示面，避免暗色界面中出现亮白提示。 */
@Composable
fun BookSnackbarHost(modifier: Modifier = Modifier) {
    val state = LocalBookSnackbar.current ?: return
    val palette = LocalBookPalette.current
    SnackbarHost(state, modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { data ->
        Snackbar(modifier = Modifier.testTag("book_snackbar").semantics { liveRegion = LiveRegionMode.Polite },
            containerColor = palette.snackbarSurface, contentColor = palette.onSnackbar, dismissAction = {
                IconButton(onClick = data::dismiss) {
                    Image(painterResource(R.drawable.ic_close), stringResource(R.string.close),
                        Modifier.size(20.dp), colorFilter = ColorFilter.tint(palette.onSnackbar))
                }
            }) { BookText(data.visuals.message, size = 14.sp, color = palette.onSnackbar) }
    }
}
