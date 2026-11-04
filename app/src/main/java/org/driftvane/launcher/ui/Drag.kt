package org.driftvane.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import org.driftvane.launcher.Source

/** Grid geometry each page reports so drops can be mapped to a cell. */
data class GridInfo(val bounds: Rect, val cellW: Float, val cellH: Float, val cols: Int)

/** What a drop over the current page means. */
sealed class Hover {
    data object None : Hover()
    data class Insert(val page: Int, val index: Int) : Hover()
    data class Onto(val page: Int, val target: String) : Hover()
    data class Dock(val index: Int) : Hover()
}

@Stable
class DragState {
    var key by mutableStateOf<String?>(null)
        private set
    var source by mutableStateOf<Source?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    var hover by mutableStateOf<Hover>(Hover.None)

    val grids = mutableStateMapOf<Int, GridInfo>()
    var dockBounds by mutableStateOf(Rect.Zero)

    val active get() = key != null

    fun start(k: String, s: Source, at: Offset) { key = k; source = s; pointer = at }
    fun end() { key = null; source = null; hover = Hover.None }
}

/**
 * One gesture handler for icons:
 *  - tap -> [onTap]
 *  - long-press and let go -> [onMenu]
 *  - long-press and move (or, in edit mode, just move) -> drag: [onDragStart] / [onDragMove] / [onDragEnd]
 * Positions given to the drag callbacks are in root (screen) coordinates.
 */
fun Modifier.iconGestures(
    editMode: Boolean,
    rootTopLeft: () -> Offset,
    onPress: (Boolean) -> Unit,
    onTap: () -> Unit,
    onMenu: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(editMode) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPress(true)
        if (editMode) {
            val slop = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
            if (slop == null) {
                onPress(false)
                return@awaitEachGesture
            }
            onDragStart(rootTopLeft() + slop.position)
            drag(slop.id) { c -> onDragMove(rootTopLeft() + c.position); c.consume() }
            onPress(false)
            onDragEnd()
            return@awaitEachGesture
        }
        val lp = awaitLongPressOrCancellation(down.id)
        if (lp == null) {
            onPress(false)
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && up.changedToUpIgnoreConsumed() && !up.isConsumed) onTap()
            return@awaitEachGesture
        }
        var dragging = false
        val start = lp.position
        val slopPx = viewConfiguration.touchSlop
        drag(lp.id) { c ->
            if (!dragging && (c.position - start).getDistance() > slopPx) {
                dragging = true
                onPress(false)
                onDragStart(rootTopLeft() + c.position)
            }
            if (dragging) onDragMove(rootTopLeft() + c.position)
            c.consume()
        }
        onPress(false)
        if (dragging) onDragEnd() else onMenu()
    }
}

/**
 * The order a page shows right now: while something is dragged over it, a gap opens at the
 * hovered spot (the dragged item itself sits there, invisible). The dragged item always stays
 * in its source list so its gesture keeps running.
 */
fun previewOrder(items: List<String>, page: Int, dragKey: String?, hover: Hover): List<String> {
    if (dragKey == null) return items
    val base = items.filter { it != dragKey }
    return when {
        hover is Hover.Insert && hover.page == page ->
            base.toMutableList().apply { add(hover.index.coerceIn(0, base.size), dragKey) }
        dragKey in items -> base + dragKey
        else -> items
    }
}
