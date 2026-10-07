package io.github.zyraxi21.accountbook.ui.income

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.FloatingActionButton
import com.microsoft.fluentui.tokenized.controls.TextField
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.Income
import io.github.zyraxi21.accountbook.domain.IncomeSource
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.IncomeDraft
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import java.time.YearMonth
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * 收入明细按月呈现，避免记录多了以后清单无限变长。
 *
 * 与资产页完全一致地使用 `HorizontalPager`：左右滑动换月、月份栏提供上/下月按钮和月份选择器，
 * 并且与资产页共用同一个 `selectedMonth`，切 Tab 时月份保持一致。
 */
@Composable
fun IncomeScreen(data: BookData, month: YearMonth, currentMonth: YearMonth, hidden: Boolean, busy: Boolean,
                 onMonthSelected: (YearMonth) -> Unit,
                 onAdd: () -> Unit, onParse: () -> Unit,
                 onEdit: (Income) -> Unit, onDelete: (Income) -> Unit) {
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

    // 其他页面切换月份时同步定位；自身停靠回调不触发第二次翻页。
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
                if (settled != selected.value) selectMonth.value(settled)
            }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 静止区自行保留与滚动区的间距，列表不再自带顶部内边距。
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SectionHeading(stringResource(R.string.income_title), stringResource(R.string.income_subtitle))
                MonthNavigation(displayedMonth,
                    onPrevious = { navigateTo((request?.page ?: pager.currentPage) - 1) },
                    onNext = { navigateTo((request?.page ?: pager.currentPage) + 1) },
                    onPickMonth = { pickerVisible = true },
                    titleTag = "income_month_title")
            }
            if (pickerVisible) {
                MonthPickerDialog(selected = displayedMonth, currentMonth = currentMonth,
                    onSelect = { picked -> pickerVisible = false; navigateTo(pageOf(picked, currentMonth)) },
                    onDismiss = { pickerVisible = false })
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("income_month_pager"),
                key = { monthAtPage(it, currentMonth).toString() },
                verticalAlignment = Alignment.Top) { page ->
                MonthlyIncome(data, monthAtPage(page, currentMonth), currentMonth, hidden, busy, onAdd, onParse, onEdit, onDelete)
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
private fun MonthlyIncome(data: BookData, month: YearMonth, currentMonth: YearMonth, hidden: Boolean, busy: Boolean,
                          onAdd: () -> Unit, onParse: () -> Unit,
                          onEdit: (Income) -> Unit, onDelete: (Income) -> Unit) {
    val palette = LocalBookPalette.current
    val monthIncomes = remember(data.incomes, month) { data.incomesIn(month) }
    val monthIncome = remember(data.incomes, month) { data.incomeIn(month) }
    LazyColumn(Modifier.fillMaxSize().testTag("income_$month"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = if (month != currentMonth) 104.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { LedgerCard {
            BookText(stringResource(R.string.monthly_income), size = 14.sp, color = palette.secondary)
            MoneyText(monthIncome, hidden, large = true, color = palette.brand)
            LedgerDivider()
            BookText(stringResource(R.string.cumulative_income), size = 14.sp, color = palette.secondary)
            MoneyText(data.cumulativeIncome, hidden)
        } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, text = stringResource(R.string.add_income), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy)
                Button(onClick = onParse, text = stringResource(R.string.sms_parse), style = ButtonStyle.OutlinedButton,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy)
            }
        }
        if (monthIncomes.isEmpty()) {
            item { LedgerCard { SectionHeading(stringResource(R.string.income_month_empty_title), stringResource(R.string.no_income_hint)) } }
        }
        items(monthIncomes, key = { it.id }) { income ->
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
