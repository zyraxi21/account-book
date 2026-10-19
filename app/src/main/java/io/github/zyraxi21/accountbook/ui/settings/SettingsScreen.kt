package io.github.zyraxi21.accountbook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.TextField
import com.microsoft.fluentui.tokenized.controls.ToggleSwitch
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.ChannelDraft
import io.github.zyraxi21.accountbook.ui.SmsScanUiState
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

@Composable
fun SettingsScreen(data: BookData, hidden: Boolean, busy: Boolean, smsPermission: Boolean, transferEnabled: Boolean,
                   transferProgress: Int?,
                   onSmsChange: (Boolean) -> Unit,
                   onExportJson: () -> Unit, onExportCsv: () -> Unit, onImport: () -> Unit,
                   onHideOnStartup: (Boolean) -> Unit, onAllowScreenshots: (Boolean) -> Unit,
                   onUseWanGrouping: (Boolean) -> Unit, onDifferenceUsesNet: (Boolean) -> Unit, onAbout: () -> Unit,
                   pendingSmsCount: Int = 0, onReviewSms: () -> Unit = {},
                   smsScanState: SmsScanUiState = SmsScanUiState.Idle,
                   onRecheckSms: () -> Unit = {}, onGrantSms: () -> Unit = {},
                   showNotificationSmsHint: Boolean = false, onNotificationSmsSettings: () -> Unit = {}) {
    val palette = LocalBookPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SectionHeading(stringResource(R.string.tab_settings)) }
        item { LedgerCard {
            SettingToggle(stringResource(R.string.sms_auto_import), stringResource(R.string.sms_auto_hint),
                data.settings.smsAutoImportEnabled, !busy, onSmsChange)
            // 开关本身已经说明是否开启，只在缺权限时补一句原因。
            if (!smsPermission && smsScanState != SmsScanUiState.PermissionRequired) {
                BookText(stringResource(R.string.sms_unavailable), size = 13.sp, color = palette.brand)
            }
            if (data.settings.smsAutoImportEnabled) {
                if (showNotificationSmsHint) {
                    BookText(stringResource(R.string.sms_notification_permission_hint), size = 13.sp, color = palette.secondary)
                    Button(onClick = onNotificationSmsSettings, text = stringResource(R.string.sms_notification_permission_settings),
                        style = ButtonStyle.TextButton, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp))
                }
                val status = when (smsScanState) {
                    SmsScanUiState.Idle -> null
                    SmsScanUiState.Scanning -> stringResource(R.string.sms_scan_running)
                    SmsScanUiState.PermissionRequired -> stringResource(R.string.sms_scan_permission_required)
                    SmsScanUiState.Failed -> stringResource(R.string.sms_scan_failed)
                    is SmsScanUiState.Complete -> stringResource(R.string.sms_scan_complete,
                        smsScanState.checkedCount, smsScanState.recognizedCount)
                }
                status?.let { BookText(it, size = 13.sp, color = palette.secondary) }
                val needsPermission = !smsPermission || smsScanState == SmsScanUiState.PermissionRequired
                Button(onClick = if (needsPermission) onGrantSms else onRecheckSms,
                    text = stringResource(if (needsPermission) R.string.sms_read_permission_allow else R.string.sms_scan_recheck),
                    style = ButtonStyle.OutlinedButton, enabled = !busy && smsScanState != SmsScanUiState.Scanning,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                if (!needsPermission) BookText(stringResource(R.string.sms_scan_recheck_hint), size = 13.sp, color = palette.secondary)
            }
            if (pendingSmsCount > 0) {
                Button(onClick = onReviewSms, text = stringResource(R.string.sms_review_pending, pendingSmsCount),
                    style = ButtonStyle.OutlinedButton, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
        } }
        item { LedgerCard {
            SettingToggle(stringResource(R.string.default_privacy_title), stringResource(R.string.default_privacy_hint),
                data.settings.hideOnStartup, !busy, onHideOnStartup)
            LedgerDivider()
            SettingToggle(stringResource(R.string.allow_screenshots_title), stringResource(R.string.allow_screenshots_hint),
                data.settings.allowScreenshots, !busy, onAllowScreenshots)
        } }
        item {
            LedgerCard {
                SettingToggle(stringResource(R.string.wan_grouping_title),
                    stringResource(if (data.settings.useWanGrouping) R.string.wan_grouping_hint else R.string.thousands_grouping_hint),
                    data.settings.useWanGrouping, !busy, onUseWanGrouping)
                LedgerDivider()
                SettingChoice(stringResource(R.string.difference_basis_title),
                    netLabel = stringResource(R.string.difference_basis_net),
                    totalLabel = stringResource(R.string.difference_basis_total),
                    usesNet = data.settings.differenceUsesNet, enabled = !busy, onSelect = onDifferenceUsesNet)
            }
        }
        item {
            FileTransferCard(data, hidden, transferEnabled, busy, transferProgress, onExportJson, onExportCsv, onImport)
        }
        item { Button(onClick = onAbout, text = stringResource(R.string.about_title), style = ButtonStyle.OutlinedButton,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) }
    }
}

/** 二选一设置：两行互斥选项，选中后用圆点标记。 */
@Composable
private fun SettingChoice(title: String, netLabel: String, totalLabel: String,
                          usesNet: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BookText(title, weight = FontWeight.Medium, size = 16.sp)
        BookText(stringResource(R.string.difference_basis_hint), size = 13.sp,
            color = LocalBookPalette.current.secondary)
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            ChoiceRow(netLabel, usesNet, enabled) { onSelect(true) }
            ChoiceRow(totalLabel, !usesNet, enabled) { onSelect(false) }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalBookPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected, enabled = enabled, role = Role.RadioButton,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = palette.brand), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(20.dp).border(2.dp, if (selected) palette.brand else palette.stroke, CircleShape),
            contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).background(palette.brand, CircleShape))
        }
        BookText(label, Modifier.weight(1f), 15.sp, if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

/** 整行提供至少 48dp 点击区，轨道保持 Fluent 的 52×32dp，避免被最小高度拉伸。 */
@Composable
private fun SettingToggle(title: String, hint: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalBookPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = palette.brand), onValueChange = onChange)
        .semantics { contentDescription = title },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BookText(title, weight = FontWeight.Medium)
            BookText(hint, size = 13.sp, color = palette.secondary)
        }
        Box(Modifier.width(52.dp).height(48.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            ToggleSwitch(onValueChange = onChange, checkedState = checked, enabledSwitch = enabled,
                modifier = Modifier.requiredSize(52.dp, 32.dp))
        }
    }
}

@Composable
fun ChannelEditor(draft: ChannelDraft, vm: BookViewModel, busy: Boolean) {
    EditorDialog(stringResource(if (draft.id == null) R.string.add_channel else R.string.rename_channel), busy, vm::closeChannelDraft,
        stringResource(R.string.save_channel), vm::saveChannel) {
        TextField(draft.name, { vm.updateChannelName(it.take(40)) }, Modifier.fillMaxWidth(), label = stringResource(R.string.channel_name),
            hintText = stringResource(R.string.channel_name_hint))
    }
}
