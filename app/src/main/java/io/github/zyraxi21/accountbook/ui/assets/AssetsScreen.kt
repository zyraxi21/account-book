package io.github.zyraxi21.accountbook.ui.assets

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
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
import com.microsoft.fluentui.tokenized.controls.FloatingActionButton
import com.microsoft.fluentui.icons.ActionBarIcons
import com.microsoft.fluentui.icons.SearchBarIcons
import com.microsoft.fluentui.icons.actionbaricons.Arrowright
import com.microsoft.fluentui.icons.searchbaricons.Arrowback
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.Money
import io.github.zyraxi21.accountbook.ui.AssetDraft
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.distinctUntilChanged

// Pager 只组合可见月份；末页固定为本月，前方留足索引供持续回溯，不创建未来月份页面。
private const val CURRENT_MONTH_PAGE = Int.MAX_VALUE / 2

@Composable
fun AssetsScreen(data: BookData, month: YearMonth, hidden: Boolean, busy: Boolean, onPrevious: () -> Unit,
                 onNext: () -> Unit, onCurrent: () -> Unit, onRegister: () -> Unit, onDelete: () -> Unit,
                 currentMonth: YearMonth = YearMonth.now(io.github.zyraxi21.accountbook.domain.BOOK_ZONE),
                 onMonthSelected: (YearMonth) -> Unit) {
    fun pageFor(value: YearMonth): Int = CURRENT_MONTH_PAGE -
        ChronoUnit.MONTHS.between(value, currentMonth).coerceIn(0, CURRENT_MONTH_PAGE.toLong()).toInt()
    val pager = rememberPagerState(initialPage = pageFor(month)) { CURRENT_MONTH_PAGE + 1 }
    val selected = rememberUpdatedState(month)
    val selectMonth = rememberUpdatedState(onMonthSelected)
    LaunchedEffect(month, currentMonth) {
        val target = pageFor(month)
        if (pager.settledPage != target) {
            pager.animateScrollToPage(target, animationSpec = tween(250, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(pager, currentMonth) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            val settled = currentMonth.minusMonths((CURRENT_MONTH_PAGE - page).toLong())
            // 滑动结束后才改变编辑目标，避免拖动途中登记到相邻月份。
            if (settled != selected.value) selectMonth.value(settled)
        }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().clipToBounds().testTag("month_statement_pager"),
            key = { currentMonth.minusMonths((CURRENT_MONTH_PAGE - it).toLong()).toString() },
            verticalAlignment = Alignment.Top) { page ->
            val pageMonth = currentMonth.minusMonths((CURRENT_MONTH_PAGE - page).toLong())
            MonthlyStatement(data, pageMonth, currentMonth, hidden, busy || pager.isScrollInProgress || pageMonth != month,
                onPrevious, onNext, onRegister, onDelete)
        }
        if (month != currentMonth) {
            FloatingActionButton(onClick = onCurrent, text = stringResource(R.string.current_month),
                icon = ImageVector.vectorResource(R.drawable.ic_current_month), enabled = !pager.isScrollInProgress,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
}

@Composable
private fun MonthlyStatement(data: BookData, month: YearMonth, currentMonth: YearMonth, hidden: Boolean, busy: Boolean,
                             onPrevious: () -> Unit, onNext: () -> Unit, onRegister: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalBookPalette.current
    val result = remember(data, month) { runCatching { data.summary(month) } }
    val snapshot = data.snapshot(month)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (month != currentMonth) 104.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { SectionHeading(stringResource(R.string.statement_title), stringResource(R.string.statement_subtitle)) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPrevious, style = ButtonStyle.OutlinedButton, icon = SearchBarIcons.Arrowback,
                        contentDescription = stringResource(R.string.previous_month), enabled = !busy,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                    BookText(stringResource(R.string.month_format, month.year, month.monthValue), Modifier.weight(1f), 20.sp, FontWeight.Medium)
                    Button(onClick = onNext, style = ButtonStyle.OutlinedButton, icon = ActionBarIcons.Arrowright,
                        contentDescription = stringResource(R.string.next_month), enabled = !busy && month < currentMonth,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
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
fun AssetEditor(draft: AssetDraft, vm: BookViewModel, busy: Boolean) {
    EditorDialog(stringResource(if (draft.originalMonth == null) R.string.register_assets else R.string.edit_assets), busy,
        vm::closeAssetDraft, stringResource(R.string.save_assets), vm::saveAsset) {
        DateTimeField(draft.registeredAt, vm::updateAssetDate, stringResource(R.string.registration_time))
        BookText(stringResource(R.string.select_channels), size = 14.sp, color = LocalBookPalette.current.secondary)
        if (draft.balances.isEmpty()) BookText(stringResource(R.string.channel_none))
        draft.balances.forEach { balance ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CheckBox(onCheckedChanged = { vm.updateBalance(balance.channelId, selected = it) }, checked = balance.selected, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = balance.name })
                    }
                    BookText(balance.name, Modifier.weight(1f))
                }
                if (balance.selected) AmountField(balance.amount, { vm.updateBalance(balance.channelId, amount = it) },
                    stringResource(R.string.channel_amount_label, balance.name))
            }
        }
        AmountField(draft.liability, vm::updateLiability, stringResource(R.string.liability_amount))
    }
}
