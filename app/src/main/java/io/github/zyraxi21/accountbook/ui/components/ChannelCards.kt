package io.github.zyraxi21.accountbook.ui.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.microsoft.fluentui.theme.token.controlTokens.BasicCardInfo
import com.microsoft.fluentui.theme.token.controlTokens.BasicCardTokens
import com.microsoft.fluentui.theme.token.controlTokens.CardType
import com.microsoft.fluentui.tokenized.controls.BasicCard
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import kotlin.math.abs

private object ChannelCardTokens : BasicCardTokens() {
    @Composable override fun backgroundBrush(basicCardInfo: BasicCardInfo): Brush = SolidColor(LocalBookPalette.current.surface)
    @Composable override fun borderColor(basicCardInfo: BasicCardInfo): Brush = SolidColor(LocalBookPalette.current.stroke)
    @Composable override fun cornerRadius(basicCardInfo: BasicCardInfo) = 12.dp
}

private val ChannelPlacement = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}

/** 卡片跟手拖动，相邻卡片以弹簧动画让位；取消手势不会提交排序。 */
@Composable
fun <T> ReorderableChannelCards(items: List<T>, itemId: (T) -> String, enabled: Boolean,
                               onReorder: (List<String>) -> Unit,
                               content: @Composable ColumnScope.(T, Modifier) -> Unit) {
    var ordered by remember { mutableStateOf(items) }
    val currentItems by rememberUpdatedState(items)
    val commitOrder by rememberUpdatedState(onReorder)
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartCenter by remember { mutableFloatStateOf(0f) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val dragLabel = stringResource(R.string.channel_drag_handle)
    LaunchedEffect(items) { ordered = items }

    fun move(from: Int, to: Int) {
        if (!enabled || to !in ordered.indices) return
        ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
        commitOrder(ordered.map(itemId))
    }

    LookaheadScope {
        val lookahead = this
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ordered.forEachIndexed { index, item ->
                val id = itemId(item)
                key(id) {
                    val dragging = id == draggingId
                    DisposableEffect(id) { onDispose { bounds.remove(id) } }
                    val translation by animateFloatAsState(
                        targetValue = if (dragging) dragStartCenter + dragDelta - (bounds[id]?.center?.y ?: dragStartCenter) else 0f,
                        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.85f),
                        label = "channel_drag_translation",
                    )
                    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "channel_drag_elevation")
                    val handle = Modifier
                        .testTag("channel_handle_$id")
                        .semantics {
                            contentDescription = dragLabel
                            if (!enabled) disabled()
                        }
                        .pointerInput(id, enabled) {
                            if (!enabled) return@pointerInput
                            fun cancelDrag() {
                                if (draggingId == id) {
                                    ordered = currentItems
                                    draggingId = null
                                    dragDelta = 0f
                                }
                            }
                            try {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragStartCenter = bounds[id]?.center?.y ?: 0f
                                        dragDelta = 0f
                                        draggingId = id
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += amount.y
                                        val target = dragStartCenter + dragDelta
                                        val targetId = bounds.minByOrNull { abs(it.value.center.y - target) }?.key
                                        val from = ordered.indexOfFirst { itemId(it) == id }
                                        val to = ordered.indexOfFirst { itemId(it) == targetId }
                                        if (from >= 0 && to >= 0 && from != to) {
                                            ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragDelta = 0f
                                        commitOrder(ordered.map(itemId))
                                    },
                                    onDragCancel = ::cancelDrag,
                                )
                            } finally { cancelDrag() }
                        }
                    // 命中区域使用未动画的停靠位置，避免相邻卡片让位时反复交换。
                    Box(Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
                        .onGloballyPositioned {
                            val rect = it.boundsInParent()
                            if (bounds[id] != rect) bounds[id] = rect
                        }
                        .testTag("channel_row_$id")
                        .semantics {
                            if (enabled) customActions = listOf(
                                CustomAccessibilityAction(moveUp) { move(index, index - 1); true },
                                CustomAccessibilityAction(moveDown) { move(index, index + 1); true },
                            )
                        }) {
                        val placement = if (dragging) Modifier else Modifier.animateBounds(lookahead, boundsTransform = ChannelPlacement)
                        BasicCard(placement.fillMaxWidth().graphicsLayer {
                            translationY = if (dragging) dragStartCenter + dragDelta - (bounds[id]?.center?.y ?: dragStartCenter) else translation
                        }
                            .shadow(elevation, RoundedCornerShape(12.dp))
                            .testTag("channel_card_$id"),
                            CardType.Outlined, basicCardTokens = ChannelCardTokens) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                content(item, handle)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 操作图标保留 48dp 点击区，长按仅在名称和把手区域启动。 */
@Composable
fun ChannelCardHeader(id: String, name: String, active: Boolean, enabled: Boolean,
                      onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier,
                      dragHandleModifier: Modifier = Modifier) {
    val palette = LocalBookPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp).then(dragHandleModifier), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.ic_drag_handle), contentDescription = null, modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(palette.secondary.copy(alpha = if (enabled) 1f else 0.38f)))
            BookText(name, Modifier.weight(1f), weight = FontWeight.Medium)
        }
        BookIconButton(R.drawable.ic_channel_edit, R.string.rename_channel, onEdit,
            Modifier.testTag("channel_edit_$id"), enabled = enabled && active)
        BookIconButton(R.drawable.ic_channel_delete, R.string.delete_channel, onDelete,
            Modifier.testTag("channel_delete_$id"), enabled = enabled && active, destructive = true)
    }
}
