package io.github.zyraxi21.accountbook.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import io.github.zyraxi21.accountbook.domain.Channel
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.ChannelDraft
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

@Composable
fun SettingsScreen(data: BookData, hidden: Boolean, busy: Boolean, smsPermission: Boolean, transferEnabled: Boolean,
                   transferProgress: Int?,
                   onSmsChange: (Boolean) -> Unit, onPermissionSettings: () -> Unit, onAddChannel: () -> Unit,
                   onRename: (Channel) -> Unit, onDelete: (Channel) -> Unit,
                   onExportJson: () -> Unit, onExportCsv: () -> Unit, onImport: () -> Unit) {
    val palette = LocalBookPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SectionHeading(stringResource(R.string.tab_settings)) }
        item { LedgerCard {
            val smsLabel = stringResource(R.string.sms_auto_import)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BookText(smsLabel, Modifier.weight(1f), weight = FontWeight.Medium)
                ToggleSwitch(onValueChange = onSmsChange, checkedState = data.settings.smsAutoImportEnabled && smsPermission, enabledSwitch = !busy,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = smsLabel })
            }
            BookText(stringResource(R.string.sms_auto_hint), size = 14.sp, color = palette.secondary)
            BookText(stringResource(when {
                !smsPermission -> R.string.sms_unavailable
                data.settings.smsAutoImportEnabled -> R.string.sms_enabled
                else -> R.string.sms_disabled
            }), size = 13.sp, color = palette.brand)
            if (!smsPermission) Button(onClick = onPermissionSettings, text = stringResource(R.string.open_system_settings), style = ButtonStyle.OutlinedButton)
        } }
        item { LedgerCard {
            SectionHeading(stringResource(R.string.channel_management), stringResource(R.string.channel_management_hint))
            if (data.activeChannels.isEmpty()) BookText(stringResource(R.string.channel_none), size = 14.sp)
            data.activeChannels.forEach { channel ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrivateText(channel.name, hidden, Modifier.weight(1f))
                    Button(onClick = { onRename(channel) }, text = stringResource(R.string.edit), style = ButtonStyle.OutlinedButton, enabled = !hidden && !busy)
                    Button(onClick = { onDelete(channel) }, text = stringResource(R.string.delete), style = ButtonStyle.OutlinedButton, enabled = !hidden && !busy)
                }
            }
            Button(onClick = onAddChannel, text = stringResource(R.string.add_channel), style = ButtonStyle.OutlinedButton, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        } }
        item { LedgerCard {
            BookText(stringResource(R.string.default_privacy_title), weight = FontWeight.Medium)
            BookText(stringResource(R.string.default_privacy_hint), size = 14.sp, color = palette.secondary)
            LedgerDivider()
            BookText(stringResource(R.string.encrypted_local_title), weight = FontWeight.Medium)
            BookText(stringResource(R.string.encrypted_local_hint), size = 14.sp, color = palette.secondary)
        } }
        item {
            FileTransferCard(data, hidden, transferEnabled, busy, transferProgress, onExportJson, onExportCsv, onImport)
        }
    }
}

@Composable
fun ChannelEditor(draft: ChannelDraft, vm: BookViewModel, busy: Boolean, message: String?) {
    EditorDialog(stringResource(if (draft.id == null) R.string.add_channel else R.string.rename_channel), busy, vm::closeChannelDraft,
        stringResource(R.string.save_channel), vm::saveChannel, message) {
        TextField(draft.name, { vm.updateChannelName(it.take(40)) }, Modifier.fillMaxWidth(), label = stringResource(R.string.channel_name),
            hintText = stringResource(R.string.channel_name_hint))
    }
}
