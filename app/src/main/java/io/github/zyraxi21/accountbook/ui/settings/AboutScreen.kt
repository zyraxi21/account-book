package io.github.zyraxi21.accountbook.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import kotlinx.coroutines.launch

/**
 * 关于页以底部弹层呈现：高度跟随内容，不铺满屏幕。
 * 下滑、点击遮罩、返回键和右上角叉形按钮都能关闭；窗口沿用账务的截屏保护，
 * 未授权截屏或隐藏账务时弹层同样不可截屏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val palette = LocalBookPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // 先播放收起动画再通知调用方移除，与下滑和点击遮罩的关闭过程保持一致。
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.foreground,
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.Inherit),
        dragHandle = { SheetDragHandle() },
    ) {
        AboutContent(close)
    }
}

/** 材料弹层自带的把手颜色来自未启用 Material 主题的默认配色，这里改用账本配色保证两种主题下都可见。 */
@Composable
private fun SheetDragHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 32.dp, height = 4.dp)
            .background(LocalBookPalette.current.stroke, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun AboutContent(onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalBookPalette.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName }
            .getOrNull().orEmpty()
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 20.dp)
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
        Column(Modifier.fillMaxSize().background(LocalBookPalette.current.surface)) {
            SheetDragHandle()
            AboutContent(onClose = {})
        }
    }
}
