package io.github.zyraxi21.accountbook.ui.income

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.SmsReviewState
import io.github.zyraxi21.accountbook.ui.components.BookText
import io.github.zyraxi21.accountbook.ui.components.EditorDialog
import io.github.zyraxi21.accountbook.ui.components.LedgerDivider
import io.github.zyraxi21.accountbook.ui.components.MoneyText
import io.github.zyraxi21.accountbook.ui.components.formatDateTime
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import io.github.zyraxi21.accountbook.ui.theme.useDarkSystemBarIcons

/** 待确认清单可逐笔选择；隐私隐藏时不创建包含短信信息的节点。 */
@Composable
fun SmsReviewDialog(review: SmsReviewState, hidden: Boolean, busy: Boolean, vm: BookViewModel, onHistory: () -> Unit = {}) {
    // 本次核对时临时排除的卡：只影响这批记录，不写偏好，点错了再点一下即可恢复。
    var excludedCards by remember { mutableStateOf(emptySet<String>()) }
    EditorDialog(stringResource(R.string.sms_review_title, review.incomes.size), busy, vm::dismissSmsReview,
        saveLabel = when {
            hidden -> stringResource(R.string.sms_review_reveal)
            // 一个都不勾时，主按钮相当于把整批转为忽略。
            review.selected.isEmpty() -> stringResource(R.string.sms_review_ignore_all)
            else -> stringResource(R.string.sms_review_import, review.selected.size)
        },
        onSave = { if (hidden) vm.togglePrivacy() else vm.resolveSmsReview(excludedCards = excludedCards) },
        saveEnabled = hidden || review.incomes.isNotEmpty(), cancelLabel = stringResource(R.string.sms_review_later)) {
        if (hidden) {
            BookText(stringResource(R.string.sms_review_hidden_hint), Modifier.testTag("sms_review_hidden"))
        } else {
            BookText(stringResource(R.string.sms_review_hint), size = 14.sp, color = LocalBookPalette.current.secondary)
            val cardTails = remember(review.incomes) { review.incomes.mapNotNull { it.cardTail }.distinct() }
            if (cardTails.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cardTails.forEach { tail ->
                        val excluded = tail in excludedCards
                        Button(onClick = {
                            excludedCards = if (excluded) excludedCards - tail else excludedCards + tail
                            // 排除时顺手取消该卡已勾选的记录，入账数量才不会虚高。
                            if (!excluded) review.incomes
                                .filter { it.cardTail == tail && it.fingerprint in review.selected }
                                .forEach { vm.selectSmsIncome(it.fingerprint, false) }
                        }, text = stringResource(R.string.sms_card_exclude, tail),
                            // 已排除的卡用实心按钮区分，文案保持一致，再点一下即恢复。
                            style = if (excluded) ButtonStyle.Button else ButtonStyle.OutlinedButton, enabled = !busy,
                            // Fluent 按钮的文字使用 weight，滚动容器里要先按内容测量宽度，否则文字量不到空间。
                            modifier = Modifier.width(IntrinsicSize.Max).heightIn(min = 48.dp)
                                .testTag("sms_review_exclude_$tail"))
                    }
                }
            }
            val visible = remember(review.incomes, excludedCards) {
                review.incomes.filter { it.cardTail?.let { tail -> tail in excludedCards } != true }
            }
            val visibleSelected = visible.count { it.fingerprint in review.selected }
            val selectAll = when {
                visible.isEmpty() || visibleSelected == 0 -> ToggleableState.Off
                visibleSelected == visible.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            SmsSelectionRow(selectAll, !busy, { selected -> visible.forEach { vm.selectSmsIncome(it.fingerprint, selected) } }) {
                BookText(stringResource(R.string.sms_review_select_all))
            }
            if (visible.isEmpty()) {
                BookText(stringResource(R.string.sms_review_all_excluded), size = 13.sp,
                    color = LocalBookPalette.current.secondary)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp).testTag("sms_review_list")) {
                items(visible, key = { it.fingerprint }) { income ->
                    LedgerDivider()
                    val state = if (income.fingerprint in review.selected) ToggleableState.On else ToggleableState.Off
                    SmsSelectionRow(state, !busy,
                        { vm.selectSmsIncome(income.fingerprint, it) }, Modifier.testTag("sms_review_${income.fingerprint}")) {
                        BookText(income.title)
                        MoneyText(income.amount, hidden = false)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BookText(formatDateTime(income.receivedAt), size = 13.sp, color = LocalBookPalette.current.secondary)
                            income.cardTail?.let { tail ->
                                BookText(stringResource(R.string.sms_card_label, tail), size = 13.sp,
                                    color = LocalBookPalette.current.secondary)
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = onHistory, text = stringResource(R.string.sms_history_view_all), style = ButtonStyle.TextButton,
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
    }
}

@Composable
private fun SmsSelectionRow(state: ToggleableState, enabled: Boolean, onChange: (Boolean) -> Unit,
                            modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalBookPalette.current
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .triStateToggleable(state = state, enabled = enabled, role = Role.Checkbox,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = palette.brand),
            // 半选状态点击按“全选”处理，已全选时再点取消。
            onClick = { onChange(state != ToggleableState.On) }),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            SelectionCheckBox(state, enabled)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/** 三态复选框：部分选中时画一条横线，尺寸与 Fluent 复选框一致。 */
@Composable
private fun SelectionCheckBox(state: ToggleableState, enabled: Boolean) {
    val palette = LocalBookPalette.current
    val active = state != ToggleableState.Off
    val shape = RoundedCornerShape(4.dp)
    // 选中底色是主题色，勾线用与其对比度更高的黑白之一。
    val onFill = if (useDarkSystemBarIcons(palette.primary)) Color.Black else Color.White
    Box(
        Modifier.size(20.dp).clip(shape)
            .background(if (active) palette.primary else Color.Transparent, shape)
            .border(1.5.dp, if (active) palette.primary else palette.stroke.copy(alpha = if (enabled) 1f else 0.38f), shape),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ToggleableState.On -> Icon(Icons.Filled.Check, contentDescription = null, tint = onFill,
                modifier = Modifier.size(14.dp))
            ToggleableState.Indeterminate -> Box(Modifier.width(10.dp).height(2.dp).background(onFill))
            ToggleableState.Off -> Unit
        }
    }
}
