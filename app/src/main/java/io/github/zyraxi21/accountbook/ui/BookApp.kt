package io.github.zyraxi21.accountbook.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.data.transfer.ExportFormat
import io.github.zyraxi21.accountbook.domain.Channel
import io.github.zyraxi21.accountbook.domain.Income
import io.github.zyraxi21.accountbook.domain.ImportMode
import io.github.zyraxi21.accountbook.ui.assets.AssetEditor
import io.github.zyraxi21.accountbook.ui.assets.AssetsScreen
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.income.IncomeEditor
import io.github.zyraxi21.accountbook.ui.income.IncomeScreen
import io.github.zyraxi21.accountbook.ui.income.SmsInputEditor
import io.github.zyraxi21.accountbook.ui.settings.ChannelEditor
import io.github.zyraxi21.accountbook.ui.settings.SettingsScreen
import io.github.zyraxi21.accountbook.ui.settings.AboutScreen
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun BookApp(vm: BookViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    // 隐私状态持续观察，后台隐藏不等待下次恢复生命周期。
    val hidden by vm.privacyHidden.collectAsState()
    val month by vm.selectedMonth.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val assetDraft by vm.assetDraft.collectAsStateWithLifecycle()
    val incomeDraft by vm.incomeDraft.collectAsStateWithLifecycle()
    val channelDraft by vm.channelDraft.collectAsStateWithLifecycle()
    val smsText by vm.smsText.collectAsStateWithLifecycle()
    val transferTask by vm.transferTask.collectAsStateWithLifecycle()
    val transferBusy by vm.transferBusy.collectAsStateWithLifecycle()
    val transferProgress by vm.transferProgress.collectAsStateWithLifecycle()
    val confirmReplace by vm.confirmReplace.collectAsStateWithLifecycle()
    val chooseImportMode by vm.chooseImportMode.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val palette = LocalBookPalette.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var tab by remember { mutableIntStateOf(0) }
    var aboutVisible by remember { mutableStateOf(false) }
    var deleteAsset by remember { mutableStateOf<YearMonth?>(null) }
    var deleteIncome by remember { mutableStateOf<Income?>(null) }
    var deleteChannel by remember { mutableStateOf<Channel?>(null) }
    var permissionExplanation by remember { mutableStateOf(false) }
    var smsPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsPermission = granted
        if (granted) vm.setSmsEnabled(true) else vm.notifyMessage(R.string.sms_grant_failed)
    }
    // 每个格式使用固定 MIME 的独立合约，避免重组尚未更新合约就启动旧格式选择器。
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.JSON.mimeType), vm::completeExport)
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType), vm::completeExport)
    // 导入：只接受 JSON/CSV；文件名用于在扩展名不可靠时判断内容格式。
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.completeImport(uri, displayName(context, uri))
    }
    LaunchedEffect(transferTask) {
        when (val task = transferTask) {
            is TransferTask.Export -> {
                val name = "${task.format.baseName}.${task.format.extension}"
                if (task.format == ExportFormat.JSON) jsonLauncher.launch(name) else csvLauncher.launch(name)
            }
            is TransferTask.Import -> importLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain"))
            null -> Unit
        }
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(hidden) {
        if (hidden) { focus.clearFocus(force = true); keyboard?.hide() }
    }
    LaunchedEffect(vm, snackbar, resources) {
        vm.message.filterNotNull().collect { template ->
            val summary = vm.importSummary.value
            val text = when {
                summary?.template == R.string.import_done_merge && summary.channels + summary.snapshots + summary.incomes == 0 ->
                    resources.getString(R.string.import_nothing_to_write)
                summary != null && summary.template == template -> resources.getString(template, summary.channels, summary.snapshots, summary.incomes)
                else -> resources.getString(template)
            }
            vm.dismissMessage()
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(text, withDismissAction = true) }
        }
    }
    // About弹层自带提示宿主，因此打开时收起主界面宿主，避免同一条消息出现两次。
    val modalVisible = aboutVisible || chooseImportMode || confirmReplace || permissionExplanation ||
        (!hidden && (assetDraft != null || incomeDraft != null || channelDraft != null || smsText != null ||
            deleteAsset != null || deleteIncome != null || deleteChannel != null))
    CompositionLocalProvider(LocalBookSnackbar provides snackbar, LocalAllowScreenshots provides state.data.settings.allowScreenshots) {
        Column(Modifier.fillMaxSize().background(palette.background), horizontalAlignment = Alignment.CenterHorizontally) {
            BookTopBar(hidden, vm::togglePrivacy)
            Column(Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                if (hidden && !state.loading && state.storageError == null) {
                    BookText(stringResource(R.string.privacy_hint), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), 13.sp, color = palette.secondary)
                }
                Box(Modifier.weight(1f)) {
                    when {
                        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BookText(stringResource(R.string.loading)) }
                        state.storageError != null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SectionHeading(stringResource(R.string.storage_error_title), stringResource(state.storageError!!.resource()))
                            BookText(stringResource(R.string.storage_error_hint), size = 14.sp, color = palette.secondary)
                            Button(onClick = vm::reload, text = stringResource(R.string.retry))
                        }
                        tab == 0 -> AssetsScreen(state.data, month, hidden, busy,
                            vm::openAssets, { deleteAsset = it }, currentMonth = vm.thisMonth,
                            onMonthSelected = vm::selectMonth)
                        // 收入页与资产页共用同一个 selectedMonth，切 Tab 时月份保持一致。
                        tab == 1 -> IncomeScreen(state.data, month, vm.thisMonth, hidden, busy,
                            onMonthSelected = vm::selectMonth,
                            onAdd = { vm.openIncome() }, onParse = vm::openSmsInput,
                            onEdit = { vm.openIncome(it) }, onDelete = { deleteIncome = it })
                        else -> SettingsScreen(state.data, hidden, busy || transferBusy, smsPermission, transferEnabled = vm.transferAvailable && !hidden && state.storageError == null,
                            transferProgress = transferProgress,
                            onSmsChange = { enabled ->
                                if (!enabled || smsPermission) vm.setSmsEnabled(enabled) else permissionExplanation = true
                            },
                            onPermissionSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null))) },
                            onExportJson = { vm.startExport(ExportFormat.JSON) },
                            onExportCsv = { vm.startExport(ExportFormat.CSV) },
                            onImport = vm::requestImport, onHideOnStartup = vm::setHideOnStartup,
                            onAllowScreenshots = vm::setAllowScreenshots, onAbout = { aboutVisible = true })
                    }
                    if (!modalVisible) BookSnackbarHost(Modifier.align(Alignment.BottomCenter))
                }
            }
            BookBottomBar(selectedIndex = tab, onSelect = { aboutVisible = false; tab = it })
        }
        if (!hidden && state.storageError == null) {
            if (channelDraft == null && deleteChannel == null) assetDraft?.let { draft ->
                AssetEditor(draft, vm, busy,
                    onRename = { id -> state.data.channels.firstOrNull { it.id == id }?.let(vm::openChannel) },
                    onDelete = { id -> deleteChannel = state.data.channels.firstOrNull { it.id == id } })
            }
            incomeDraft?.let { IncomeEditor(it, vm, busy, editingExisting = state.data.incomes.any { income -> income.id == it.id }) }
            channelDraft?.let { ChannelEditor(it, vm, busy) }
            smsText?.let { SmsInputEditor(it, vm, busy) }
            deleteAsset?.let { targetMonth ->
                ConfirmDialog(stringResource(R.string.delete_record_title), stringResource(R.string.delete_asset_hint), busy,
                    { deleteAsset = null }, { deleteAsset = null; vm.deleteAsset(targetMonth) })
            }
            deleteIncome?.let { income -> ConfirmDialog(stringResource(R.string.delete_record_title), stringResource(R.string.delete_income_hint), busy,
                { deleteIncome = null }, { deleteIncome = null; vm.deleteIncome(income.id) }) }
            deleteChannel?.let { channel -> ConfirmDialog(stringResource(R.string.delete_channel_title), stringResource(R.string.delete_channel_hint), busy,
                { deleteChannel = null }, { deleteChannel = null; vm.deleteChannel(channel.id) }) }
        }
        if (chooseImportMode) {
            EditorDialog(stringResource(R.string.import_choose_title), busy = transferBusy, onClose = vm::cancelImport,
                saveLabel = stringResource(R.string.import_merge_button), onSave = { vm.chooseImport(ImportMode.MERGE) }) {
                BookText(stringResource(R.string.import_merge_hint))
                LedgerDivider()
                BookText(stringResource(R.string.import_replace_hint))
                Button(onClick = { vm.chooseImport(ImportMode.REPLACE) }, text = stringResource(R.string.import_confirm_replace),
                    style = ButtonStyle.OutlinedButton, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
        }
        if (confirmReplace) {
            EditorDialog(stringResource(R.string.import_replace_title), busy = transferBusy, onClose = vm::cancelImport,
                saveLabel = stringResource(R.string.import_confirm_replace), onSave = vm::startImportAfterConfirm) {
                BookText(stringResource(R.string.import_replace_message))
            }
        }
        if (permissionExplanation) {
            EditorDialog(stringResource(R.string.sms_permission_required), busy = false, onClose = { permissionExplanation = false },
                saveLabel = stringResource(R.string.request_permission), onSave = {
                    permissionExplanation = false
                    permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                }) { BookText(stringResource(R.string.sms_permission_explanation)) }
        }
        // 关于页以底部弹层显示，设置页和底部导航保持可见，下滑或点击遮罩即可关闭。
        if (aboutVisible) AboutScreen(vm) { aboutVisible = false }
    }
}

/** 读取系统文件选择器返回的显示名，提供方不响应查询时退化为空串。 */
private fun displayName(context: Context, uri: Uri?): String {
    if (uri == null) return ""
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty()
}
