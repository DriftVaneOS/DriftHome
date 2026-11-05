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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.Source
import org.driftvane.launcher.WidgetItem
import org.driftvane.launcher.isFolder
import kotlin.math.abs
import kotlin.math.roundToInt

/** Callbacks for anything you can tap, long-press or drag. */
interface ItemHandler {
    fun tap(key: String, bounds: Rect)
    fun menu(key: String, bounds: Rect)
    fun dragStart(key: String, source: Source, at: Offset)
    fun dragMove(at: Offset)
    fun dragEnd()
}

/** Bumped every time you come back home, so icons can cascade in. */
val LocalResumeTick = compositionLocalOf { 0 }

fun cellHeight(model: LauncherModel): Dp = if (model.showLabels) 104.dp else 86.dp

/** Wiggle shared by everything in edit mode. */
@Composable
fun wiggleAngle(): Float {
    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val a by wiggle.animateFloat(-1.4f, 1.4f, infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    return a
}

/** One home page: optional glance card, widgets, then the icon grid. */
@Composable
fun HomePage(
    model: LauncherModel,
    page: Int,
    editMode: Boolean,
    drag: DragState,
    handler: ItemHandler,
    widget: @Composable (WidgetItem, Modifier) -> Unit,
) {
    val cellH = cellHeight(model)
    val p = model.pages.getOrNull(page) ?: return
    Column(Modifier.fillMaxWidth()) {
        if (page == 0 && model.showGlance) {
            Box(Modifier.fillMaxWidth().height(cellH * 2).padding(horizontal = 16.dp, vertical = 6.dp)) {
                GlanceCard(model, Modifier.fillMaxSize())
                if (editMode) RemoveBadge(Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp)) {
                    model.changeGlance(false)
                }
            }
        }
        // widgets: full-width rows, half-width ones side by side
        var i = 0
        val ws = p.widgets
        while (i < ws.size) {
            val w = ws[i]
            if (w.half && i + 1 < ws.size && ws[i + 1].half) {
                val w2 = ws[i + 1]
                val rows = maxOf(w.rows, w2.rows)
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    WidgetBox(model, w, rows, editMode, cellH, Modifier.weight(1f), widget)
                    WidgetBox(model, w2, rows, editMode, cellH, Modifier.weight(1f), widget)
                }
                i += 2
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    WidgetBox(model, w, w.rows, editMode, cellH, if (w.half) Modifier.fillMaxWidth(0.5f) else Modifier.fillMaxWidth(), widget)
                }
                i++
            }
        }
        IconGrid(model, page, p.items, editMode, drag, handler)
    }
}

/**
 * A widget on the home screen. In edit mode:
 *  - drag it up/down to reorder, sideways off the edge to move it to another page
 *  - drag the corner handle to resize, tap ⇆ for half/full width, – to remove
 */
@Composable
private fun WidgetBox(
    model: LauncherModel,
    w: WidgetItem,
    rows: Int,
    editMode: Boolean,
    cellH: Dp,
    modifier: Modifier,
    widget: @Composable (WidgetItem, Modifier) -> Unit,
) {
    key("w${w.id}") {
        val density = LocalDensity.current
        val cellPx = with(density) { cellH.toPx() }
        var grow by remember { mutableFloatStateOf(0f) }
        val move = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val scope = rememberCoroutineScope()
        val angle = if (editMode) wiggleAngle() else 0f
        val heightDp = with(density) { (cellPx * rows + grow).coerceIn(cellPx * 0.9f, cellPx * 4.2f).toDp() }
        val lifted by animateFloatAsState(if (move.value != Offset.Zero) 1.04f else 1f, spring(0.6f), label = "lift")

        Box(
            modifier
                .height(heightDp)
                .padding(6.dp)
                .zIndex(if (move.value != Offset.Zero) 3f else 0f)
                .offset { IntOffset(move.value.x.roundToInt(), move.value.y.roundToInt()) }
                .graphicsLayer { rotationZ = angle * 0.35f; scaleX = lifted; scaleY = lifted }
        ) {
            widget(w, Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)))
            if (editMode) {
                // body: drag to move
                Box(
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0x22FFFFFF))
                        .pointerInput(w.id) {
                            detectDragGestures(
                                onDrag = { c, d -> c.consume(); scope.launch { move.snapTo(move.value + d) } },
                                onDragEnd = {
                                    val o = move.value
                                    val wpx = size.width.toFloat()
                                    val hpx = size.height.toFloat()
                                    when {
                                        abs(o.x) > wpx * 0.35f -> model.moveWidgetPage(w.id, if (o.x > 0) 1 else -1)
                                        o.y > hpx * 0.5f -> model.moveWidget(w.id, 1)
                                        o.y < -hpx * 0.5f -> model.moveWidget(w.id, -1)
                                    }
                                    scope.launch { move.animateTo(Offset.Zero, spring(dampingRatio = 0.7f, stiffness = 380f)) }
                                },
                                onDragCancel = { scope.launch { move.animateTo(Offset.Zero, spring(0.7f)) } },
                            )
                        }
                )
                RemoveBadge(Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp)) { model.removeWidget(w.id) }
                // half/full width
                Box(
                    Modifier.align(Alignment.TopEnd).offset(8.dp, (-8).dp).size(30.dp)
                        .shadow(6.dp, CircleShape).glassShape(CircleShape, 0.9f).clickable { model.toggleWidgetHalf(w.id) },
                    contentAlignment = Alignment.Center,
                ) { Text(if (w.half) "⇲" else "⇆", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W800) }
                // resize handle: drag up/down
                Box(
                    Modifier.align(Alignment.BottomCenter).offset(y = 10.dp).width(64.dp).height(22.dp)
                        .pointerInput(w.id) {
                            detectDragGestures(
                                onDrag = { c, d -> c.consume(); grow += d.y },
                                onDragEnd = {
                                    val newRows = ((cellPx * rows + grow) / cellPx).roundToInt().coerceIn(1, 4)
                                    grow = 0f
                                    model.resizeWidget(w.id, newRows)
                                },
                                onDragCancel = { grow = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(44.dp).height(6.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50)))
                }
            }
        }
    }
}

@Composable
private fun IconGrid(
    model: LauncherModel,
    page: Int,
    items: List<String>,
    editMode: Boolean,
    drag: DragState,
    handler: ItemHandler,
) {
    val cols = model.columns
    val rows = model.iconRows(page)
    val cellHdp = cellHeight(model)
    val tick = LocalResumeTick.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(cellHdp * rows).clipToBounds()
    ) {
        val density = LocalDensity.current
        val cellW = constraints.maxWidth.toFloat() / cols
        val cellH = with(density) { cellHdp.toPx() }
        val cellWdp = with(density) { cellW.toDp() }
        val dens = density.density

        Box(Modifier.fillMaxSize().onGloballyPositioned {
            drag.grids[page] = GridInfo(it.boundsInRoot(), cellW, cellH, cols)
        })

        val angle = if (editMode) wiggleAngle() else 0f
        val order = previewOrder(items, page, drag.key, drag.hover)
        val ontoTarget = (drag.hover as? Hover.Onto)?.takeIf { it.page == page }?.target
        val iconSize = 60f * model.iconScale * (if (cols >= 6) 0.86f else if (cols == 5) 0.94f else 1f)

        order.forEachIndexed { index, itemKey ->
            if (!isFolder(itemKey) && model.apps[itemKey] == null) return@forEachIndexed
            if (isFolder(itemKey) && model.folders[itemKey] == null) return@forEachIndexed
            key(itemKey) {
                val target = IntOffset(((index % cols) * cellW).toInt(), ((index / cols) * cellH).toInt())
                val pos = remember { Animatable(target, IntOffset.VectorConverter) }
                LaunchedEffect(target) { pos.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 420f)) }
                // cascade in when you come back home (row by row, like a wave)
                val enter = remember { Animatable(1f) }
                LaunchedEffect(tick) {
                    if (tick > 1) {
                        enter.snapTo(0f)
                        delay(((index / cols) * 28L + (index % cols) * 10L))
                        enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 300f))
                    }
                }
                var pressed by remember { mutableStateOf(false) }
                val topLeft = remember { arrayOf(Offset.Zero) }
                val bounds = remember { arrayOf(Rect.Zero) }
                val hidden = drag.key == itemKey
                val grow by animateFloatAsState(if (ontoTarget == itemKey) 1.16f else 1f, spring(0.55f), label = "g")
                val phase = if (itemKey.hashCode() % 2 == 0) 1f else -1f

                Box(
                    Modifier
                        .offset { pos.value }
                        .size(cellWdp, cellHdp)
                        .zIndex(if (hidden) -1f else 0f)
                        .onGloballyPositioned { topLeft[0] = it.positionInRoot() }
                        .graphicsLayer {
                            val e = enter.value
                            alpha = if (hidden) 0f else e.coerceIn(0f, 1f)
                            rotationZ = if (editMode) angle * phase else 0f
                            val s = grow * (0.84f + 0.16f * e)
                            scaleX = s; scaleY = s
                            translationY = (1f - e) * 26f * dens
                        }
                        .iconGestures(
                            editMode = editMode,
                            rootTopLeft = { topLeft[0] },
                            onPress = { pressed = it },
                            onTap = { handler.tap(itemKey, bounds[0]) },
                            onMenu = { handler.menu(itemKey, bounds[0]) },
                            onDragStart = { handler.dragStart(itemKey, Source.OnPage(page), it) },
                            onDragMove = { handler.dragMove(it) },
                            onDragEnd = { handler.dragEnd() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    HomeItem(model, itemKey, iconSize, model.showLabels, pressed, onBounds = { bounds[0] = it })
                    if (editMode && !hidden && !model.lockLayout) {
                        RemoveBadge(Modifier.align(Alignment.TopStart).offset(8.dp, 4.dp)) {
                            model.removeFromHome(itemKey)
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
        modifier.size(26.dp).shadow(6.dp, CircleShape)
            .glassShape(CircleShape, 0.95f)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.width(11.dp).height(2.5.dp).background(Color.White, RoundedCornerShape(50))) }
}
