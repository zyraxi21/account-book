package io.github.zyraxi21.accountbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.microsoft.fluentui.icons.ActionBarIcons
import com.microsoft.fluentui.icons.SearchBarIcons
import com.microsoft.fluentui.icons.actionbaricons.Arrowright
import com.microsoft.fluentui.icons.searchbaricons.Arrowback
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.menu.Dialog
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// Pager 只组合可见月份；末页固定为本月，前方留足索引供持续回溯，不创建未来月份页面。
internal const val CURRENT_MONTH_PAGE = Int.MAX_VALUE / 2

/** 每次点击都创建独立请求，旧动画的取消清理不会清除后来的同目标请求。 */
internal class MonthPageRequest(val page: Int)

/** 月份 → 页索引；资产页与收入页共用同一套换算，两个页面的月份语义保持一致。 */
internal fun pageOf(value: YearMonth, currentMonth: YearMonth): Int = CURRENT_MONTH_PAGE -
    ChronoUnit.MONTHS.between(value, currentMonth).coerceIn(0, CURRENT_MONTH_PAGE.toLong()).toInt()

/** 页索引 → 月份。 */
internal fun monthAtPage(page: Int, currentMonth: YearMonth): YearMonth =
    currentMonth.minusMonths((CURRENT_MONTH_PAGE - page).toLong())

/**
 * 资产页与收入页共用的月份导航：`◀` / 可点击的月份 / `▶`。
 * 点月份直接弹出选择器，不必逐月翻；`▶` 在本月时仍可点（点了不生效），保持既有交互。
 */
@Composable
fun MonthNavigation(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, onPickMonth: () -> Unit,
                    modifier: Modifier = Modifier, titleTag: String = "month_title") {
    val pickLabel = stringResource(R.string.select_month)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPrevious, style = ButtonStyle.OutlinedButton, icon = SearchBarIcons.Arrowback,
            contentDescription = stringResource(R.string.previous_month),
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
        // 中间整段都可点，与两侧按钮等高但不加边框，保持月份读数的居中位置。
        Box(Modifier.weight(1f).heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onPickMonth)
            .semantics { contentDescription = pickLabel }
            .testTag("month_picker_button"),
            contentAlignment = Alignment.Center) {
            BookText(stringResource(R.string.month_format, month.year, month.monthValue),
                Modifier.testTag(titleTag), 20.sp, FontWeight.Medium, align = TextAlign.Center)
        }
        Button(onClick = onNext, style = ButtonStyle.OutlinedButton, icon = ActionBarIcons.Arrowright,
            contentDescription = stringResource(R.string.next_month),
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
    }
}

/**
 * 月份选择器：年份步进 + 4×3 月份网格，点一下就选中并关闭。
 *
 * 可选上限与"本月"标记都是 [currentMonth]——应用不允许登记未来月份，因此不需要单独的上限参数。
 */
@Composable
fun MonthPickerDialog(selected: YearMonth, currentMonth: YearMonth, onSelect: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    val maxWidth = bookDialogMaxWidth()
    var year by remember(selected) { mutableIntStateOf(selected.year) }
    Dialog(onDismiss = onDismiss, dialogProperties = DialogProperties(dismissOnBackPress = true,
        dismissOnClickOutside = true, securePolicy = SecureFlagPolicy.Inherit, usePlatformDefaultWidth = false)) {
        Column(Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BookText(stringResource(R.string.select_month), size = 22.sp, weight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { year-- }, style = ButtonStyle.TextButton, icon = SearchBarIcons.Arrowback,
                    contentDescription = stringResource(R.string.previous_year), modifier = Modifier.size(48.dp))
                BookText(stringResource(R.string.year_format, year), Modifier.weight(1f), 18.sp, FontWeight.Medium, align = TextAlign.Center)
                Button(onClick = { year++ }, style = ButtonStyle.TextButton, icon = ActionBarIcons.Arrowright,
                    contentDescription = stringResource(R.string.next_year), modifier = Modifier.size(48.dp),
                    enabled = year < currentMonth.year)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (column in 0 until 4) {
                            val value = YearMonth.of(year, row * 4 + column + 1)
                            MonthCell(value, value <= currentMonth, value == selected, value == currentMonth) { onSelect(value) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MonthCell(value: YearMonth, enabled: Boolean, isSelected: Boolean, isCurrent: Boolean, onClick: () -> Unit) {
    val palette = LocalBookPalette.current
    val shape = RoundedCornerShape(8.dp)
    val label = stringResource(R.string.month_format, value.year, value.monthValue)
    // 选中态用 brand 作底、background 作字色：两者明度相反，浅色与深色主题下都保持对比度。
    val background = if (isSelected) palette.brand else palette.surface
    val border = if (isCurrent || isSelected) palette.brand else palette.stroke
    Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(shape)
        .background(background, shape)
        .border(1.dp, border, shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics {
            contentDescription = label
            selected = isSelected
        },
        contentAlignment = Alignment.Center) {
        BookText(stringResource(R.string.month_number_format, value.monthValue),
            size = 16.sp, weight = if (isSelected || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> palette.secondary
                isSelected -> palette.background
                isCurrent -> palette.brand
                else -> palette.foreground
            }, align = TextAlign.Center)
    }
}
