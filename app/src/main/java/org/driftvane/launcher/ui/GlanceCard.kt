package org.driftvane.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Top of the first page: a colorful clock tile and a glass date + battery tile. */
@Composable
fun GlanceCard(model: org.driftvane.launcher.LauncherModel, modifier: Modifier = Modifier) {
    val accent = model.accent
    val context = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            val c = Calendar.getInstance()
            delay((60 - c.get(Calendar.SECOND)) * 1000L)
        }
    }
    val battery by produceState(batteryLevel(context)) {
        while (true) {
            value = batteryLevel(context)
            delay(30_000)
        }
    }
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    val time = SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)

    val charging = isCharging(context, battery)
    val day = SimpleDateFormat("d", Locale.getDefault()).format(now)
    val weekday = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
    val month = SimpleDateFormat("MMMM", Locale.getDefault()).format(now)

    // Two matching glass tiles: time on the left, date + battery on the right.
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().glass(24.dp, model.glassStrength).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(greeting, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.W500)
            Column {
                Text(time, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.W300, letterSpacing = (-1.5).sp,
                    lineHeight = 50.sp, maxLines = 1)
                Text(weekday, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.W500)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().glass(24.dp, model.glassStrength).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(month.uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.W600, letterSpacing = 1.6.sp)
                Text(day, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.W300, lineHeight = 42.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = size.minDimension * 0.16f
                        val inset = sw / 2
                        val arc = Size(size.width - sw, size.height - sw)
                        drawArc(Color.White.copy(alpha = 0.16f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                        drawArc(if (battery <= 15 && !charging) Color(0xFFFF6B6B) else Color.White, -90f, 360f * battery / 100f,
                            false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(if (charging) "$battery% · Charging" else "$battery%", color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp, fontWeight = FontWeight.W500)
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)

@Suppress("UNUSED_PARAMETER")
private fun isCharging(context: Context, battery: Int): Boolean {
    val i: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
}

private fun batteryLevel(context: Context): Int {
    val i: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    return if (level < 0) 0 else level * 100 / scale
}
