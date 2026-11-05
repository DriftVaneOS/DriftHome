package org.driftvane.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class G { EDIT, INFO, TRASH, DOCK, HOME, HIDE, REMOVE, SHORTCUT, WIDGET, GEAR, WALLPAPER, CHEVRON, BACK, SHAPE, PALETTE, GRID, HAND, LIBRARY, LOCK, SPARK }

/** Small line icons drawn in code, so menus look crisp at any size. */
@Composable
fun Glyph(kind: G, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.09f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Float): Path = Path().apply {
            moveTo(pts[0] * w, pts[1] * w)
            var i = 2
            while (i < pts.size) { lineTo(pts[i] * w, pts[i + 1] * w); i += 2 }
        }
        when (kind) {
            G.EDIT -> {
                drawPath(p(0.22f, 0.78f, 0.25f, 0.62f, 0.66f, 0.21f, 0.79f, 0.34f, 0.38f, 0.75f, 0.22f, 0.78f), color, style = st)
                drawLine(color, Offset(0.58f * w, 0.29f * w), Offset(0.71f * w, 0.42f * w), sw, StrokeCap.Round)
            }
            G.INFO -> {
                drawCircle(color, w * 0.38f, style = st)
                drawLine(color, Offset(w / 2, 0.46f * w), Offset(w / 2, 0.70f * w), sw, StrokeCap.Round)
                drawCircle(color, sw * 0.75f, Offset(w / 2, 0.31f * w))
            }
            G.TRASH -> {
                drawPath(p(0.28f, 0.32f, 0.33f, 0.82f, 0.67f, 0.82f, 0.72f, 0.32f), color, style = st)
                drawLine(color, Offset(0.2f * w, 0.3f * w), Offset(0.8f * w, 0.3f * w), sw, StrokeCap.Round)
                drawPath(p(0.4f, 0.3f, 0.43f, 0.18f, 0.57f, 0.18f, 0.6f, 0.3f), color, style = st)
            }
            G.DOCK -> {
                drawRoundRect(color, Offset(0.16f * w, 0.18f * w), Size(0.68f * w, 0.64f * w), CornerRadius(0.12f * w), style = st)
                drawLine(color, Offset(0.3f * w, 0.66f * w), Offset(0.7f * w, 0.66f * w), sw, StrokeCap.Round)
            }
            G.HOME -> drawPath(p(0.18f, 0.48f, 0.5f, 0.2f, 0.82f, 0.48f, 0.82f, 0.82f, 0.18f, 0.82f, 0.18f, 0.48f), color, style = st)
            G.HIDE -> {
                drawPath(Path().apply {
                    moveTo(0.12f * w, 0.5f * w)
                    quadraticTo(0.5f * w, 0.12f * w, 0.88f * w, 0.5f * w)
                    quadraticTo(0.5f * w, 0.88f * w, 0.12f * w, 0.5f * w)
                }, color, style = st)
                drawCircle(color, w * 0.11f, style = st)
                drawLine(color, Offset(0.2f * w, 0.2f * w), Offset(0.8f * w, 0.8f * w), sw, StrokeCap.Round)
            }
            G.REMOVE -> {
                drawCircle(color, w * 0.38f, style = st)
                drawLine(color, Offset(0.32f * w, w / 2), Offset(0.68f * w, w / 2), sw, StrokeCap.Round)
            }
            G.SHORTCUT -> {
                drawLine(color, Offset(0.28f * w, 0.72f * w), Offset(0.72f * w, 0.28f * w), sw, StrokeCap.Round)
                drawPath(p(0.4f, 0.28f, 0.72f, 0.28f, 0.72f, 0.6f), color, style = st)
            }
            G.WIDGET, G.GRID -> {
                val s = 0.3f * w
                listOf(0.17f to 0.17f, 0.53f to 0.17f, 0.17f to 0.53f, 0.53f to 0.53f).forEachIndexed { i, (x, y) ->
                    val r = CornerRadius(0.08f * w)
                    if (kind == G.WIDGET && i == 0) drawRoundRect(color, Offset(x * w, y * w), Size(s, s), r)
                    else drawRoundRect(color, Offset(x * w, y * w), Size(s, s), r, style = st)
                }
            }
            G.GEAR -> {
                drawCircle(color, w * 0.14f, style = st)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = Offset(w / 2, w / 2)
                    drawLine(color, c + Offset((0.27f * w * kotlin.math.cos(a)).toFloat(), (0.27f * w * kotlin.math.sin(a)).toFloat()),
                        c + Offset((0.38f * w * kotlin.math.cos(a)).toFloat(), (0.38f * w * kotlin.math.sin(a)).toFloat()), sw * 1.2f, StrokeCap.Round)
                }
            }
            G.WALLPAPER -> {
                drawRoundRect(color, Offset(0.16f * w, 0.2f * w), Size(0.68f * w, 0.6f * w), CornerRadius(0.1f * w), style = st)
                drawPath(p(0.24f, 0.72f, 0.42f, 0.5f, 0.54f, 0.62f, 0.64f, 0.52f, 0.78f, 0.72f), color, style = st)
            }
            G.CHEVRON -> drawPath(p(0.4f, 0.25f, 0.65f, 0.5f, 0.4f, 0.75f), color, style = st)
            G.BACK -> drawPath(p(0.6f, 0.25f, 0.35f, 0.5f, 0.6f, 0.75f), color, style = st)
            G.SHAPE -> {
                drawRoundRect(color, Offset(0.18f * w, 0.18f * w), Size(0.4f * w, 0.4f * w), CornerRadius(0.14f * w), style = st)
                drawCircle(color, 0.2f * w, Offset(0.62f * w, 0.62f * w), style = st)
            }
            G.PALETTE -> {
                drawCircle(color, w * 0.38f, style = st)
                drawCircle(color, sw, Offset(0.36f * w, 0.36f * w))
                drawCircle(color, sw, Offset(0.6f * w, 0.32f * w))
                drawCircle(color, sw, Offset(0.68f * w, 0.54f * w))
            }
            G.HAND -> {
                drawPath(p(0.5f, 0.2f, 0.5f, 0.6f), color, style = st)
                drawPath(Path().apply {
                    moveTo(0.3f * w, 0.5f * w)
                    quadraticTo(0.3f * w, 0.85f * w, 0.55f * w, 0.85f * w)
                    quadraticTo(0.75f * w, 0.85f * w, 0.75f * w, 0.55f * w)
                    lineTo(0.75f * w, 0.42f * w)
                }, color, style = st)
            }
            G.LIBRARY -> {
                drawRoundRect(color, Offset(0.16f * w, 0.16f * w), Size(0.3f * w, 0.68f * w), CornerRadius(0.08f * w), style = st)
                drawRoundRect(color, Offset(0.54f * w, 0.16f * w), Size(0.3f * w, 0.3f * w), CornerRadius(0.08f * w), style = st)
                drawRoundRect(color, Offset(0.54f * w, 0.54f * w), Size(0.3f * w, 0.3f * w), CornerRadius(0.08f * w), style = st)
            }
            G.LOCK -> {
                drawRoundRect(color, Offset(0.22f * w, 0.44f * w), Size(0.56f * w, 0.4f * w), CornerRadius(0.08f * w), style = st)
                drawPath(Path().apply {
                    moveTo(0.34f * w, 0.44f * w); lineTo(0.34f * w, 0.32f * w)
                    quadraticTo(0.34f * w, 0.16f * w, 0.5f * w, 0.16f * w)
                    quadraticTo(0.66f * w, 0.16f * w, 0.66f * w, 0.32f * w); lineTo(0.66f * w, 0.44f * w)
                }, color, style = st)
            }
            G.SPARK -> {
                drawPath(p(0.5f, 0.14f, 0.58f, 0.42f, 0.86f, 0.5f, 0.58f, 0.58f, 0.5f, 0.86f, 0.42f, 0.58f, 0.14f, 0.5f, 0.42f, 0.42f, 0.5f, 0.14f), color)
            }
        }
    }
}
