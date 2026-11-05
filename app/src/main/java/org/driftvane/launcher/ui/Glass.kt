package org.driftvane.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Superellipse: 2 = circle, higher = squarer. */
class SquircleShape(private val n: Double = 4.6) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = size.width / 2f
        val b = size.height / 2f
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = a + a * (abs(c).pow(2.0 / n) * sign(c)).toFloat()
            val y = b + b * (abs(s).pow(2.0 / n) * sign(s)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val Squircle = SquircleShape()

/** Icon shapes users can pick in Home settings → Icons. */
object IconShapes {
    val all = listOf(
        "squircle" to "Squircle",
        "rounded" to "Rounded",
        "pebble" to "Pebble",
        "teardrop" to "Teardrop",
        "square" to "Square",
        "circle" to "Circle",
    )

    fun shape(id: String): Shape = when (id) {
        "rounded" -> RoundedCornerShape(26)
        "pebble" -> SquircleShape(3.2)
        "teardrop" -> RoundedCornerShape(50, 50, 50, 14)
        "square" -> RoundedCornerShape(14)
        "circle" -> CircleShape
        else -> Squircle
    }
}

/** The icon shape everything on the home screen uses; set once at the root. */
val LocalIconShape = staticCompositionLocalOf<Shape> { Squircle }

/**
 * Liquid glass: a frosted tint, a bright specular sheen from the top-left, and a rim of light
 * that fades around the edge. [alpha] is how strong the frost is (0.4 light … 0.9 strong).
 */
fun Modifier.glass(radius: Dp = 28.dp, alpha: Float = 0.72f): Modifier {
    val shape = RoundedCornerShape(radius)
    val a = alpha.coerceIn(0.2f, 1f)
    return this
        .clip(shape)
        .background(Color(0xFF15131C).copy(alpha = 0.30f + 0.38f * a))
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.16f * a), Color.White.copy(alpha = 0.04f * a))
            )
        )
        .drawWithContent {
            // specular sheen in the top-left corner
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.20f * a), Color.Transparent),
                    center = Offset(size.width * 0.18f, 0f),
                    radius = maxOf(size.width, size.height) * 0.75f,
                )
            )
            drawContent()
        }
        .border(
            1.2.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.30f),
                ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            ),
            shape,
        )
}

/** Same liquid glass on any shape (used for icon tiles and folders). */
fun Modifier.glassShape(shape: Shape, alpha: Float = 0.6f): Modifier {
    val a = alpha.coerceIn(0.2f, 1f)
    return this
        .clip(shape)
        .background(Color(0xFF15131C).copy(alpha = 0.25f + 0.35f * a))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f * a), Color.White.copy(alpha = 0.05f * a))))
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.3f))),
            shape,
        )
}
