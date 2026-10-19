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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
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
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

@Composable
fun SettingsScreen(data: BookData, hidden: Boolean, busy: Boolean, transferEnabled: Boolean,
                   transferProgress: Int?,
                   onSmsChange: (Boolean) -> Unit,
                   onExportJson: () -> Unit, onExportCsv: () -> Unit, onImport: () -> Unit,
                   onHideOnStartup: (Boolean) -> Unit, onAllowScreenshots: (Boolean) -> Unit,
                   onUseWanGrouping: (Boolean) -> Unit, onDifferenceUsesNet: (Boolean) -> Unit, onAbout: () -> Unit,
                   pendingSmsCount: Int = 0, onSmsHistory: () -> Unit = {}, onSmsInfo: () -> Unit = {},
                   onSmsCardIncluded: (String, Boolean) -> Unit = { _, _ -> }) {
    val palette = LocalBookPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SectionHeading(stringResource(R.string.tab_settings)) }
        item { LedgerCard(Modifier.testTag("sms_settings_card")) {
            SettingToggle(stringResource(R.string.sms_auto_import), stringResource(R.string.sms_auto_hint),
                data.settings.smsAutoImportEnabled, !busy, onSmsChange)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSmsHistory,
                    text = if (pendingSmsCount > 0) stringResource(R.string.sms_history_entry_pending, pendingSmsCount)
                        else stringResource(R.string.sms_history_title),
                    style = ButtonStyle.TextButton, enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                Button(onClick = onSmsInfo, icon = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.sms_permissions_title),
                    style = ButtonStyle.TextButton, modifier = Modifier.size(48.dp))
            }
            // 卡列表由补查建立，新发现的卡默认导入，这里只负责关掉不想记的卡。
            if (data.settings.smsCards.isNotEmpty()) {
                LedgerDivider()
                BookText(stringResource(R.string.sms_card_title), weight = FontWeight.Medium)
                BookText(stringResource(R.string.sms_card_hint), size = 13.sp, color = palette.secondary)
                data.settings.smsCards.forEach { card ->
                    // 账务隐藏时尾号同样打码，与页面上其它金额、名称的遮罩保持一致。
                    val label = stringResource(R.string.sms_card_label,
                        if (hidden) stringResource(R.string.privacy_mask) else card.tail)
                    SettingRow(label, card.included, !busy) { onSmsCardIncluded(card.tail, it) }
                }
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

/** 只有标题的开关行：开关语义仍用完整标题，未开启时文字降为次要色。 */
@Composable
private fun SettingRow(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalBookPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = palette.brand), onValueChange = onChange)
        .semantics { contentDescription = title },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BookText(title, Modifier.weight(1f), color = if (checked) palette.foreground else palette.secondary)
        Box(Modifier.width(52.dp).height(48.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            ToggleSwitch(onValueChange = onChange, checkedState = checked, enabledSwitch = enabled,
                modifier = Modifier.requiredSize(52.dp, 32.dp))
        }
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
