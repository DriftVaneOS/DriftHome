package org.driftvane.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.LauncherModel

private val Bg = Color(0xFF0B1022)
private val Tile = Color(0xFF1A2138)
private val Blue = Color(0xFF2F6BFF)
private val Muted = Color(0xFF8EA3D6)

/**
 * First-boot setup: pick your home screen.
 * Shown once (after the phone's setup), and again from Home settings > Run setup again.
 */
@Composable
fun SetupFlow(model: LauncherModel) {
    var homeStyle by remember { mutableStateOf("frame") }

    Box(Modifier.fillMaxSize().background(Bg).swallowTaps()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            Text("DriftVaneOS · Made by Alex Desrosier", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.W600)
            Spacer(Modifier.height(6.dp))
            Text("Pick your home screen", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.W700)
            Text(
                "How your home screen is laid out. You can change it later in Home settings.",
                color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                model.homeStyles.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (id, name) ->
                            Choice(name, homeStyle == id, Modifier.weight(1f), onClick = { homeStyle = id }) {
                                HomePreview(id)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Blue)
                        .clickable { model.finishSetup(homeStyle) }
                        .padding(horizontal = 30.dp, vertical = 14.dp)
                ) {
                    Text("Done", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W700)
                }
            }
        }
    }
}

@Composable
private fun Choice(name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val ring by animateColorAsState(if (selected) Blue else Color.Transparent, label = "ring")
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(3.dp, ring, RoundedCornerShape(22.dp))
                .clickable(onClick = onClick).padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxHeight().aspectRatio(0.5f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF0D1326)).padding(7.dp)) {
                preview()
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, color = if (selected) Color.White else Muted, fontSize = 14.sp, fontWeight = FontWeight.W600,
            textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- tiny previews
@Composable private fun B(c: Color, m: Modifier, r: Dp = 6.dp) = Box(m.clip(RoundedCornerShape(r)).background(c))

private val appColors = listOf(
    Color(0xFFFF8A5C), Color(0xFF58D1A8), Color(0xFFFFD166), Color(0xFF8C7CFF),
    Color(0xFFF09CE2), Color(0xFF8CD0F0), Color(0xFFC9C2FF), Color(0xFFFF6B78),
)

@Composable
private fun AppRow(offset: Int = 0) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(4) { B(appColors[(it + offset) % appColors.size], Modifier.weight(1f).aspectRatio(1f), 4.dp) }
    }
}

@Composable
private fun HomePreview(id: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (id) {
            "frame" -> {
                B(Color(0xFF3A5BD9), Modifier.fillMaxWidth().weight(1.4f), 9.dp)
                AppRow(); AppRow(4)
                Spacer(Modifier.weight(0.6f))
            }
            "split" -> {
                Row(Modifier.fillMaxWidth().weight(0.9f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    B(Blue, Modifier.weight(1f).fillMaxHeight(), 8.dp)
                    B(Tile, Modifier.width(8.dp).fillMaxHeight(), 4.dp)
                }
                AppRow(); AppRow(4); AppRow(2)
                Spacer(Modifier.weight(0.4f))
            }
            "stack" -> {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    B(Color(0xFF1C2340), Modifier.fillMaxSize().padding(horizontal = 8.dp), 8.dp)
                    B(Color(0xFF27306A), Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 3.dp), 8.dp)
                    B(Blue, Modifier.fillMaxSize().padding(top = 6.dp), 8.dp)
                }
                AppRow(); AppRow(4)
                Spacer(Modifier.weight(0.5f))
            }
            else -> {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        B(Blue, Modifier.fillMaxWidth().weight(1.2f), 7.dp)
                        B(Tile, Modifier.fillMaxWidth().weight(1f), 7.dp)
                        B(Tile, Modifier.fillMaxWidth().weight(1f), 7.dp)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        appColors.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                B(c, Modifier.size(9.dp), 3.dp)
                                Spacer(Modifier.width(3.dp))
                                B(Color(0xFF3A4366), Modifier.weight(1f).height(3.dp), 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
