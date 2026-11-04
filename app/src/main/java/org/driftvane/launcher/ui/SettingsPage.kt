package org.driftvane.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.Accents
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.LibraryStyle
import org.driftvane.launcher.SwipeDown

/** Full-screen Home settings. */
@Composable
fun SettingsPage(model: LauncherModel, onClose: () -> Unit) {
    val activity = LocalActivity.current
    val accent = model.accent
    Box(Modifier.fillMaxSize().background(Color(0xF00B0A0D)).swallowTaps()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Home settings", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.W900, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.background(accent, RoundedCornerShape(50)).clickable { onClose() }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) { Text("Done", color = Color.White, fontWeight = FontWeight.W800) }
                }
            }
            item {
                Card("Home screen") {
                    Label("Columns")
                    Segmented(listOf("4" to 4, "5" to 5, "6" to 6), model.columns, accent) { model.changeColumns(it) }
                    Gap()
                    Label("Icon size")
                    Segmented(listOf("S" to 0.86f, "M" to 1f, "L" to 1.12f, "XL" to 1.24f), model.iconScale, accent) { model.changeIconScale(it) }
                    Gap()
                    Toggle("App names", model.showLabels, accent) { model.changeLabels(it) }
                    Toggle("Clock & battery card", model.showGlance, accent) { model.changeGlance(it) }
                    Toggle("Page dots", model.showDots, accent) { model.changeDots(it) }
                    Toggle("Dock", model.showDock, accent) { model.changeDock(it) }
                }
            }
            item {
                Card("App Library") {
                    Choice("Smart categories", "Suggestions, recently added and auto-sorted groups",
                        model.libraryStyle == LibraryStyle.SMART, accent) { model.changeLibrary(LibraryStyle.SMART) }
                    Choice("Category cards", "2×2 cards for each category",
                        model.libraryStyle == LibraryStyle.CARDS, accent) { model.changeLibrary(LibraryStyle.CARDS) }
                    Choice("A–Z list", "Every app in one alphabetical list",
                        model.libraryStyle == LibraryStyle.LIST, accent) { model.changeLibrary(LibraryStyle.LIST) }
                    Choice("Swipe up drawer", "No library page; swipe up anywhere for all apps",
                        model.libraryStyle == LibraryStyle.DRAWER, accent) { model.changeLibrary(LibraryStyle.DRAWER) }
                }
            }
            item {
                Card("Gestures") {
                    Label("Swipe down on home")
                    Segmented(listOf("Search" to SwipeDown.SEARCH, "Notifications" to SwipeDown.NOTIFICATIONS, "Nothing" to SwipeDown.NOTHING),
                        model.swipeDown, accent) { model.changeSwipeDown(it) }
                }
            }
            item {
                Card("Look") {
                    Label("Accent")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Accents.forEachIndexed { i, c ->
                            val ring by animateColorAsState(if (i == model.accentIndex) Color.White else Color.Transparent, label = "r")
                            Box(Modifier.size(40.dp).border(3.dp, ring, CircleShape).padding(5.dp).background(c, CircleShape).clickable { model.changeAccent(i) })
                        }
                    }
                    Gap()
                    Label("Glass")
                    Segmented(listOf("Light" to 0.5f, "Normal" to 0.72f, "Strong" to 0.9f), model.glassStrength, accent) { model.changeGlass(it) }
                }
            }
            item {
                Card("System") {
                    Action("Wallpaper & style", Color(0x33FFFFFF)) {
                        runCatching { activity?.startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }
                    }
                    Action("Phone settings", Color(0x33FFFFFF)) {
                        runCatching { activity?.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
                    Action("Reset home screen layout", Color(0x33FF4D5E)) { model.resetLayout(); onClose() }
                }
            }
            item {
                Text("DriftHome 0.2 · made for DriftVaneOS", color = Color(0xFFA6ADB2), fontSize = 12.sp,
                    fontWeight = FontWeight.W700, modifier = Modifier.padding(start = 6.dp, bottom = 30.dp))
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(26.dp, 0.8f).padding(18.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.W900)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable private fun Gap() = Spacer(Modifier.height(16.dp))

@Composable
private fun Label(t: String) {
    Text(t.uppercase(), color = Color(0xFFA6ADB2), fontSize = 12.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun Toggle(text: String, value: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W700, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent))
    }
}

@Composable
private fun Choice(title: String, sub: String, selected: Boolean, accent: Color, onPick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.25f) else Color(0x10FFFFFF), label = "c")
    val ring by animateColorAsState(if (selected) accent else Color(0x22FFFFFF), label = "cr")
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(bg, RoundedCornerShape(16.dp))
            .border(1.dp, ring, RoundedCornerShape(16.dp)).clickable(onClick = onPick).padding(14.dp)
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W800)
        Text(sub, color = Color(0xFFA6ADB2), fontSize = 13.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, accent: Color, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0x22FFFFFF), RoundedCornerShape(16.dp)).padding(4.dp)) {
        options.forEach { (label, value) ->
            val bg by animateColorAsState(if (value == selected) accent else Color.Transparent, label = "s")
            Box(
                Modifier.weight(1f).background(bg, RoundedCornerShape(12.dp)).clickable { onPick(value) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = Color.White, fontWeight = FontWeight.W800, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun Action(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(color, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.W800, fontSize = 15.sp) }
}
