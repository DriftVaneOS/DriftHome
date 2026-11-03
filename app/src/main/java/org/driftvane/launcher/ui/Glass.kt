package org.driftvane.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * DriftVaneOS icon shape: a superellipse ("squircle") — smooth corners, not a circle.
 * [n] controls how square it is: 2 = circle, higher = squarer.
 */
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

/** AlexLMP-style glass card: dark diagonal gradient + thin light border. */
fun Modifier.glass(radius: Dp = 28.dp, alpha: Float = 0.72f): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(Color(0xFF262330).copy(alpha = alpha), Color(0xFF0B0A0D).copy(alpha = alpha)),
                start = Offset(Float.POSITIVE_INFINITY, 0f),
                end = Offset(0f, Float.POSITIVE_INFINITY),
            )
        )
        .border(1.dp, Color(0x22FFFFFF), shape)
}
