package io.github.zyraxi21.accountbook.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.SnackbarStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.notification.SnackbarState
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.data.transfer.ExportFormat
import io.github.zyraxi21.accountbook.domain.Income
import io.github.zyraxi21.accountbook.domain.ImportMode
import io.github.zyraxi21.accountbook.sms.hasSmsReadPermission
import io.github.zyraxi21.accountbook.ui.assets.AssetEditor
import io.github.zyraxi21.accountbook.ui.assets.AssetsScreen
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.income.IncomeEditor
import io.github.zyraxi21.accountbook.ui.income.IncomeScreen
import io.github.zyraxi21.accountbook.ui.income.SmsInputEditor
import io.github.zyraxi21.accountbook.ui.income.SmsReviewDialog
import io.github.zyraxi21.accountbook.ui.income.SmsHistoryScreen
import io.github.zyraxi21.accountbook.ui.settings.ChannelEditor
import io.github.zyraxi21.accountbook.ui.settings.SettingsScreen
import io.github.zyraxi21.accountbook.ui.settings.AboutScreen
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import io.github.zyraxi21.accountbook.ui.update.UpdateDialogs
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
    val smsReview by vm.smsReview.collectAsStateWithLifecycle()
    val smsRecords by vm.smsIncomeRecords.collectAsStateWithLifecycle()
    val smsScanState by vm.smsScanState.collectAsStateWithLifecycle()
    val transferTask by vm.transferTask.collectAsStateWithLifecycle()
    val transferBusy by vm.transferBusy.collectAsStateWithLifecycle()
    val transferProgress by vm.transferProgress.collectAsStateWithLifecycle()
    val confirmReplace by vm.confirmReplace.collectAsStateWithLifecycle()
    val chooseImportMode by vm.chooseImportMode.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarState() }
    val palette = LocalBookPalette.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    // 后台完成查询时保留结果，回到前台后才呈现更新弹窗。
    val updateDialogVisible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        (updateState is UpdateUiState.Available || updateState is UpdateUiState.Downloading || updateState is UpdateUiState.ReadyToInstall)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var tab by remember { mutableIntStateOf(0) }
    var aboutVisible by remember { mutableStateOf(false) }
    var smsHistoryVisible by remember { mutableStateOf(false) }
    var smsInfoVisible by remember { mutableStateOf(false) }
    var deleteAsset by remember { mutableStateOf<YearMonth?>(null) }
    var deleteIncome by remember { mutableStateOf<Income?>(null) }
    var smsPermission by remember { mutableStateOf(hasSmsReadPermission(context)) }
    var smsPermissionRationale by remember { mutableStateOf(false) }
    var enableSmsAfterSettings by remember { mutableStateOf(false) }
    var recheckSmsAfterSettings by remember { mutableStateOf(false) }
    val smsPermissions = remember { arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        smsPermission = hasSmsReadPermission(context)
        when {
            smsPermission -> { enableSmsAfterSettings = false; vm.setSmsEnabled(true) }
            // 系统仍会弹窗时只提示一句，下次点开关会再问；
            // 不再弹窗（选过“不再询问”或被设备策略禁止）才引导到应用权限设置页。
            context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == true ->
                vm.notifyMessage(R.string.sms_grant_failed)
            else -> { enableSmsAfterSettings = true; openAppPermissionSettings(context) }
        }
    }
    val requestSmsPermission: () -> Unit = {
        when {
            hasSmsReadPermission(context) -> vm.setSmsEnabled(true)
            context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == true ->
                smsPermissionRationale = true
            else -> permissionLauncher.launch(smsPermissions)
        }
    }
    val openSmsHistory: () -> Unit = { vm.dismissSmsReview(); smsHistoryVisible = true }
    BackHandler(enabled = smsHistoryVisible && !smsInfoVisible) { smsHistoryVisible = false }
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
                smsPermission = hasSmsReadPermission(context)
                if (enableSmsAfterSettings && smsPermission) {
                    enableSmsAfterSettings = false
                    vm.setSmsEnabled(true)
                }
                if (recheckSmsAfterSettings) {
                    recheckSmsAfterSettings = false
                    vm.recheckSmsInbox()
                }
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
            snackbar.currentSnackbar?.dismiss()
            launch { snackbar.showSnackbar(text, style = SnackbarStyle.Accent, enableDismiss = true) }
        }
    }
    // About弹层自带提示宿主，因此打开时收起主界面宿主，避免同一条消息出现两次。
    val privateEditorVisible = !hidden && (assetDraft != null || incomeDraft != null || channelDraft != null || smsText != null ||
        deleteAsset != null || deleteIncome != null)
    val smsReviewVisible = smsReview.visible && state.data.settings.smsAutoImportEnabled && !state.loading && state.storageError == null &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && !aboutVisible && !updateDialogVisible && !chooseImportMode &&
        !confirmReplace && !privateEditorVisible && !smsPermissionRationale && !smsHistoryVisible && !smsInfoVisible &&
        !transferBusy && transferTask == null
    val modalVisible = aboutVisible || updateDialogVisible || chooseImportMode || confirmReplace || privateEditorVisible ||
        smsReviewVisible || smsPermissionRationale || smsInfoVisible
    CompositionLocalProvider(LocalBookSnackbar provides snackbar, LocalAllowScreenshots provides state.data.settings.allowScreenshots,
        LocalUseWanGrouping provides state.data.settings.useWanGrouping) {
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
                        smsHistoryVisible -> SmsHistoryScreen(smsRecords, hidden, busy, state.data.settings.smsAutoImportEnabled,
                            smsScanState, onBack = { smsHistoryVisible = false }, onReveal = vm::togglePrivacy,
                            onRecheck = { if (hasSmsReadPermission(context)) vm.recheckSmsInbox() else requestSmsPermission() },
                            onInfo = { smsInfoVisible = true },
                            onImport = { vm.resolveSmsRecord(it, import = true) }, onIgnore = { vm.resolveSmsRecord(it, import = false) })
                        tab == 0 -> AssetsScreen(state.data, month, hidden, busy,
                            vm::openAssets, { deleteAsset = it }, currentMonth = vm.thisMonth,
                            onMonthSelected = vm::selectMonth)
                        // 收入页与资产页共用同一个 selectedMonth，切 Tab 时月份保持一致。
                        tab == 1 -> IncomeScreen(state.data, month, vm.thisMonth, hidden, busy,
                            onMonthSelected = vm::selectMonth,
                            onAdd = { vm.openIncome() }, onParse = vm::openSmsInput,
                            onEdit = { vm.openIncome(it) }, onDelete = { deleteIncome = it }, onSmsHistory = openSmsHistory)
                        else -> SettingsScreen(state.data, hidden, busy || transferBusy, transferEnabled = vm.transferAvailable && !hidden && state.storageError == null,
                            transferProgress = transferProgress,
                            onSmsChange = { enabled ->
                                if (!enabled) { enableSmsAfterSettings = false; vm.setSmsEnabled(false) }
                                else requestSmsPermission()
                            },
                            onExportJson = { vm.startExport(ExportFormat.JSON) },
                            onExportCsv = { vm.startExport(ExportFormat.CSV) },
                            onImport = vm::requestImport, onHideOnStartup = vm::setHideOnStartup,
                            onAllowScreenshots = vm::setAllowScreenshots, onUseWanGrouping = vm::setUseWanGrouping,
                            onDifferenceUsesNet = vm::setDifferenceUsesNet,
                            onAbout = { aboutVisible = true },
                            pendingSmsCount = smsReview.incomes.size, onSmsHistory = openSmsHistory,
                            onSmsInfo = { smsInfoVisible = true })
                    }
                    if (!modalVisible) BookSnackbarHost(Modifier.align(Alignment.BottomCenter))
                }
            }
            BookBottomBar(selectedIndex = tab, onSelect = { aboutVisible = false; smsHistoryVisible = false; smsInfoVisible = false; tab = it })
        }
        if (!hidden && state.storageError == null) {
            // 渠道删除确认在编辑弹窗内部切换内容，不再需要额外的弹窗状态，也不会顶掉编辑器。
            if (channelDraft == null) assetDraft?.let { draft ->
                AssetEditor(draft, vm, busy,
                    onAdd = { vm.openChannel() },
                    onRename = { id -> state.data.channels.firstOrNull { it.id == id }?.let(vm::openChannel) })
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
        }
        if (smsReviewVisible) SmsReviewDialog(smsReview, hidden, busy, vm, onHistory = openSmsHistory)
        if (smsInfoVisible) {
            EditorDialog(stringResource(R.string.sms_permissions_title), busy = false, onClose = { smsInfoVisible = false },
                saveLabel = stringResource(R.string.sms_system_permissions), cancelLabel = stringResource(R.string.close), onSave = {
                    smsInfoVisible = false
                    recheckSmsAfterSettings = true
                    openAppPermissionSettings(context)
                }) {
                BookText(stringResource(R.string.sms_permission_hint), size = 14.sp)
                BookText(stringResource(R.string.sms_history_local_hint), size = 13.sp, color = palette.secondary)
                if (!smsPermission) BookText(stringResource(R.string.sms_unavailable), size = 13.sp)
                if (isXiaomiDevice()) BookText(stringResource(R.string.sms_notification_permission_hint), size = 13.sp)
            }
        }
        if (smsPermissionRationale) {
            EditorDialog(stringResource(R.string.sms_permission_title), busy = false, onClose = { smsPermissionRationale = false },
                saveLabel = stringResource(R.string.sms_permission_allow), onSave = {
                    smsPermissionRationale = false
                    permissionLauncher.launch(smsPermissions)
                }) { BookText(stringResource(R.string.sms_permission_hint)) }
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
        // 关于页以底部弹层显示，设置页和底部导航保持可见，下滑或点击遮罩即可关闭。
        if (aboutVisible) AboutScreen(vm) { aboutVisible = false }
        if (updateDialogVisible) UpdateDialogs(updateState, vm, snackbarHost = !aboutVisible)
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

/** 权限回调里需要 Activity 才能判断系统是否还会弹窗，这里沿 Context 链找到宿主。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun isXiaomiDevice() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

/** 小米的通知类短信由额外系统权限控制，引导至权限编辑页，其他设备进入应用详情页。 */
private fun openAppPermissionSettings(context: Context) {
    if (isXiaomiDevice()) {
        val editor = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setPackage("com.miui.securitycenter")
            .putExtra("extra_pkgname", context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(editor) }.isSuccess) return
    }
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null))
    // 部分 ROM 会拒绝该跳转，失败时静默处理，不因跳不过去而崩溃。
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
