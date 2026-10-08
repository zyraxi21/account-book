package io.github.zyraxi21.accountbook.ui.assets

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.Money
import io.github.zyraxi21.accountbook.ui.AssetDraft
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import java.time.YearMonth
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun AssetsScreen(data: BookData, month: YearMonth, hidden: Boolean, busy: Boolean,
                 onRegister: (YearMonth) -> Unit, onDelete: (YearMonth) -> Unit,
                 currentMonth: YearMonth = YearMonth.now(io.github.zyraxi21.accountbook.domain.BOOK_ZONE),
                 onMonthSelected: (YearMonth) -> Unit) {
    val pager = rememberPagerState(initialPage = pageOf(month, currentMonth)) { CURRENT_MONTH_PAGE + 1 }
    val selected = rememberUpdatedState(month)
    val selectMonth = rememberUpdatedState(onMonthSelected)
    var request by remember { mutableStateOf<MonthPageRequest?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    var reportedPage by remember { mutableIntStateOf(pageOf(month, currentMonth)) }
    val displayedMonth = monthAtPage(pager.currentPage, currentMonth)

    fun navigateTo(page: Int) {
        request = MonthPageRequest(page.coerceIn(0, CURRENT_MONTH_PAGE))
    }

    // 保存资产或其他外部操作选中月份时，同样使用动画定位。自身停靠回调不触发第二次翻页。
    LaunchedEffect(month, currentMonth) {
        val target = pageOf(month, currentMonth)
        if (target != reportedPage) navigateTo(target)
    }
    LaunchedEffect(request) {
        val active = request ?: return@LaunchedEffect
        try {
            pager.animateScrollToPage(active.page, animationSpec = tween(250, easing = FastOutSlowInEasing))
        } finally {
            if (request === active) request = null
        }
    }
    LaunchedEffect(pager, currentMonth) {
        snapshotFlow { if (!pager.isScrollInProgress && request == null) pager.settledPage else null }
            .filterNotNull().distinctUntilChanged().collect { page ->
            reportedPage = page
            val settled = monthAtPage(page, currentMonth)
            // 手势和按钮动画共用停靠结果；每页的账务操作独立绑定其月份。
            if (settled != selected.value) selectMonth.value(settled)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 静止区自行保留与滚动区的间距，列表不再自带顶部内边距，滚动时内容不会贴到月份行。
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp).testTag("month_header"),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SectionHeading(stringResource(R.string.statement_title), stringResource(R.string.statement_subtitle))
                MonthNavigation(displayedMonth,
                    onPrevious = { navigateTo((request?.page ?: pager.currentPage) - 1) },
                    onNext = { navigateTo((request?.page ?: pager.currentPage) + 1) },
                    onPickMonth = { pickerVisible = true }, nextEnabled = displayedMonth < currentMonth)
            }
            if (pickerVisible) {
                MonthPickerDialog(selected = displayedMonth, currentMonth = currentMonth,
                    onSelect = { picked -> pickerVisible = false; navigateTo(pageOf(picked, currentMonth)) },
                    onDismiss = { pickerVisible = false })
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("month_statement_pager"),
                key = { monthAtPage(it, currentMonth).toString() },
                verticalAlignment = Alignment.Top) { page ->
                val pageMonth = monthAtPage(page, currentMonth)
                MonthlyStatement(data, pageMonth, currentMonth, hidden, busy,
                    onRegister = { onRegister(pageMonth) }, onDelete = { onDelete(pageMonth) })
            }
        }
        CurrentMonthButton(displayedMonth != currentMonth, onClick = { navigateTo(CURRENT_MONTH_PAGE) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }
}

@Composable
private fun MonthlyStatement(data: BookData, month: YearMonth, currentMonth: YearMonth, hidden: Boolean, busy: Boolean,
                             onRegister: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalBookPalette.current
    val result = remember(data, month) { runCatching { data.summary(month) } }
    val snapshot = data.snapshot(month)
    LazyColumn(Modifier.fillMaxSize().testTag("statement_$month"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = if (month != currentMonth) 104.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                LedgerCard {
                    if (snapshot == null) {
                        SectionHeading(stringResource(R.string.no_assets_title), stringResource(R.string.no_assets_hint))
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BookText(stringResource(R.string.month_short_format, month.year, month.monthValue),
                                Modifier.weight(1f), size = 14.sp, color = palette.brand)
                            BookIconButton(R.drawable.ic_channel_edit, R.string.edit_assets, onRegister,
                                Modifier.testTag("register_assets_$month"), enabled = !busy)
                            BookIconButton(R.drawable.ic_channel_delete, R.string.delete, onDelete,
                                Modifier.testTag("delete_assets_$month"), enabled = !busy && !hidden, destructive = true)
                        }
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
                    LedgerDivider()
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
            if (snapshot == null) {
                item { Button(onClick = onRegister, text = stringResource(R.string.register_assets),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("register_assets_$month"), enabled = !busy) }
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
fun AssetEditor(draft: AssetDraft, vm: BookViewModel, busy: Boolean, onAdd: () -> Unit,
                onRename: (String) -> Unit, onDelete: (String) -> Unit) {
    EditorDialog(stringResource(if (draft.originalMonth == null) R.string.register_assets else R.string.edit_assets), busy,
        vm::closeAssetDraft, stringResource(R.string.save_assets), vm::saveAsset) {
        DateField(draft.registeredAt, vm::updateAssetDate, stringResource(R.string.registration_date))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BookText(stringResource(R.string.asset_channel), Modifier.weight(1f), weight = FontWeight.Medium)
            BookIconButton(R.drawable.ic_add, R.string.add_channel, onAdd,
                Modifier.testTag("add_channel_button"), enabled = !busy)
        }
        BookText(stringResource(R.string.asset_channels_hint), size = 14.sp, color = LocalBookPalette.current.secondary)
        if (draft.balances.isEmpty()) BookText(stringResource(R.string.channel_none))
        ReorderableChannelCards(draft.balances, { it.channelId }, !busy, vm::reorderAssetChannels) { balance, handle ->
            ChannelCardHeader(balance.channelId, balance.name, balance.active, !busy,
                onEdit = { onRename(balance.channelId) }, onDelete = { onDelete(balance.channelId) }, dragHandleModifier = handle)
            if (!balance.active) BookText(stringResource(R.string.channel_historical), size = 12.sp, color = LocalBookPalette.current.secondary)
            AmountField(balance.amount, { vm.updateBalance(balance.channelId, it) }, stringResource(R.string.asset_amount),
                modifier = Modifier.testTag("channel_amount_${balance.channelId}"))
        }
        LedgerDivider()
        AmountField(draft.liability, vm::updateLiability, stringResource(R.string.liability_amount))
    }
}
