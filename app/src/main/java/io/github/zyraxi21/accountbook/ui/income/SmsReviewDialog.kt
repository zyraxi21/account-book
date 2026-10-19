package io.github.zyraxi21.accountbook.ui.income

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.CheckBox
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.SmsReviewState
import io.github.zyraxi21.accountbook.ui.components.BookText
import io.github.zyraxi21.accountbook.ui.components.EditorDialog
import io.github.zyraxi21.accountbook.ui.components.LedgerDivider
import io.github.zyraxi21.accountbook.ui.components.MoneyText
import io.github.zyraxi21.accountbook.ui.components.formatDateTime
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

/** 待确认清单可逐笔选择；隐私隐藏时不创建包含短信信息的节点。 */
@Composable
fun SmsReviewDialog(review: SmsReviewState, hidden: Boolean, busy: Boolean, vm: BookViewModel, onHistory: () -> Unit = {}) {
    EditorDialog(stringResource(R.string.sms_review_title, review.incomes.size), busy, vm::dismissSmsReview,
        saveLabel = if (hidden) stringResource(R.string.sms_review_reveal) else stringResource(R.string.sms_review_import, review.selected.size),
        onSave = { if (hidden) vm.togglePrivacy() else vm.resolveSmsReview(import = true) },
        saveEnabled = hidden || review.selected.isNotEmpty(), cancelLabel = stringResource(R.string.sms_review_later)) {
        if (hidden) {
            BookText(stringResource(R.string.sms_review_hidden_hint), Modifier.testTag("sms_review_hidden"))
        } else {
            BookText(stringResource(R.string.sms_review_hint), size = 14.sp, color = LocalBookPalette.current.secondary)
            SmsSelectionRow(review.selected.size == review.incomes.size, !busy, vm::selectAllSmsIncomes) {
                BookText(stringResource(R.string.sms_review_select_all))
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("sms_review_list")) {
                items(review.incomes, key = { it.fingerprint }) { income ->
                    LedgerDivider()
                    SmsSelectionRow(income.fingerprint in review.selected, !busy,
                        { vm.selectSmsIncome(income.fingerprint, it) }, Modifier.testTag("sms_review_${income.fingerprint}")) {
                        BookText(income.title)
                        MoneyText(income.amount, hidden = false)
                        BookText(formatDateTime(income.receivedAt), size = 13.sp, color = LocalBookPalette.current.secondary)
                    }
                }
            }
            Button(onClick = { vm.resolveSmsReview(import = false) }, text = stringResource(R.string.sms_review_ignore, review.selected.size),
                style = ButtonStyle.OutlinedButton, enabled = !busy && review.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        }
        Button(onClick = onHistory, text = stringResource(R.string.sms_history_view_all), style = ButtonStyle.TextButton,
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
    }
}

@Composable
private fun SmsSelectionRow(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit,
                            modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalBookPalette.current
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Checkbox,
            interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = palette.brand), onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            CheckBox(checked = checked, enabled = enabled, onCheckedChanged = onChange)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}
