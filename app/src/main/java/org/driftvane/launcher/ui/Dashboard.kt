package org.driftvane.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.driftvane.launcher.LauncherModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Tonal colors, all solid, all from the accent. */
object Tonal {
    fun mix(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
    )
    private val ink = Color(0xFF15121D)
    /** Big card: deep accent. */
    fun strong(accent: Color) = mix(ink, accent, 0.42f)
    /** Text on the big card. */
    fun onStrong(accent: Color) = mix(Color.White, accent, 0.12f)
    /** Light pastel tile. */
    fun light(accent: Color) = mix(accent, Color.White, 0.55f)
    /** Dark text on the pastel tile. */
    fun onLight(accent: Color) = mix(Color.Black, accent, 0.28f)
    /** Quiet dark tile / panel behind the grid. */
    fun quiet(accent: Color) = mix(ink, accent, 0.14f)
}

private fun batteryInfo(context: Context): Pair<Int, Boolean> {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    return (if (level < 0) 0 else level * 100 / scale) to charging
}

/**
 * Top of the first page: a solid tonal card with the time and date, then two tiles:
 * now playing (tap to open the player) and battery.
 */
@Composable
fun DashboardTop(model: LauncherModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = model.accent
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay((60 - Calendar.getInstance().get(Calendar.SECOND)) * 1000L)
        }
    }
    val battery by produceState(batteryInfo(context)) {
        while (true) { value = batteryInfo(context); delay(30_000) }
    }
    val playing by produceState<NowPlaying?>(null) {
        while (true) { value = nowPlaying(context); delay(1500) }
    }
    val access by produceState(hasMediaAccess(context)) {
        while (true) { value = hasMediaAccess(context); delay(3000) }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. time + date
        Column(
            Modifier.fillMaxWidth().weight(1.15f).clip(RoundedCornerShape(30.dp)).background(Tonal.strong(accent))
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(time, color = Tonal.onStrong(accent), fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.W500,
                letterSpacing = (-2).sp, maxLines = 1)
            Text(date, color = Tonal.light(accent), fontSize = 15.sp, fontWeight = FontWeight.W500, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 2. now playing
            val np = playing
            Column(
                Modifier.weight(1.35f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(Tonal.light(accent))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (np != null) openPlayer(context, np)
                        else if (!hasMediaAccess(context)) openMediaAccessSettings(context)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(if (np != null) "Now playing" else "Music", color = Tonal.onLight(accent).copy(alpha = 0.75f),
                    fontSize = 13.sp, fontWeight = FontWeight.W500)
                Column {
                    Text(
                        when {
                            np != null -> np.title
                            !access -> "Tap to show what's playing"
                            else -> "Nothing playing"
                        },
                        color = Tonal.onLight(accent), fontSize = 16.sp, fontWeight = FontWeight.W600,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (np != null && np.artist.isNotBlank()) {
                        Text(np.artist, color = Tonal.onLight(accent).copy(alpha = 0.75f), fontSize = 13.sp,
                            fontWeight = FontWeight.W500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // 3. battery
            val (level, charging) = battery
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(Tonal.quiet(accent))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(if (charging) "Charging" else "Battery", color = Tonal.light(accent), fontSize = 13.sp, fontWeight = FontWeight.W500)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$level%", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.W600, modifier = Modifier.weight(1f))
                    Box(Modifier.size(22.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = size.minDimension * 0.18f
                            val inset = sw / 2
                            val arc = Size(size.width - sw, size.height - sw)
                            drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                            drawArc(if (level <= 15 && !charging) Color(0xFFFF6B6B) else Tonal.light(accent), -90f, 360f * level / 100f,
                                false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp).width(1.dp))
    }
}

/** Open the app that's playing (its own screen if it has one). */
fun openPlayer(context: Context, np: NowPlaying) {
    runCatching {
        val pi = np.controller.sessionActivity ?: error("no session activity")
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            val opts = android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            pi.send(context, 0, null, null, null, null, opts.toBundle())
        } else {
            pi.send()
        }
    }.onFailure {
        runCatching {
            context.packageManager.getLaunchIntentForPackage(np.controller.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
