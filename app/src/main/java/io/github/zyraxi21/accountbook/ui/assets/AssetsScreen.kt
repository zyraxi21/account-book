package io.github.zyraxi21.accountbook.ui.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.domain.Money
import io.github.zyraxi21.accountbook.ui.AssetDraft
import io.github.zyraxi21.accountbook.ui.BalanceDraft
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
                SectionHeading(stringResource(R.string.statement_title))
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
                            // 卡片左上角展示最后一次登记的完整时间，替代原先无信息量的月份数字。
                            PrivateText(formatDateTime(snapshot.registeredAt), hidden, Modifier.weight(1f), 13.sp, color = palette.secondary)
                            BookIconButton(R.drawable.ic_channel_edit, R.string.edit_assets, onRegister,
                                Modifier.testTag("register_assets_$month"), enabled = !busy && !hidden)
                            BookIconButton(R.drawable.ic_channel_delete, R.string.delete, onDelete,
                                Modifier.testTag("delete_assets_$month"), enabled = !busy && !hidden)
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
                        // 债权不落在具体渠道里，单独列在渠道合计与总资产之间。
                        SummaryRow(stringResource(R.string.receivable), snapshot.receivable, hidden)
                        SummaryRow(stringResource(R.string.total_assets), snapshot.total, hidden)
                        SummaryRow(stringResource(R.string.liability), snapshot.liability, hidden)
                        SummaryRow(stringResource(R.string.net_assets), snapshot.net, hidden, emphasis = true)
                    }
                    LedgerDivider()
                    val summary = result.getOrNull()
                    if (summary == null) BookText(stringResource(R.string.calculation_unavailable), color = palette.negative)
                    else {
                        SummaryRow(stringResource(R.string.monthly_income), summary.monthlyIncome, hidden)
                        // 口径与公式都收进气泡，正文只留结论；图标紧跟在标签文字后面。
                        SummaryRow(stringResource(R.string.asset_difference), summary.assetDifference, hidden, signed = true,
                            labelSuffix = {
                                HelpIcon(
                                    text = stringResource(if (data.settings.differenceUsesNet) R.string.difference_hint_net
                                        else R.string.difference_hint_total),
                                    description = stringResource(R.string.difference_basis_title))
                            })
                        SummaryRow(stringResource(R.string.estimated_expense), summary.estimatedExpense, hidden,
                            labelSuffix = { HelpIcon(stringResource(R.string.estimate_hint),
                                stringResource(R.string.estimate_hint_title)) })
                    }
                }
            }
            if (snapshot == null) {
                item { Button(onClick = onRegister, text = stringResource(R.string.register_assets),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("register_assets_$month"), enabled = !busy && !hidden) }
            }
    }
}

@Composable
private fun SummaryRow(label: String, money: Money?, hidden: Boolean, emphasis: Boolean = false, signed: Boolean = false,
                       /** 跟在标签文字后面，而不是跟在金额后面。 */
                       labelSuffix: (@Composable () -> Unit)? = null) {
    val palette = LocalBookPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BookText(label, size = if (emphasis) 18.sp else 14.sp,
                weight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
                color = if (emphasis) palette.brand else palette.secondary)
            labelSuffix?.invoke()
        }
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

/**
 * 紧跟标签文字后面的圆形 i 图标：点击后在图标上方弹出半透明气泡，点其他位置或按返回键才收起。
 *
 * 不用按钮和现成气泡控件：48dp 的最小点击区会把单行行高撑开，气泡也需要常驻而不是按秒自动消失。
 * 因此这里用 20dp 的裸图标随身锚点，气泡自绘深色底，不占布局位置。
 */
@Composable
private fun HelpIcon(text: String, description: String) {
    val palette = LocalBookPalette.current
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val layout = rememberTooltipLayout()
    // 气泡相对图标的位置与箭头落点由定位器写回，绘制时据此决定箭头朝向。
    val anchor = remember { mutableStateOf(TooltipAnchor(centerX = 0, above = true)) }
    Box {
        Box(
            Modifier.size(20.dp).clickable(interactionSource = interaction, indication = null, role = Role.Button,
                onClickLabel = description, onClick = { expanded = !expanded }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = description, tint = palette.secondary,
                modifier = Modifier.size(14.dp))
        }
        if (expanded) {
            Popup(popupPositionProvider = rememberTooltipPosition(layout, anchor),
                onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
                TooltipBubble(text, layout, anchor.value)
            }
        }
    }
}

/** 箭头中心相对气泡左缘的像素位置，以及气泡是否挂在图标上方。 */
private data class TooltipAnchor(val centerX: Int, val above: Boolean)

/** 气泡与锚点的几何参数：定位、绘制与内边距共用同一份数值，箭头才能始终对准图标。 */
private class TooltipLayout(val gapPx: Int, val arrowPx: Int, val arrowWidthPx: Int, val cornerPx: Int)

@Composable
private fun rememberTooltipLayout(): TooltipLayout {
    val density = LocalDensity.current
    return remember(density) { with(density) {
        // 箭头与图标之间留 6dp；圆角取 10dp，给箭头两侧留出起笔余量。
        TooltipLayout(gapPx = 6.dp.roundToPx(), arrowPx = 8.dp.roundToPx(),
            arrowWidthPx = 16.dp.roundToPx(), cornerPx = 10.dp.roundToPx())
    } }
}

/**
 * 气泡本体：圆角矩形加一枚指向图标的小三角，两者沿同一条轮廓一次填充，
 * 半透明底色不会在接缝处叠出更深的线。
 */
@Composable
private fun TooltipBubble(text: String, layout: TooltipLayout, anchor: TooltipAnchor) {
    val palette = LocalBookPalette.current
    val arrowHeight = with(LocalDensity.current) { layout.arrowPx.toDp() }
    Box(Modifier.widthIn(max = 260.dp)
        .tooltipSurface(layout, anchor, palette.tooltipSurface, palette.tooltipBorder)
        // 箭头那一侧留出等高内边距，文字不会被三角压到。
        .padding(start = 12.dp, end = 12.dp,
            top = 8.dp + if (anchor.above) 0.dp else arrowHeight,
            bottom = 8.dp + if (anchor.above) arrowHeight else 0.dp)) {
        BookText(text, size = 13.sp, color = palette.onTooltip)
    }
}

/**
 * 按 [layout] 画出「圆角矩形 + 单侧箭头」的底色与描边。
 *
 * 气泡宽高由文字撑开，只有测量之后才确定，因此形状在绘制阶段按当前尺寸构造，
 * 不交给 background(shape)：那里拿不到最终尺寸，也就摆不正箭头。
 *
 * 圆角矩形与三角先取并集再填充和描边：两条并存的子路径会在三角根部被描上一条横线，
 * 这正是之前看到的接缝。并集只剩一条外轮廓，接缝处只有自然拐角。
 */
private fun Modifier.tooltipSurface(layout: TooltipLayout, anchor: TooltipAnchor, fill: Color, stroke: Color): Modifier =
    drawBehind {
        val arrowPx = layout.arrowPx.toFloat()
        val half = layout.arrowWidthPx / 2f
        // 箭头挂在图标上方时气泡在上、三角朝下，反之三角朝上。
        val body = if (anchor.above) Rect(0f, 0f, size.width, size.height - arrowPx)
            else Rect(0f, arrowPx, size.width, size.height)
        // 箭头中心限制在圆角以内，内收到窗口边缘时三角也不会被切到外面。
        val minimum = layout.cornerPx + half
        val maximum = (size.width - layout.cornerPx - half).coerceAtLeast(minimum)
        val apex = anchor.centerX.toFloat().coerceIn(minimum, maximum)
        val corner = CornerRadius(layout.cornerPx.toFloat())
        val rounded = Path().apply { addRoundRect(RoundRect(body, corner)) }
        val triangle = Path().apply {
            // 三角底边要往气泡里塞 1px，与矩形真重叠；若往外偏，两者会隔着一条缝，
            // 描边就会沿矩形底边和三角底边各画一条，看起来是一条白线。
            val root = if (anchor.above) body.bottom - 1f else body.top + 1f
            if (anchor.above) {
                moveTo(apex - half, root)
                lineTo(apex + half, root)
                lineTo(apex, body.bottom + arrowPx)
            } else {
                moveTo(apex - half, root)
                lineTo(apex, body.top - arrowPx)
                lineTo(apex + half, root)
            }
            close()
        }
        val shape = Path().apply { op(rounded, triangle, PathOperation.Union) }
        drawPath(shape, fill)
        // 描边拐角取圆角，三角两翼与气泡底边相接处不会冒出尖刺。
        drawPath(shape, stroke, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
    }

/**
 * 气泡定位：默认贴在图标上方并让箭头对准图标中心，越过窗口边缘时整体收进留白之内。
 *
 * 只做内收、不拉长气泡，所以箭头会跟着偏离图标中心，但始终留在圆角范围内。
 */
@Composable
private fun rememberTooltipPosition(layout: TooltipLayout, anchor: MutableState<TooltipAnchor>): PopupPositionProvider {
    val marginPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    return remember(layout, marginPx) { TooltipPosition(layout, marginPx, anchor) }
}

private class TooltipPosition(
    private val layout: TooltipLayout, private val marginPx: Int,
    private val anchor: MutableState<TooltipAnchor>,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
                                   popupContentSize: IntSize): IntOffset {
        // 水平：先按图标中心居中，再整体收进窗口留边之内，不贴屏幕边缘。
        val limitX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(marginPx, limitX)
        val minimumY = marginPx
        val maximumY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(minimumY)
        val aboveY = anchorBounds.top - layout.gapPx - popupContentSize.height
        val belowY = anchorBounds.bottom + layout.gapPx
        val fitsAbove = aboveY >= minimumY
        // 上方放不下时改挂到图标下方；两边都放不下时选空间更大的一侧。
        val above = fitsAbove || (belowY > maximumY && anchorBounds.center.y > windowSize.height / 2)
        val placement = TooltipAnchor(centerX = anchorBounds.center.x - x, above = above)
        if (anchor.value != placement) anchor.value = placement
        val y = (if (above) aboveY else belowY).coerceIn(minimumY, maximumY)
        return IntOffset(x, y)
    }
}

@Composable
fun AssetEditor(draft: AssetDraft, vm: BookViewModel, busy: Boolean, onAdd: () -> Unit,
                onRename: (String) -> Unit) {
    // 删除确认做成同一个弹窗的第二层内容：另开一个 Dialog 会把编辑器顶掉，
    // 两层遮罩叠在一起也会让背景明显更暗。取消只退回编辑内容，草稿始终留在 ViewModel 里。
    var pendingDelete by remember { mutableStateOf<BalanceDraft?>(null) }
    val confirming = pendingDelete
    EditorDialog(
        title = if (confirming == null) {
            stringResource(if (draft.originalMonth == null) R.string.register_assets else R.string.edit_assets)
        } else stringResource(R.string.delete_channel_title),
        busy = busy,
        onClose = { if (confirming == null) vm.closeAssetDraft() else pendingDelete = null },
        saveLabel = stringResource(if (confirming == null) R.string.save_assets else R.string.delete),
        onSave = {
            val target = pendingDelete
            if (target == null) vm.saveAsset()
            else {
                pendingDelete = null
                vm.deleteChannel(target.channelId)
            }
        },
        destructive = confirming != null,
    ) {
        if (confirming == null) {
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
                    onEdit = { onRename(balance.channelId) },
                    onDelete = { pendingDelete = balance }, dragHandleModifier = handle)
                if (!balance.active) BookText(stringResource(R.string.channel_historical), size = 12.sp, color = LocalBookPalette.current.secondary)
                AmountField(balance.amount, { vm.updateBalance(balance.channelId, it) }, stringResource(R.string.asset_amount),
                    modifier = Modifier.testTag("channel_amount_${balance.channelId}"))
            }
            LedgerDivider()
            AmountField(draft.receivable, vm::updateReceivable, stringResource(R.string.receivable_amount))
            AmountField(draft.liability, vm::updateLiability, stringResource(R.string.liability_amount))
        } else {
            BookText(stringResource(R.string.delete_channel_hint))
        }
    }
}


