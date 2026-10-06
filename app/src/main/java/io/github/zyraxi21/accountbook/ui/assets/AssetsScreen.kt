package io.github.zyraxi21.accountbook.ui.assets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.CheckBox
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.Money
import io.github.zyraxi21.accountbook.ui.AssetDraft
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import java.time.YearMonth

@Composable
fun AssetsScreen(data: BookData, month: YearMonth, hidden: Boolean, busy: Boolean, onPrevious: () -> Unit,
                 onNext: () -> Unit, onCurrent: () -> Unit, onRegister: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalBookPalette.current
    val result = remember(data, month) { runCatching { data.summary(month) } }
    val snapshot = data.snapshot(month)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SectionHeading(stringResource(R.string.statement_title), stringResource(R.string.statement_subtitle)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrevious, style = ButtonStyle.OutlinedButton, icon = ImageVector.vectorResource(R.drawable.ic_previous),
                    contentDescription = stringResource(R.string.previous_month), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                BookText(stringResource(R.string.month_format, month.year, month.monthValue), Modifier.weight(1f), 20.sp, FontWeight.Medium)
                Button(onClick = onCurrent, style = ButtonStyle.OutlinedButton, text = stringResource(R.string.current_month))
                Button(onClick = onNext, style = ButtonStyle.OutlinedButton, icon = ImageVector.vectorResource(R.drawable.ic_next),
                    contentDescription = stringResource(R.string.next_month), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
            }
        }
        if (snapshot == null) {
            item { LedgerCard { SectionHeading(stringResource(R.string.no_assets_title), stringResource(R.string.no_assets_hint)) } }
        } else {
            item {
                LedgerCard {
                    BookText(stringResource(R.string.month_short_format, month.year, month.monthValue), size = 14.sp, color = palette.brand, numeric = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BookText(stringResource(R.string.asset_channel), size = 13.sp, color = palette.secondary)
                        BookText(stringResource(R.string.asset_amount), size = 13.sp, color = palette.secondary)
                    }
                    LedgerDivider()
                    snapshot.balances.forEach { balance ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrivateText(balance.channelName, hidden, Modifier.weight(1f))
                            MoneyText(balance.amount, hidden)
                        }
                    }
                    LedgerDivider()
                    SummaryRow(stringResource(R.string.total_assets), snapshot.total, hidden)
                    SummaryRow(stringResource(R.string.liability), snapshot.liability, hidden)
                    SummaryRow(stringResource(R.string.net_assets), snapshot.net, hidden, emphasis = true)
                    PrivateText(formatDateTime(snapshot.registeredAt), hidden, size = 12.sp, color = palette.secondary)
                }
            }
        }
        item {
            Button(onClick = onRegister, text = stringResource(if (snapshot == null) R.string.register_assets else R.string.edit_assets),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy)
        }
        item {
            LedgerCard {
                val summary = result.getOrNull()
                if (summary == null) BookText(stringResource(R.string.calculation_unavailable), color = palette.negative)
                else {
                    SummaryRow(stringResource(R.string.monthly_income), summary.monthlyIncome, hidden)
                    SummaryRow(stringResource(R.string.asset_difference), summary.assetDifference, hidden, signed = true)
                    SummaryRow(stringResource(R.string.estimated_expense), summary.estimatedExpense, hidden)
                    BookText(stringResource(R.string.estimate_hint), size = 12.sp, color = palette.secondary)
                }
            }
        }
        if (snapshot != null) {
            item { Button(onClick = onDelete, style = ButtonStyle.OutlinedButton, text = stringResource(R.string.delete), enabled = !busy && !hidden) }
        }
    }
}

@Composable
private fun SummaryRow(label: String, money: Money?, hidden: Boolean, emphasis: Boolean = false, signed: Boolean = false) {
    val palette = LocalBookPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BookText(label, Modifier.weight(1f), if (emphasis) 18.sp else 14.sp,
            if (emphasis) FontWeight.SemiBold else FontWeight.Normal, if (emphasis) palette.brand else palette.secondary)
        if (money == null) BookText(stringResource(R.string.no_previous_month), size = 12.sp, color = palette.secondary)
        else MoneyText(money, hidden, large = emphasis, color = when {
            hidden -> palette.secondary
            emphasis -> palette.brand
            signed && money.fen > 0 -> palette.positive
            signed && money.fen < 0 -> palette.negative
            else -> palette.foreground
        })
    }
}

@Composable
fun AssetEditor(draft: AssetDraft, vm: BookViewModel, busy: Boolean, message: String?) {
    EditorDialog(stringResource(if (draft.originalMonth == null) R.string.register_assets else R.string.edit_assets), busy,
        vm::closeAssetDraft, stringResource(R.string.save_assets), vm::saveAsset, message) {
        DateTimeField(draft.registeredAt, vm::updateAssetDate, stringResource(R.string.registration_time))
        BookText(stringResource(R.string.select_channels), size = 14.sp, color = LocalBookPalette.current.secondary)
        if (draft.balances.isEmpty()) BookText(stringResource(R.string.channel_none))
        draft.balances.forEach { balance ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckBox(onCheckedChanged = { vm.updateBalance(balance.channelId, selected = it) }, checked = balance.selected, enabled = !busy,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = balance.name })
                    BookText(balance.name, Modifier.weight(1f))
                }
                if (balance.selected) AmountField(balance.amount, { vm.updateBalance(balance.channelId, amount = it) },
                    stringResource(R.string.channel_amount_label, balance.name))
            }
        }
        AmountField(draft.liability, vm::updateLiability, stringResource(R.string.liability_amount))
    }
}
