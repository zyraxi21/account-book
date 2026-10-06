package io.github.zyraxi21.accountbook.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.AppBar
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.navigation.TabBar
import com.microsoft.fluentui.tokenized.navigation.TabData
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.Channel
import io.github.zyraxi21.accountbook.domain.Income
import io.github.zyraxi21.accountbook.ui.assets.AssetEditor
import io.github.zyraxi21.accountbook.ui.assets.AssetsScreen
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.income.IncomeEditor
import io.github.zyraxi21.accountbook.ui.income.IncomeScreen
import io.github.zyraxi21.accountbook.ui.income.SmsInputEditor
import io.github.zyraxi21.accountbook.ui.settings.ChannelEditor
import io.github.zyraxi21.accountbook.ui.settings.SettingsScreen
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

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
    val message by vm.message.collectAsStateWithLifecycle()
    val palette = LocalBookPalette.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var tab by remember { mutableIntStateOf(0) }
    var deleteAsset by remember { mutableStateOf(false) }
    var deleteIncome by remember { mutableStateOf<Income?>(null) }
    var deleteChannel by remember { mutableStateOf<Channel?>(null) }
    var permissionExplanation by remember { mutableStateOf(false) }
    var smsPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsPermission = granted
        if (granted) vm.setSmsEnabled(true) else vm.notifyMessage(R.string.sms_grant_failed)
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
    val messageText = message?.let { stringResource(it) }
    Box(Modifier.fillMaxSize().background(palette.background).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            AppBar(title = stringResource(R.string.app_name), rightAccessoryView = {
                Button(onClick = vm::togglePrivacy, text = stringResource(R.string.privacy), style = ButtonStyle.OutlinedButton,
                    icon = ImageVector.vectorResource(if (hidden) R.drawable.ic_eye else R.drawable.ic_eye_off),
                    contentDescription = stringResource(if (hidden) R.string.privacy_show else R.string.privacy_hide),
                    modifier = Modifier.heightIn(min = 48.dp))
            })
            if (hidden && !state.loading && state.storageError == null) {
                BookText(stringResource(R.string.privacy_hint), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), 13.sp, color = palette.secondary)
            }
            if (messageText != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookText(messageText, Modifier.weight(1f), 14.sp, color = palette.brand)
                    Button(onClick = vm::dismissMessage, text = stringResource(R.string.close), style = ButtonStyle.OutlinedButton)
                }
            }
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BookText(stringResource(R.string.loading)) }
                    state.storageError != null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionHeading(stringResource(R.string.storage_error_title), stringResource(state.storageError!!.resource()))
                        BookText(stringResource(R.string.storage_error_hint), size = 14.sp, color = palette.secondary)
                        Button(onClick = vm::reload, text = stringResource(R.string.retry))
                    }
                    tab == 0 -> AssetsScreen(state.data, month, hidden, busy, { vm.moveMonth(-1) }, { vm.moveMonth(1) },
                        vm::currentMonth, vm::openAssets, { deleteAsset = true })
                    tab == 1 -> IncomeScreen(state.data, hidden, busy, { vm.openIncome() }, vm::openSmsInput,
                        { vm.openIncome(it) }, { deleteIncome = it })
                    else -> SettingsScreen(state.data, hidden, busy, smsPermission,
                        onSmsChange = { enabled ->
                            if (!enabled || smsPermission) vm.setSmsEnabled(enabled) else permissionExplanation = true
                        },
                        onPermissionSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null))) },
                        onAddChannel = { vm.openChannel() }, onRename = { vm.openChannel(it) }, onDelete = { deleteChannel = it })
                }
            }
            TabBar(tabDataList = listOf(
                TabData(stringResource(R.string.tab_assets), ImageVector.vectorResource(R.drawable.ic_assets), onClick = { tab = 0 },
                    accessibilityDescription = stringResource(if (tab == 0) R.string.tab_selected else R.string.tab_available, stringResource(R.string.tab_assets))),
                TabData(stringResource(R.string.tab_income), ImageVector.vectorResource(R.drawable.ic_income), onClick = { tab = 1 },
                    accessibilityDescription = stringResource(if (tab == 1) R.string.tab_selected else R.string.tab_available, stringResource(R.string.tab_income))),
                TabData(stringResource(R.string.tab_settings), ImageVector.vectorResource(R.drawable.ic_settings), onClick = { tab = 2 },
                    accessibilityDescription = stringResource(if (tab == 2) R.string.tab_selected else R.string.tab_available, stringResource(R.string.tab_settings))),
            ), selectedIndex = tab, showIndicator = true)
        }
    }
    if (!hidden && state.storageError == null) {
        assetDraft?.let { AssetEditor(it, vm, busy, messageText) }
        incomeDraft?.let { IncomeEditor(it, vm, busy, messageText, editingExisting = state.data.incomes.any { income -> income.id == it.id }) }
        channelDraft?.let { ChannelEditor(it, vm, busy, messageText) }
        smsText?.let { SmsInputEditor(it, vm, busy, messageText) }
        if (deleteAsset) ConfirmDialog(stringResource(R.string.delete_record_title), stringResource(R.string.delete_asset_hint), busy,
            { deleteAsset = false }, { deleteAsset = false; vm.deleteAsset() })
        deleteIncome?.let { income -> ConfirmDialog(stringResource(R.string.delete_record_title), stringResource(R.string.delete_income_hint), busy,
            { deleteIncome = null }, { deleteIncome = null; vm.deleteIncome(income.id) }) }
        deleteChannel?.let { channel -> ConfirmDialog(stringResource(R.string.delete_channel_title), stringResource(R.string.delete_channel_hint), busy,
            { deleteChannel = null }, { deleteChannel = null; vm.deleteChannel(channel.id) }) }
    }
    if (permissionExplanation) {
        EditorDialog(stringResource(R.string.sms_permission_required), busy = false, onClose = { permissionExplanation = false },
            saveLabel = stringResource(R.string.request_permission), onSave = {
                permissionExplanation = false
                permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }) { BookText(stringResource(R.string.sms_permission_explanation)) }
    }
}
