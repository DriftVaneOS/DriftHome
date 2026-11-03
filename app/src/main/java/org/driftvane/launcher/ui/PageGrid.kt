package org.driftvane.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.driftvane.launcher.LauncherModel

/**
 * One home page. Icons sit on a fixed grid; in edit mode they wiggle and can be dragged
 * to a new spot, and the others slide out of the way with springs.
 */
@Composable
fun PageGrid(
    model: LauncherModel,
    pageIndex: Int,
    items: List<String>,
    rows: Int,
    editMode: Boolean,
    onEnterEdit: () -> Unit,
    onDragging: (Boolean) -> Unit,
    onLaunch: (String, Rect) -> Unit,
    onMenu: (String) -> Unit,
) {
    val cols = model.columns
    val cellHeightDp = if (model.showLabels) 104.dp else 86.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(cellHeightDp * rows)) {
        val density = LocalDensity.current
        val cellW = constraints.maxWidth / cols
        val cellH = with(density) { cellHeightDp.roundToPx() }
        val cellWdp = with(density) { cellW.toDp() }
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()

        var order by remember(items) { mutableStateOf(items) }
        var dragKey by remember { mutableStateOf<String?>(null) }
        var dragPos by remember { mutableStateOf(Offset.Zero) }

        fun slot(i: Int) = IntOffset((i % cols) * cellW, (i / cols) * cellH)

        val wiggle = rememberInfiniteTransition(label = "wiggle")
        val angle by wiggle.animateFloat(
            -1.6f, 1.6f,
            infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse),
            label = "angle",
        )

        order.forEachIndexed { index, appKey ->
            val app = model.apps[appKey] ?: return@forEachIndexed
            key(appKey) {
                val target = slot(index)
                val pos = remember { Animatable(target, IntOffset.VectorConverter) }
                val dragging = dragKey == appKey
                LaunchedEffect(target, dragging) {
                    if (!dragging) pos.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 380f))
                }
                val lift by animateFloatAsState(if (dragging) 1.15f else 1f, spring(0.6f), label = "lift")
                val phase = if (appKey.hashCode() % 2 == 0) 1f else -1f
                val shown = if (dragging) dragPos.round() else pos.value

                Box(
                    Modifier
                        .offset { shown }
                        .size(cellWdp, cellHeightDp)
                        .zIndex(if (dragging) 2f else 0f)
                        .graphicsLayer {
                            rotationZ = if (editMode && !dragging) angle * phase else 0f
                            scaleX = lift
                            scaleY = lift
                        }
                        .then(
                            if (editMode) Modifier.pointerInput(appKey, order) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragKey = appKey
                                        dragPos = Offset(pos.value.x.toFloat(), pos.value.y.toFloat())
                                        onDragging(true)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragPos += delta
                                        val c = dragPos + Offset(cellW / 2f, cellH / 2f)
                                        val col = (c.x / cellW).toInt().coerceIn(0, cols - 1)
                                        val row = (c.y / cellH).toInt().coerceIn(0, rows - 1)
                                        val t = (row * cols + col).coerceIn(0, order.lastIndex)
                                        val cur = order.indexOf(appKey)
                                        if (cur >= 0 && t != cur) {
                                            order = order.toMutableList().apply { removeAt(cur); add(t, appKey) }
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = {
                                        val from = dragPos.round()
                                        scope.launch { pos.snapTo(from) }
                                        dragKey = null
                                        onDragging(false)
                                        model.setPage(pageIndex, order)
                                    },
                                    onDragCancel = {
                                        dragKey = null
                                        onDragging(false)
                                        model.setPage(pageIndex, order)
                                    },
                                )
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        app = app,
                        iconSize = 60f * model.iconScale,
                        showLabel = model.showLabels,
                        enabled = !editMode,
                        onClick = { r -> onLaunch(appKey, r) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMenu(appKey)
                        },
                    )
                    if (editMode) {
                        RemoveBadge(Modifier.align(Alignment.TopStart).offset(10.dp, 4.dp)) {
                            model.removeFromHome(appKey)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoveBadge(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(24.dp)
            .background(Color(0xE6262330), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("–", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W900)
    }
}
