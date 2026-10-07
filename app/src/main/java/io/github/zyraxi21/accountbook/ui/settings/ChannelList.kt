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
 * 渠道列表：长按 ☰ 图标或渠道名称即可上下拖动排序。
 *
 * 三个改动时不要走回头路的点：
 *
 * 1. 每一行必须包在 `key(channel.id)` 里。`Column` 默认按位置匹配子项，重排后同一位置会被
 *    复用给另一个渠道，`pointerInput(channel.id)` 的 key 因此变化，正在执行的手势协程会被
 *    静默取消——`onDragCancel` 也不会回调，拖动状态就永久残留（表现为一行悬在半空）。
 *    `pointerInput` 的 key 只能写 `channel.id`，写进任何会随重排变化的顺序都会重现这个问题。
 * 2. 手势协程外面套 `try / finally`：协程取消同样不会回调 `onDragEnd`/`onDragCancel`，
 *    只有 `finally` 能保证拖动状态一定被清除。
 * 3. 不要为了"防止拖动时页面滚动"去关父级 `userScrollEnabled`；一旦拖动状态意外残留，
 *    那会把整个设置页锁死。
 *
 * 列表本身不用 `LazyColumn`：它已经在 `LedgerCard` 的 `Column` 里，同方向嵌套滚动会抛异常。
 *
 * 已知限制：拖到列表边缘不会自动滚动，一屏放不下时请先滚动再拖。
 * 排序只在松手时落库一次；写入失败不会立刻回滚本地顺序，下一次渠道数据变化时会自动回到权威顺序。
 */
@Composable
fun ChannelList(channels: List<Channel>, hidden: Boolean, busy: Boolean,
                onReorder: (List<String>) -> Unit,
                onRename: (Channel) -> Unit, onDelete: (Channel) -> Unit) {
    // 拖动期间的本地顺序；上游数据变化（落库成功、新增、删除、改名）后自动回到权威顺序。
    val orderState = remember { mutableStateOf(channels) }
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
        val current = orderState.value
        if (from !in current.indices || to !in current.indices || from == to) return
        orderState.value = current.toMutableList().apply { add(to, removeAt(from)) }
        onReorder(orderState.value.map { it.id })
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
                        customActions = listOf(
                            CustomAccessibilityAction(moveUpLabel) { move(index, index - 1); true },
                            CustomAccessibilityAction(moveDownLabel) { move(index, index + 1); true },
                        )
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
                                .pointerInput(channel.id) {
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
                                                onReorder(orderState.value.map { it.id })
                                            },
                                            onDragCancel = {
                                                // 顺序已经动过，回滚后必须把回滚结果也写回仓储。
                                                orderState.value = channels
                                                onReorder(channels.map { it.id })
                                            },
                                        )
                                    } finally {
                                        draggingId = null
                                        dragDelta = 0f
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier.size(48.dp)
                                    .testTag("channel_handle_${channel.id}")
                                    .semantics { contentDescription = dragHandleLabel },
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painterResource(R.drawable.ic_drag_handle), contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(LocalBookPalette.current.secondary),
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
