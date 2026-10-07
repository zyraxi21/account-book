package io.github.zyraxi21.accountbook.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.SheetAccessibilityAnnouncement
import com.microsoft.fluentui.tokenized.bottomsheet.BottomSheet
import com.microsoft.fluentui.tokenized.progress.LinearProgressIndicator
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
import io.github.zyraxi21.accountbook.domain.ReleaseNotesFormatter
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
    val sheetHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height.toDp() * 0.88f).coerceAtMost(760.dp) }
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
    val repositoryUrl = stringResource(R.string.about_repository_url)
    val palette = LocalBookPalette.current
    // 版本号统一来自构建配置的 versionName，此处只负责读取展示。
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName }
            .getOrNull().orEmpty()
    }
    Column(modifier.fillMaxWidth().testTag(ABOUT_SHEET_TAG)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BookText(stringResource(R.string.about_title), Modifier.weight(1f), 22.sp, FontWeight.SemiBold)
            Button(onClick = onClose, style = ButtonStyle.TextButton, icon = ImageVector.vectorResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.close), modifier = Modifier.size(48.dp))
        }
        Column(Modifier.fillMaxWidth().weight(1f)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SectionHeading(stringResource(R.string.app_name), stringResource(R.string.about_version, version))
            BookText(stringResource(R.string.about_description), size = 14.sp, color = palette.secondary)
            LedgerCard {
                BookText(stringResource(R.string.about_features_title), weight = FontWeight.Medium)
                BookText(stringResource(R.string.about_features), size = 14.sp, color = palette.secondary)
                LedgerDivider()
                BookText(stringResource(R.string.about_currency), size = 14.sp, color = palette.secondary)
            }
            LedgerCard {
                SectionHeading(stringResource(R.string.encrypted_local_title))
                BookText(stringResource(R.string.encrypted_local_hint), size = 14.sp, color = palette.secondary)
                LedgerDivider()
                BookText(stringResource(R.string.about_export_hint), size = 14.sp, color = palette.secondary)
            }
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, repositoryUrl.toUri())) },
                text = stringResource(R.string.about_source), style = ButtonStyle.TextButton,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LedgerDivider()
            UpdateSection(updateState, vm)
            BookSnackbarHost()
        }
    }
    // 新版本弹窗与安装授权引导独立于弹层内容，避免被滚动区域裁剪。
    when (val state = updateState) {
        is UpdateUiState.Available -> UpdateAvailableDialog(state, vm)
        is UpdateUiState.Downloading -> UpdateProgressDialog(state, vm)
        is UpdateUiState.ReadyToInstall -> UpdateInstallDialog(vm)
        else -> Unit
    }
}

/** 底部“检查更新”区块：按钮、进度与安装入口都集中在这里。 */
@Composable
private fun UpdateSection(updateState: UpdateUiState, vm: BookViewModel) {
    val palette = LocalBookPalette.current
    val checking = updateState is UpdateUiState.Checking
    val downloading = updateState is UpdateUiState.Downloading
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (updateState) {
            is UpdateUiState.Available -> {
                BookText(stringResource(R.string.update_available_message,
                    updateState.release.version.toString()), size = 14.sp, color = palette.secondary)
                Button(onClick = { vm.startUpdateDownload() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    text = stringResource(R.string.update_download_now), enabled = updateState.release.apkUrl != null)
                Button(onClick = { vm.dismissUpdate() }, style = ButtonStyle.OutlinedButton,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), text = stringResource(R.string.update_later))
            }
            is UpdateUiState.ReadyToInstall -> {
                BookText(stringResource(R.string.update_download_done), size = 14.sp, color = palette.secondary)
                Button(onClick = { installOrGuide(vm) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    text = stringResource(R.string.update_install))
            }
            else -> Button(onClick = { vm.checkForUpdates() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                text = stringResource(if (checking) R.string.update_checking else R.string.update_check),
                enabled = !checking && !downloading)
        }
        if (downloading) {
            val progress = (updateState as UpdateUiState.Downloading).progress
            BookText(
                if (progress >= 0) stringResource(R.string.update_progress, progress)
                else stringResource(R.string.update_progress_unknown),
                size = 14.sp, color = palette.secondary)
        }
    }
}

/** 安装需要未知来源授权；未授权时引导到系统设置页，再由用户返回后点击安装。 */
private fun installOrGuide(vm: BookViewModel) {
    vm.installUpdate()
}

/** 新版本弹窗：展示版本号与更新说明原文，提供“稍后”与“立即下载”。 */
@Composable
private fun UpdateAvailableDialog(state: UpdateUiState.Available, vm: BookViewModel) {
    val release = state.release
    val maxHeight = bookDialogMaxHeight()
    // Release 正文是 Markdown，按纯文本渲染前先规整掉标记符号。
    val fallback = stringResource(R.string.update_no_notes)
    val notes = remember(release.notes, fallback) {
        ReleaseNotesFormatter.format(release.notes).ifEmpty { fallback }
    }
    EditorDialog(title = stringResource(R.string.update_available_title), busy = false,
        onClose = { vm.dismissUpdate() }, saveLabel = stringResource(R.string.update_download_now),
        onSave = { vm.startUpdateDownload() }, snackbarHost = false) {
        BookText(stringResource(R.string.update_available_message, release.version.toString()),
            size = 18.sp, weight = FontWeight.Medium)
        if (release.apkUrl == null) {
            BookText(stringResource(R.string.update_no_apk), size = 14.sp, color = LocalBookPalette.current.secondary)
        } else {
            BookText(stringResource(R.string.update_release_notes), size = 14.sp, color = LocalBookPalette.current.secondary)
            // 更新说明按原文展示，不解析 Markdown，避免把文本当成可执行内容渲染。
            BookText(notes, Modifier.heightIn(max = maxHeight * 0.5f)
                .verticalScroll(rememberScrollState()), 14.sp)
        }
    }
}

/** 下载进度弹窗：展示百分比并允许取消。进度条使用 Fluent 控件。 */
@Composable
private fun UpdateProgressDialog(state: UpdateUiState.Downloading, vm: BookViewModel) {
    EditorDialog(title = stringResource(R.string.update_downloading), busy = true,
        onClose = { vm.cancelUpdateDownload() }, saveLabel = stringResource(R.string.update_cancel_download),
        onSave = { vm.cancelUpdateDownload() }, snackbarHost = false) {
        BookText(
            if (state.progress >= 0) stringResource(R.string.update_progress, state.progress)
            else stringResource(R.string.update_progress_unknown), size = 16.sp)
        // 总量未知时用不确定进度样式，避免显示成 0% 造成误判。
        if (state.progress >= 0) {
            LinearProgressIndicator(progress = state.progress / 100f, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * 下载完成后的安装弹窗。
 * 授权指引常驻显示：设置页拉起失败时用户仍能在此看到该去哪里开启，
 * 不依赖一次性的 Snackbar——从系统设置返回后弹窗仍在。
 */
@Composable
private fun UpdateInstallDialog(vm: BookViewModel) {
    val settingsMissing by vm.installSettingsMissing.collectAsStateWithLifecycle()
    EditorDialog(title = stringResource(R.string.update_download_done), busy = false,
        onClose = { vm.dismissUpdate(); vm.clearInstallSettingsMissing() },
        saveLabel = stringResource(R.string.update_install),
        onSave = { vm.clearInstallSettingsMissing(); installOrGuide(vm) }, snackbarHost = false) {
        BookText(stringResource(R.string.update_install_permission_message), size = 14.sp,
            color = LocalBookPalette.current.secondary)
        if (settingsMissing) {
            // 设置页未能拉起时给出可手动执行的路径，而不是反复弹出提示。
            BookText(stringResource(R.string.update_install_manual_path), size = 14.sp,
                color = LocalBookPalette.current.secondary)
        }
    }
}

@Preview(name = "浅色 · 关于弹层", widthDp = 393, heightDp = 780, showBackground = true)
@Composable
private fun LightAboutSheetPreview() { AboutSheetPreview(darkTheme = false) }

@Preview(name = "深色 · 关于弹层", widthDp = 393, heightDp = 780, showBackground = true)
@Composable
private fun DarkAboutSheetPreview() { AboutSheetPreview(darkTheme = true) }

@Composable
private fun AboutSheetPreview(darkTheme: Boolean) {
    AccountBookTheme(darkTheme = darkTheme, dynamicColor = false) {
        BottomSheet(modifier = Modifier.fillMaxSize(),
            sheetState = rememberBottomSheetState(BottomSheetValue.Shown),
            sheetContent = { AboutContent(onClose = {}, UpdateUiState.Idle, previewViewModel(), Modifier.height(680.dp)) },
            slideOver = false, expandable = false, peekHeight = 700.dp,
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
    override suspend fun importSms(parsed: ParsedIcbcIncome, requireAutoEnabled: Boolean) = false
    override suspend fun recordExport(exportedAt: Instant) = Unit
    override suspend fun importBook(data: BookData, mode: ImportMode) = BookImportResult(mode, 0, 0, 0)
}
