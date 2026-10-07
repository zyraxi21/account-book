package io.github.zyraxi21.accountbook.ui.settings

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.SheetAccessibilityAnnouncement
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheet
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheetValue
import com.microsoft.fluentui.tokenized.bottomsheet.rememberBottomSheetState
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 关于页以底部弹层呈现：高度受窗口约束，正文可滚动。
 * 下滑、点击遮罩、返回键和右上角叉形按钮都能关闭；窗口沿用账务的截屏保护，
 * 未授权截屏或隐藏账务时弹层同样不可截屏。
 */
@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val sheetState = rememberBottomSheetState(BottomSheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val close: () -> Unit = remember(sheetState, scope) {
        { scope.launch { sheetState.hide() }; Unit }
    }
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.85f }
    // Fluent 以内容函数的标识缓存测量结果；保持引用稳定，避免动画帧反复重置高度。
    val sheetContent: @Composable () -> Unit = remember(close, maxHeight) {
        { AboutContent(close, Modifier.heightIn(max = maxHeight)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) }
    }
    val opened = stringResource(R.string.about_sheet_opened)
    val expanded = stringResource(R.string.about_sheet_expanded)
    val collapsed = stringResource(R.string.about_sheet_collapsed)
    BackHandler(onBack = close)
    LaunchedEffect(sheetState) {
        // 先建立停靠位置，再由 Fluent 播放打开动画。
        snapshotFlow { sheetState.anchorsFilled }.first { it }
        launch { sheetState.show() }.join()
        // 正文下滑只改变 Fluent 的状态；所有关闭方式统一在停靠隐藏后移除弹层。
        snapshotFlow { sheetState.currentValue == BottomSheetValue.Hidden && !sheetState.isAnimationRunning }
            .first { it }
        dismiss()
    }
    BottomSheet(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        sheetState = sheetState,
        sheetContent = sheetContent,
        // 使用固定停靠模式，避免 SDK 的 slideOver 分支循环修改测量高度。
        slideOver = false,
        expandable = false,
        peekHeight = maxHeight.coerceAtMost(480.dp),
        scrimVisible = true,
        enableSwipeDismiss = true,
        talkbackAnnouncement = SheetAccessibilityAnnouncement(
            expandedToShown = opened, expandedToCollapsed = collapsed,
            shownToExpanded = expanded, shownToCollapsed = collapsed,
            collapsedToExpanded = expanded, collapsedToShown = opened,
        ),
        onDismiss = close,
        content = {},
    )
}

@Composable
private fun AboutContent(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalBookPalette.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName }
            .getOrNull().orEmpty()
    }
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 20.dp)
        .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookText(stringResource(R.string.about_title), Modifier.weight(1f), 22.sp, FontWeight.SemiBold)
            Button(onClick = onClose, style = ButtonStyle.TextButton, icon = ImageVector.vectorResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.close), modifier = Modifier.size(48.dp))
        }
        SectionHeading(stringResource(R.string.app_name), stringResource(R.string.about_version, version))
        LedgerCard {
            SectionHeading(stringResource(R.string.encrypted_local_title))
            BookText(stringResource(R.string.encrypted_local_hint), size = 14.sp, color = palette.secondary)
            LedgerDivider()
            BookText(stringResource(R.string.about_export_hint), size = 14.sp, color = palette.secondary)
        }
    }
}

@Preview(name = "浅色 · 关于弹层", widthDp = 393, heightDp = 420, showBackground = true)
@Composable
private fun LightAboutSheetPreview() { AboutSheetPreview(darkTheme = false) }

@Preview(name = "深色 · 关于弹层", widthDp = 393, heightDp = 420, showBackground = true)
@Composable
private fun DarkAboutSheetPreview() { AboutSheetPreview(darkTheme = true) }

@Composable
private fun AboutSheetPreview(darkTheme: Boolean) {
    AccountBookTheme(darkTheme = darkTheme, dynamicColor = false) {
        BottomSheet(modifier = Modifier.fillMaxSize(),
            sheetState = rememberBottomSheetState(BottomSheetValue.Shown),
            sheetContent = { AboutContent(onClose = {}) },
            slideOver = false, expandable = false, peekHeight = 360.dp,
            content = {})
    }
}
