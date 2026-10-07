package io.github.zyraxi21.accountbook.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
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
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

@Composable
fun SettingsScreen(data: BookData, hidden: Boolean, busy: Boolean, smsPermission: Boolean, transferEnabled: Boolean,
                   transferProgress: Int?,
                   onSmsChange: (Boolean) -> Unit, onPermissionSettings: () -> Unit,
                   onExportJson: () -> Unit, onExportCsv: () -> Unit, onImport: () -> Unit,
                   onHideOnStartup: (Boolean) -> Unit, onAllowScreenshots: (Boolean) -> Unit, onAbout: () -> Unit) {
    val palette = LocalBookPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SectionHeading(stringResource(R.string.tab_settings)) }
        item { LedgerCard {
            SettingToggle(stringResource(R.string.sms_auto_import), stringResource(R.string.sms_auto_hint),
                data.settings.smsAutoImportEnabled && smsPermission, !busy, onSmsChange)
            BookText(stringResource(when {
                !smsPermission -> R.string.sms_unavailable
                data.settings.smsAutoImportEnabled -> R.string.sms_enabled
                else -> R.string.sms_disabled
            }), size = 13.sp, color = palette.brand)
            if (!smsPermission) Button(onClick = onPermissionSettings, text = stringResource(R.string.open_system_settings), style = ButtonStyle.OutlinedButton)
        } }
        item { LedgerCard {
            SettingToggle(stringResource(R.string.default_privacy_title), stringResource(R.string.default_privacy_hint),
                data.settings.hideOnStartup, !busy, onHideOnStartup)
            LedgerDivider()
            SettingToggle(stringResource(R.string.allow_screenshots_title), stringResource(R.string.allow_screenshots_hint),
                data.settings.allowScreenshots, !busy, onAllowScreenshots)
        } }
        item {
            FileTransferCard(data, hidden, transferEnabled, busy, transferProgress, onExportJson, onExportCsv, onImport)
        }
        item { Button(onClick = onAbout, text = stringResource(R.string.about_title), style = ButtonStyle.OutlinedButton,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) }
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
