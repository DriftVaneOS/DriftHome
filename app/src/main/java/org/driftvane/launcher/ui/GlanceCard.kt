package org.driftvane.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
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

/** Big glass card at the top of the first page: time, date, greeting and battery. */
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

    Column(modifier.fillMaxWidth().glass(30.dp, model.glassStrength).padding(20.dp)) {
        Text(greeting.uppercase(), color = accent, fontSize = 12.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp)
        Text(time, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.W800, lineHeight = 60.sp)
        Text(date, color = Color(0xFFD5DCE0), fontSize = 16.sp, fontWeight = FontWeight.W700)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(120.dp).height(8.dp).background(Color(0x33FFFFFF), RoundedCornerShape(4.dp))) {
                Box(
                    Modifier.fillMaxWidth(battery / 100f).height(8.dp)
                        .background(accent, RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("$battery%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.W800)
        }
    }
}

private fun batteryLevel(context: Context): Int {
    val i: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    return if (level < 0) 0 else level * 100 / scale
}
