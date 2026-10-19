package io.github.zyraxi21.accountbook.ui.income

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.icons.SearchBarIcons
import com.microsoft.fluentui.icons.searchbaricons.Arrowback
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.SmsCard
import io.github.zyraxi21.accountbook.domain.SmsIncomeRecord
import io.github.zyraxi21.accountbook.domain.SmsIncomeStatus
import io.github.zyraxi21.accountbook.ui.SmsScanUiState
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

private enum class SmsFilter(@param:StringRes val label: Int) {
    ALL(R.string.sms_filter_all), PENDING(R.string.sms_status_pending),
    RECORDED(R.string.sms_status_imported), MATCHED(R.string.sms_status_matched),
    IGNORED(R.string.sms_status_ignored), PROCESSED(R.string.sms_status_processed);

    // 一个状态只对应一个分类，避免“已入账”里混进“账本已有”或“历史已处理”的记录。
    fun includes(status: SmsIncomeStatus) = when (this) {
        ALL -> true
        PENDING -> status == SmsIncomeStatus.PENDING
        RECORDED -> status == SmsIncomeStatus.IMPORTED
        MATCHED -> status == SmsIncomeStatus.MATCHED
        IGNORED -> status == SmsIncomeStatus.IGNORED
        PROCESSED -> status == SmsIncomeStatus.PROCESSED
    }
}

/** 处理记录独立浏览，隐私隐藏时不构建包含项目、金额和日期的节点。 */
@Composable
fun SmsHistoryScreen(records: List<SmsIncomeRecord>, hidden: Boolean, busy: Boolean,
                     enabled: Boolean, scanState: SmsScanUiState, onBack: () -> Unit,
                     onReveal: () -> Unit, onRecheck: () -> Unit, onInfo: () -> Unit,
                     onImport: (String) -> Unit, onIgnore: (String) -> Unit,
                     cards: List<SmsCard> = emptyList(), cardFilter: String? = null,
                     onSelectCard: (String?) -> Unit = {}) {
    val palette = LocalBookPalette.current
    var filter by remember { mutableStateOf(SmsFilter.ALL) }
    // 卡筛选只按尾号过滤；尾号为空的老记录在选中某张卡时不显示。
    val filtered = remember(records, filter, cardFilter) {
        records.filter { filter.includes(it.status) && (cardFilter == null || it.cardTail == cardFilter) }
    }
    Column(Modifier.fillMaxSize().testTag("sms_history")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, icon = SearchBarIcons.Arrowback, contentDescription = stringResource(R.string.back),
                style = ButtonStyle.TextButton, modifier = Modifier.size(48.dp))
            BookText(stringResource(R.string.sms_history_title), Modifier.weight(1f), 22.sp, FontWeight.SemiBold)
            Button(onClick = onRecheck, text = stringResource(R.string.sms_history_recheck),
                contentDescription = stringResource(R.string.sms_scan_recheck), style = ButtonStyle.TextButton,
                enabled = enabled && !busy && scanState != SmsScanUiState.Scanning, modifier = Modifier.heightIn(min = 48.dp))
            Button(onClick = onInfo, icon = Icons.Outlined.Info, contentDescription = stringResource(R.string.sms_permissions_title),
                style = ButtonStyle.TextButton, modifier = Modifier.size(48.dp))
        }
        val status = when (scanState) {
            SmsScanUiState.Idle -> stringResource(R.string.sms_history_local_hint)
            SmsScanUiState.Scanning -> stringResource(R.string.sms_scan_running)
            SmsScanUiState.PermissionRequired -> stringResource(R.string.sms_scan_permission_required)
            SmsScanUiState.Failed -> stringResource(R.string.sms_scan_failed)
            is SmsScanUiState.Complete -> stringResource(R.string.sms_history_scan_result, scanState.checkedCount, scanState.recognizedCount)
        }
        BookText(status, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), 13.sp, color = palette.secondary)
        if (hidden) {
            Column(Modifier.fillMaxWidth().padding(20.dp).testTag("sms_history_hidden"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BookText(stringResource(R.string.sms_history_hidden_hint))
                Button(onClick = onReveal, text = stringResource(R.string.sms_history_reveal), modifier = Modifier.heightIn(min = 48.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmsFilter.entries.forEach { option ->
                    Button(onClick = { filter = option }, text = stringResource(option.label),
                        style = if (filter == option) ButtonStyle.Button else ButtonStyle.OutlinedButton,
                        // Fluent 按钮的文字使用 weight；滚动容器中需先按内容测量宽度。
                        modifier = Modifier.width(IntrinsicSize.Max).heightIn(min = 48.dp)
                            .testTag("sms_filter_${option.name}").semantics { selected = filter == option })
                }
            }
            // 卡尾号筛选：全部或某张卡，与上面的状态分类叠加生效。
            if (cards.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options: List<String?> = listOf(null) + cards.map { it.tail }
                options.forEach { tail ->
                    Button(onClick = { onSelectCard(tail) },
                        text = tail ?: stringResource(R.string.sms_card_all),
                        style = if (cardFilter == tail) ButtonStyle.Button else ButtonStyle.OutlinedButton,
                        modifier = Modifier.width(IntrinsicSize.Max).heightIn(min = 48.dp)
                            .testTag("sms_card_${tail ?: "ALL"}").semantics { selected = cardFilter == tail })
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("sms_history_list"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (filtered.isEmpty()) item {
                    // 分类为空只说明这一类没有记录；只有一条记录都没有且从未补查过时才引导开启。
                    val guide = records.isEmpty() && scanState == SmsScanUiState.Idle
                    LedgerCard {
                        SectionHeading(stringResource(R.string.sms_history_empty),
                            if (guide) stringResource(R.string.sms_history_empty_hint) else null)
                    }
                }
                items(filtered, key = { it.fingerprint }) { record ->
                    LedgerCard(Modifier.testTag("sms_record_${record.fingerprint}")) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BookText(record.title, Modifier.weight(1f), 18.sp, FontWeight.Medium)
                            BookText(stringResource(record.status.label()), size = 13.sp,
                                color = when (record.status) {
                                    SmsIncomeStatus.PENDING -> palette.brand
                                    SmsIncomeStatus.IMPORTED -> palette.positive
                                    else -> palette.secondary
                                })
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BookText(formatDateTime(record.receivedAt), Modifier.weight(1f), 13.sp, color = palette.secondary)
                            // 未按卡筛选时也能看出这条属于哪张卡。
                            record.cardTail?.let { tail ->
                                BookText(stringResource(R.string.sms_card_label, tail), size = 13.sp, color = palette.secondary)
                            }
                            MoneyText(record.amount, hidden = false)
                        }
                        if (record.canImport) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically) {
                            if (record.status == SmsIncomeStatus.PENDING) {
                                Button(onClick = { onIgnore(record.fingerprint) }, text = stringResource(R.string.sms_history_ignore),
                                    enabled = !busy, style = ButtonStyle.TextButton, modifier = Modifier.heightIn(min = 48.dp)
                                        .testTag("sms_ignore_${record.fingerprint}"))
                            }
                            Button(onClick = { onImport(record.fingerprint) },
                                text = stringResource(if (record.status == SmsIncomeStatus.PENDING) R.string.sms_history_import else R.string.sms_history_restore),
                                enabled = !busy, style = ButtonStyle.OutlinedButton, modifier = Modifier.heightIn(min = 48.dp)
                                    .testTag("sms_import_${record.fingerprint}"))
                        }
                    }
                }
            }
        }
    }
}

@StringRes
private fun SmsIncomeStatus.label() = when (this) {
    SmsIncomeStatus.PENDING -> R.string.sms_status_pending
    SmsIncomeStatus.IMPORTED -> R.string.sms_status_imported
    SmsIncomeStatus.IGNORED -> R.string.sms_status_ignored
    SmsIncomeStatus.MATCHED -> R.string.sms_status_matched
    SmsIncomeStatus.PROCESSED -> R.string.sms_status_processed
}
