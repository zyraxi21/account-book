package io.github.zyraxi21.accountbook.ui.income

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.TextField
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.Income
import io.github.zyraxi21.accountbook.domain.IncomeSource
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.IncomeDraft
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

@Composable
fun IncomeScreen(data: BookData, hidden: Boolean, busy: Boolean, onAdd: () -> Unit, onParse: () -> Unit,
                 onEdit: (Income) -> Unit, onDelete: (Income) -> Unit) {
    val palette = LocalBookPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeading(stringResource(R.string.income_title), stringResource(R.string.income_subtitle)) }
        item { LedgerCard {
            BookText(stringResource(R.string.cumulative_income), size = 14.sp, color = palette.secondary)
            MoneyText(data.cumulativeIncome, hidden, large = true, color = palette.brand)
        } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, text = stringResource(R.string.add_income), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy)
                Button(onClick = onParse, text = stringResource(R.string.sms_parse), style = ButtonStyle.OutlinedButton,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy)
            }
        }
        if (data.incomes.isEmpty()) {
            item { LedgerCard { SectionHeading(stringResource(R.string.no_income_title), stringResource(R.string.no_income_hint)) } }
        }
        items(data.incomes, key = { it.id }) { income ->
            LedgerCard {
                PrivateText(income.title, hidden, size = 18.sp, weight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrivateText(formatDateTime(income.receivedAt), hidden, Modifier.weight(1f), 12.sp, color = palette.secondary)
                    MoneyText(income.amount, hidden)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookText(stringResource(if (income.source == IncomeSource.SMS) R.string.income_source_sms else R.string.income_source_manual),
                        Modifier.weight(1f), 12.sp, color = palette.secondary)
                    Button(onClick = { onEdit(income) }, text = stringResource(R.string.edit), style = ButtonStyle.OutlinedButton, enabled = !busy && !hidden)
                    Button(onClick = { onDelete(income) }, text = stringResource(R.string.delete), style = ButtonStyle.OutlinedButton, enabled = !busy && !hidden)
                }
            }
        }
    }
}

@Composable
fun IncomeEditor(draft: IncomeDraft, vm: BookViewModel, busy: Boolean, editingExisting: Boolean) {
    EditorDialog(stringResource(if (editingExisting) R.string.edit_income else R.string.add_income), busy,
        vm::closeIncomeDraft, stringResource(R.string.save_income), vm::saveIncome) {
        TextField(draft.title, { vm.updateIncome(title = it.take(120)) }, Modifier.fillMaxWidth(), label = stringResource(R.string.income_project),
            hintText = stringResource(R.string.income_project_hint))
        AmountField(draft.amount, { vm.updateIncome(amount = it) }, stringResource(R.string.income_amount))
        DateTimeField(draft.receivedAt, { vm.updateIncome(time = it) }, stringResource(R.string.income_date))
    }
}

@Composable
fun SmsInputEditor(text: String, vm: BookViewModel, busy: Boolean) {
    EditorDialog(stringResource(R.string.sms_parse), busy, vm::closeSmsInput, stringResource(R.string.parse_income), vm::parseSms) {
        BookText(stringResource(R.string.sms_parse_hint), size = 14.sp, color = LocalBookPalette.current.secondary)
        TextField(text, vm::updateSmsInput, Modifier.fillMaxWidth().heightIn(min = 180.dp), label = stringResource(R.string.sms_body),
            hintText = stringResource(R.string.sms_example))
    }
}
