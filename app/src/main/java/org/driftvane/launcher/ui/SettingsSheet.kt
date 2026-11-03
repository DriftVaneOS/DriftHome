package org.driftvane.launcher.ui

import android.app.role.RoleManager
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(model: LauncherModel, onDismiss: () -> Unit) {
    val activity = LocalActivity.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF16141B)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding()) {
            Text("DriftHome", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.W900)
            Spacer(Modifier.height(18.dp))

            Label("Accent")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Accents.forEachIndexed { i, c ->
                    val ring by animateColorAsState(if (i == model.accentIndex) Color.White else Color.Transparent, label = "ring")
                    Box(Modifier.size(40.dp).border(3.dp, ring, CircleShape).padding(5.dp).background(c, CircleShape).clickable { model.setAccent(i) })
                }
            }
            Spacer(Modifier.height(20.dp))

            Label("Icon size")
            Segmented(listOf("Small" to 0.88f, "Medium" to 1f, "Large" to 1.14f), model.iconScale, model.accent) { model.changeIconScale(it) }
            Spacer(Modifier.height(20.dp))

            Label("Columns")
            Segmented(listOf("4" to 4, "5" to 5), model.columns, model.accent) { model.changeColumns(it) }
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show app names", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W700, modifier = Modifier.weight(1f))
                Switch(model.showLabels, { model.setLabels(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = model.accent))
            }
            Spacer(Modifier.height(20.dp))

            Button("Make DriftHome the default home", model.accent) {
                val rm = activity?.getSystemService(RoleManager::class.java)
                if (rm != null && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    activity?.startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }
            }
            Spacer(Modifier.height(10.dp))
            Button("Reset home screen layout", Color(0x33FFFFFF)) { model.resetLayout(); onDismiss() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Label(t: String) {
    Text(t.uppercase(), color = Color(0xFFA6ADB2), fontSize = 12.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, accent: Color, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0x22FFFFFF), RoundedCornerShape(16.dp)).padding(4.dp)) {
        options.forEach { (label, value) ->
            val bg by animateColorAsState(if (value == selected) accent else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).background(bg, RoundedCornerShape(12.dp)).clickable { onPick(value) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = Color.White, fontWeight = FontWeight.W800) }
        }
    }
}

@Composable
private fun Button(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(color, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.W800, fontSize = 15.sp) }
}
