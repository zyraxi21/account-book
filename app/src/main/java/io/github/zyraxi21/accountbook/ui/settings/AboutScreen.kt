package io.github.zyraxi21.accountbook.ui.settings

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonSize
import com.microsoft.fluentui.theme.token.controlTokens.SheetAccessibilityAnnouncement
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheet
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheetValue
import com.microsoft.fluentui.tokenized.bottomsheet.rememberBottomSheetState
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.BookImportResult
import io.github.zyraxi21.accountbook.domain.BookRepository
import io.github.zyraxi21.accountbook.domain.Channel
import io.github.zyraxi21.accountbook.domain.ImportMode
import io.github.zyraxi21.accountbook.domain.Income
import io.github.zyraxi21.accountbook.domain.MonthlyAssetSnapshot
import io.github.zyraxi21.accountbook.sms.IcbcSmsParser
import io.github.zyraxi21.accountbook.sms.ParsedIcbcIncome
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.UpdateUiState
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * 关于弹层根节点标识，供界面测试断言提示位于弹层之内。
 * 公开而非私有，避免测试侧再写一份字符串而与实现脱节。
 */
const val ABOUT_SHEET_TAG = "about_sheet_root"

/**
 * 关于页以底部弹层呈现：高度受窗口约束，正文可滚动。
 * 下滑、点击遮罩、返回键和右上角叉形按钮都能关闭；窗口沿用账务的截屏保护，
 * 未授权截屏或隐藏账务时弹层同样不可截屏。
 *
 * 底部提供“检查更新”：查询仓库最新正式版本，按需下载安装包并调起系统安装器。
 * 该联网行为是应用唯一的出网路径，不涉及账务数据。
 */
@Composable
fun AboutScreen(vm: BookViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberBottomSheetState(BottomSheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val close: () -> Unit = remember(sheetState, scope) {
        { scope.launch { sheetState.hide() }; Unit }
    }
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val sheetHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height.toDp() * 0.88f).coerceAtMost(560.dp) }
    // Fluent 以内容函数的标识缓存测量结果；保持引用稳定，避免动画帧反复重置高度。
    val sheetContent: @Composable () -> Unit = remember(close, sheetHeight, updateState) {
        // Fluent 把手占 4dp，上下各有 8dp 内边距；正文高度与可见停靠高度对应。
        { AboutContent(close, updateState, vm, Modifier.height(sheetHeight - 20.dp)
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
        peekHeight = sheetHeight,
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
private fun AboutContent(onClose: () -> Unit, updateState: UpdateUiState, vm: BookViewModel,
                         modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalBookPalette.current
    // 版本号统一来自构建配置的 versionName，此处只负责读取展示。
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName }
            .getOrNull().orEmpty()
    }
    Column(modifier.fillMaxWidth().testTag(ABOUT_SHEET_TAG)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            BookText(stringResource(R.string.about_title), Modifier.align(Alignment.Center), 20.sp, FontWeight.SemiBold)
            BookIconButton(R.drawable.ic_close, R.string.close, onClose, Modifier.align(Alignment.CenterEnd))
        }
        Column(Modifier.fillMaxWidth().weight(1f)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AboutLogo()
                BookText(stringResource(R.string.app_name), size = 24.sp, weight = FontWeight.SemiBold)
                BookText(stringResource(R.string.about_description), size = 14.sp, color = palette.secondary, align = TextAlign.Center)
                BookText(stringResource(R.string.about_version, version), size = 13.sp, color = palette.secondary)
            }
            Column(Modifier.fillMaxWidth()) {
                AboutLink(R.string.about_author_info, R.string.about_author_url)
                AboutLink(R.string.about_license_info, R.string.about_license_url)
                AboutLink(R.string.about_source, R.string.about_repository_url)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BookText(stringResource(R.string.encrypted_local_title), weight = FontWeight.Medium, align = TextAlign.Center)
                BookText(stringResource(R.string.encrypted_local_hint), size = 14.sp, color = palette.secondary, align = TextAlign.Center)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UpdateButton(updateState, vm)
            BookSnackbarHost()
        }
    }
}

/** 复用启动图标的路径，关于页的线条与背景使用同一动态主题色。 */
@Composable
private fun AboutLogo() {
    val brand = LocalBookPalette.current.brand
    val original = ImageVector.vectorResource(R.drawable.ic_launcher_foreground)
    val logo = remember(original, brand) {
        ImageVector.Builder(name = "AboutAppLogo", defaultWidth = original.defaultWidth,
            defaultHeight = original.defaultHeight, viewportWidth = original.viewportWidth,
            viewportHeight = original.viewportHeight).apply {
            for (index in 0 until original.root.size) {
                val path = original.root[index] as VectorPath
                addPath(pathData = path.pathData, pathFillType = path.pathFillType,
                    fill = path.fill, fillAlpha = path.fillAlpha,
                    stroke = path.stroke?.let { SolidColor(brand) }, strokeAlpha = path.strokeAlpha,
                    strokeLineWidth = path.strokeLineWidth, strokeLineCap = path.strokeLineCap,
                    strokeLineJoin = path.strokeLineJoin, strokeLineMiter = path.strokeLineMiter)
            }
        }.build()
    }
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(brand)
        .testTag("about_app_icon"), contentAlignment = Alignment.Center) {
        Image(logo, contentDescription = null, modifier = Modifier.requiredSize(108.dp))
    }
}

@Composable
private fun AboutLink(@StringRes text: Int, @StringRes url: Int) {
    val uriHandler = LocalUriHandler.current
    val destination = stringResource(url)
    Button(onClick = { uriHandler.openUri(destination) }, text = stringResource(text),
        style = ButtonStyle.TextButton, size = ButtonSize.Small, modifier = Modifier.fillMaxWidth())
}

/** 关于页仅保留检查入口，更新说明、下载进度及安装由应用层弹窗显示。 */
@Composable
private fun UpdateButton(updateState: UpdateUiState, vm: BookViewModel) {
    val checking = updateState is UpdateUiState.Checking
    Button(onClick = vm::checkForUpdates, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        text = stringResource(if (checking) R.string.update_checking else R.string.update_check),
        enabled = updateState is UpdateUiState.Idle)
}

@Preview(name = "关于弹层（浅色）", widthDp = 393, heightDp = 780, showBackground = true)
@Composable
private fun LightAboutSheetPreview() { AboutSheetPreview(darkTheme = false) }

@Preview(name = "关于弹层（深色）", widthDp = 393, heightDp = 780, showBackground = true)
@Composable
private fun DarkAboutSheetPreview() { AboutSheetPreview(darkTheme = true) }

@Composable
private fun AboutSheetPreview(darkTheme: Boolean) {
    AccountBookTheme(darkTheme = darkTheme, dynamicColor = false) {
        BottomSheet(modifier = Modifier.fillMaxSize(),
            sheetState = rememberBottomSheetState(BottomSheetValue.Shown),
            sheetContent = { AboutContent(onClose = {}, UpdateUiState.Idle, previewViewModel(), Modifier.height(540.dp)) },
            slideOver = false, expandable = false, peekHeight = 560.dp,
            content = {})
    }
}

/**
 * 预览只需要一个不会发起网络与安装动作的实现。
 * 更新相关依赖留空，传入的 `UpdateUiState` 决定按钮呈现，不触发真实查询。
 */
@Composable
private fun previewViewModel(): BookViewModel {
    val context = LocalContext.current
    return remember(context) {
        BookViewModel(PreviewBookRepository, IcbcSmsParser())
    }
}

/** 预览用的空仓库：所有写操作直接返回默认值，观察流始终为空账本。 */
private object PreviewBookRepository : BookRepository {
    private val empty = BookData()
    override fun observeBook(): Flow<BookData> = MutableStateFlow(empty)
    override suspend fun saveAsset(snapshot: MonthlyAssetSnapshot, originalMonth: YearMonth?) = Unit
    override suspend fun deleteAsset(month: YearMonth) = Unit
    override suspend fun saveIncome(income: Income, importFingerprint: String?) = Unit
    override suspend fun deleteIncome(id: String) = Unit
    override suspend fun addChannel(name: String): Channel = Channel(UUID.randomUUID().toString(), name, true, 0)
    override suspend fun renameChannel(id: String, name: String) = Unit
    override suspend fun deleteChannel(id: String) = Unit
    override suspend fun reorderChannels(orderedIds: List<String>) = Unit
    override suspend fun setSmsAutoImport(enabled: Boolean) = Unit
    override suspend fun setHideOnStartup(enabled: Boolean) = Unit
    override suspend fun setAllowScreenshots(enabled: Boolean) = Unit
    override suspend fun setUseWanGrouping(enabled: Boolean) = Unit
    override suspend fun importSms(parsed: ParsedIcbcIncome, requireAutoEnabled: Boolean) = false
    override suspend fun recordExport(exportedAt: Instant) = Unit
    override suspend fun importBook(data: BookData, mode: ImportMode) = BookImportResult(mode, 0, 0, 0)
}
