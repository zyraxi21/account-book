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
import com.microsoft.fluentui.tokenized.controls.TextField
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
                    onPickMonth = { pickerVisible = true })
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
        if (displayedMonth != currentMonth) {
            FloatingActionButton(onClick = { navigateTo(CURRENT_MONTH_PAGE) }, text = stringResource(R.string.current_month),
                icon = ImageVector.vectorResource(R.drawable.ic_current_month),
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
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
            if (snapshot == null) {
                item { LedgerCard { SectionHeading(stringResource(R.string.no_assets_title), stringResource(R.string.no_assets_hint)) } }
            } else {
                item {
                    LedgerCard {
                        BookText(stringResource(R.string.month_short_format, month.year, month.monthValue), size = 14.sp, color = palette.brand)
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("register_assets_$month"), enabled = !busy)
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
                item { Button(onClick = onDelete, style = ButtonStyle.OutlinedButton, text = stringResource(R.string.delete),
                    modifier = Modifier.testTag("delete_assets_$month"), enabled = !busy && !hidden) }
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
        DateField(draft.registeredAt, vm::updateAssetDate, stringResource(R.string.registration_date))
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
        // 登记时随手新增渠道：立即创建并自动勾选，不必先去设置页。
        LedgerDivider()
        BookText(stringResource(R.string.channel_new_label), size = 14.sp, color = LocalBookPalette.current.secondary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(draft.newChannelName, vm::updateNewChannelName, Modifier.weight(1f),
                label = stringResource(R.string.channel_name), hintText = stringResource(R.string.channel_name_hint))
            Button(onClick = vm::addChannelToDraft, text = stringResource(R.string.channel_add_button),
                modifier = Modifier.heightIn(min = 48.dp), enabled = !busy && draft.newChannelName.isNotBlank())
        }
        AmountField(draft.liability, vm::updateLiability, stringResource(R.string.liability_amount))
    }
}
