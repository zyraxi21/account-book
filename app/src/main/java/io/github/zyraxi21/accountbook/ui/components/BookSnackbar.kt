package io.github.zyraxi21.accountbook.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.tokenized.notification.Snackbar
import com.microsoft.fluentui.tokenized.notification.SnackbarState
import io.github.zyraxi21.accountbook.R

val LocalBookSnackbar = staticCompositionLocalOf<SnackbarState?> { null }
val LocalAllowScreenshots = staticCompositionLocalOf { false }

/** 使用原生 Fluent 提示和关闭动画，浅色为淡品牌色，深色为低亮度品牌色。 */
@Composable
fun BookSnackbarHost(modifier: Modifier = Modifier) {
    val state = LocalBookSnackbar.current ?: return
    val metadata = state.currentSnackbar ?: return
    val scope = rememberCoroutineScope()
    val closeLabel = stringResource(R.string.close)
    // 库内关闭图标的语义固定为英文，宿主使用资源提供提示文本和中文关闭动作。
    Box(modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 8.dp)
        .testTag("book_snackbar").clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            text = AnnotatedString(metadata.message)
            customActions = listOf(CustomAccessibilityAction(closeLabel) { metadata.dismiss(scope); true })
        }) {
        Snackbar(snackbarState = state, enableSwipeToDismiss = true)
    }
}
