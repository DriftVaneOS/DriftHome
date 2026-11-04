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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.Source
import org.driftvane.launcher.WidgetItem
import org.driftvane.launcher.isFolder

/** Callbacks for anything you can tap, long-press or drag. */
interface ItemHandler {
    fun tap(key: String, bounds: Rect)
    fun menu(key: String)
    fun dragStart(key: String, source: Source, at: Offset)
    fun dragMove(at: Offset)
    fun dragEnd()
}

fun cellHeight(model: LauncherModel): Dp = if (model.showLabels) 104.dp else 86.dp

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
        p.widgets.forEach { w ->
            key("w${w.id}") {
                Box(Modifier.fillMaxWidth().height(cellH * w.rows).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    widget(w, Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)))
                    if (editMode) {
                        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {})
                        RemoveBadge(Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp)) {
                            model.removeWidget(w.id)
                        }
                    }
                }
            }
        }
        IconGrid(model, page, p.items, editMode, drag, handler)
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
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(cellHdp * rows).clipToBounds()
    ) {
        val density = LocalDensity.current
        val cellW = constraints.maxWidth.toFloat() / cols
        val cellH = with(density) { cellHdp.toPx() }
        val cellWdp = with(density) { cellW.toDp() }

        Box(Modifier.fillMaxSize().onGloballyPositioned {
            drag.grids[page] = GridInfo(it.boundsInRoot(), cellW, cellH, cols)
        })

        val wiggle = rememberInfiniteTransition(label = "wiggle")
        val angle by wiggle.animateFloat(
            -1.6f, 1.6f, infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse), label = "a",
        )
        val order = previewOrder(items, page, drag.key, drag.hover)
        val ontoTarget = (drag.hover as? Hover.Onto)?.takeIf { it.page == page }?.target
        val iconSize = 60f * model.iconScale * (if (cols >= 6) 0.86f else if (cols == 5) 0.94f else 1f)

        order.forEachIndexed { index, itemKey ->
            if (!isFolder(itemKey) && model.apps[itemKey] == null) return@forEachIndexed
            if (isFolder(itemKey) && model.folders[itemKey] == null) return@forEachIndexed
            key(itemKey) {
                val target = IntOffset(((index % cols) * cellW).toInt(), ((index / cols) * cellH).toInt())
                val pos = remember { Animatable(target, IntOffset.VectorConverter) }
                LaunchedEffect(target) { pos.animateTo(target, spring(dampingRatio = 0.75f, stiffness = 420f)) }
                var pressed by remember { mutableStateOf(false) }
                val topLeft = remember { arrayOf(Offset.Zero) }
                val bounds = remember { arrayOf(Rect.Zero) }
                val hidden = drag.key == itemKey
                val grow by animateFloatAsState(if (ontoTarget == itemKey) 1.14f else 1f, spring(0.6f), label = "g")
                val phase = if (itemKey.hashCode() % 2 == 0) 1f else -1f

                Box(
                    Modifier
                        .offset { pos.value }
                        .size(cellWdp, cellHdp)
                        .zIndex(if (hidden) -1f else 0f)
                        .onGloballyPositioned { topLeft[0] = it.positionInRoot() }
                        .graphicsLayer {
                            alpha = if (hidden) 0f else 1f
                            rotationZ = if (editMode) angle * phase else 0f
                            scaleX = grow; scaleY = grow
                        }
                        .iconGestures(
                            editMode = editMode,
                            rootTopLeft = { topLeft[0] },
                            onPress = { pressed = it },
                            onTap = { handler.tap(itemKey, bounds[0]) },
                            onMenu = { handler.menu(itemKey) },
                            onDragStart = { handler.dragStart(itemKey, Source.OnPage(page), it) },
                            onDragMove = { handler.dragMove(it) },
                            onDragEnd = { handler.dragEnd() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    HomeItem(model, itemKey, iconSize, model.showLabels, pressed, onBounds = { bounds[0] = it })
                    if (editMode && !hidden) {
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
        modifier.size(24.dp).background(Color(0xE6262330), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("–", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W900) }
}
