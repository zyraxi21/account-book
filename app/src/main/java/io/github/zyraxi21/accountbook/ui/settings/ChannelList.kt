package io.github.zyraxi21.accountbook.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.Channel
import io.github.zyraxi21.accountbook.ui.components.BookText
import io.github.zyraxi21.accountbook.ui.components.LedgerDivider
import io.github.zyraxi21.accountbook.ui.components.PrivateText
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import kotlin.math.abs

/**
 * 长按图标或名称拖动排序，仅在松手时保存；取消拖动只恢复仓储返回的顺序。
 * 使用稳定渠道 ID 保留行和手势，顺序变化不重启手势协程；隐私或忙碌状态改变则终止拖动。
 * 列表跟随设置页滚动，不嵌套同方向的 LazyColumn。
 */
@Composable
fun ChannelList(channels: List<Channel>, hidden: Boolean, busy: Boolean,
                onReorder: (List<String>) -> Unit,
                onRename: (Channel) -> Unit, onDelete: (Channel) -> Unit) {
    // 拖动期间的本地顺序；上游数据变化（落库成功、新增、删除、改名）后自动回到权威顺序。
    val orderState = remember { mutableStateOf(channels) }
    val currentChannels by rememberUpdatedState(channels)
    val commitOrder by rememberUpdatedState(onReorder)
    val enabled = !hidden && !busy
    LaunchedEffect(channels) { orderState.value = channels }
    // 各行的布局位置，用于判断拖到了哪一格。记录的是不随手指平移的容器。
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartCenter by remember { mutableFloatStateOf(0f) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)
    val dragHandleLabel = stringResource(R.string.channel_drag_handle)

    // 被拖动的渠道消失时（删除或数据刷新）清除拖动状态，避免留下悬空的行。
    LaunchedEffect(orderState.value) {
        val active = draggingId
        if (active != null && orderState.value.none { it.id == active }) {
            draggingId = null
            dragDelta = 0f
        }
    }

    fun move(from: Int, to: Int) {
        if (!enabled) return
        val current = orderState.value
        if (from !in current.indices || to !in current.indices || from == to) return
        orderState.value = current.toMutableList().apply { add(to, removeAt(from)) }
        commitOrder(orderState.value.map { it.id })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 只有一个渠道时无法排序，不显示拖动提示。
        if (channels.size > 1) {
            BookText(stringResource(R.string.channel_drag_hint), size = 13.sp, color = LocalBookPalette.current.secondary)
        }
        orderState.value.forEachIndexed { index, channel ->
            key(channel.id) {
                val dragging = channel.id == draggingId
                Box(Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
                    .onGloballyPositioned { coordinates ->
                        val rect = coordinates.boundsInParent()
                        if (bounds[channel.id] != rect) bounds[channel.id] = rect
                    }
                    .semantics {
                        if (enabled) {
                            customActions = listOf(
                                CustomAccessibilityAction(moveUpLabel) { move(index, index - 1); true },
                                CustomAccessibilityAction(moveDownLabel) { move(index, index + 1); true },
                            )
                        }
                    }
                    .testTag("channel_row_${channel.id}")) {
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer {
                            // 每帧用"手指位置 − 当前基准位置"重算，重新排位后依然跟手。
                            if (dragging) {
                                val base = bounds[channel.id]?.center?.y ?: dragStartCenter
                                translationY = dragStartCenter + dragDelta - base
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 长按图标或名称都能开始拖动；不放 "编辑/删除" 按钮，避免长按按钮时误触发排序。
                        Row(
                            Modifier.weight(1f).heightIn(min = 48.dp)
                                .pointerInput(channel.id, enabled) {
                                    if (!enabled) return@pointerInput
                                    try {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragStartCenter = bounds[channel.id]?.center?.y ?: 0f
                                                dragDelta = 0f
                                                draggingId = channel.id
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragDelta += amount.y
                                                val target = dragStartCenter + dragDelta
                                                val targetId = bounds.minByOrNull { abs(it.value.center.y - target) }?.key
                                                if (targetId != null && targetId != channel.id) {
                                                    val current = orderState.value
                                                    val from = current.indexOfFirst { it.id == channel.id }
                                                    val to = current.indexOfFirst { it.id == targetId }
                                                    if (from >= 0 && to >= 0) {
                                                        orderState.value = current.toMutableList().apply { add(to, removeAt(from)) }
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggingId = null
                                                dragDelta = 0f
                                                commitOrder(orderState.value.map { it.id })
                                            },
                                            onDragCancel = {
                                                // 拖动尚未保存，取消时无需写入，避免覆盖上一次成功排序。
                                                orderState.value = currentChannels
                                                draggingId = null
                                                dragDelta = 0f
                                            },
                                        )
                                    } finally {
                                        if (draggingId == channel.id) {
                                            orderState.value = currentChannels
                                            draggingId = null
                                            dragDelta = 0f
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier.size(48.dp)
                                    .testTag("channel_handle_${channel.id}")
                                    .semantics {
                                        contentDescription = dragHandleLabel
                                        if (!enabled) disabled()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painterResource(R.drawable.ic_drag_handle), contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(LocalBookPalette.current.secondary.copy(alpha = if (enabled) 1f else 0.38f)),
                                )
                            }
                            PrivateText(channel.name, hidden, Modifier.weight(1f))
                        }
                        Button(onClick = { onRename(channel) }, text = stringResource(R.string.edit),
                            style = ButtonStyle.OutlinedButton, enabled = !hidden && !busy)
                        Button(onClick = { onDelete(channel) }, text = stringResource(R.string.delete),
                            style = ButtonStyle.OutlinedButton, enabled = !hidden && !busy)
                    }
                }
                if (index != orderState.value.lastIndex) LedgerDivider()
            }
        }
    }
}
