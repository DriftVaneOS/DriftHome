package org.driftvane.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.LauncherModel

/** "App Shelf": the last page. Every app, grouped into glass cards by category. */
@Composable
fun AppShelf(
    model: LauncherModel,
    onLaunch: (String, Rect) -> Unit,
    onMenu: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val groups = remember(model.apps.size, model.apps.keys.hashCode()) { model.shelfGroups() }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("App Shelf", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.W900, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).background(model.accent.copy(alpha = 0.22f), CircleShape).clickable { onSettings() },
                    contentAlignment = Alignment.Center,
                ) { Text("⚙", color = Color.White, fontSize = 20.sp) }
            }
        }
        items(groups, key = { it.first }) { (title, apps) ->
            Column(Modifier.fillMaxWidth().glass(26.dp).padding(14.dp)) {
                Text(title.uppercase(), color = model.accent, fontSize = 12.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                apps.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        row.forEach { app ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                AppIcon(app, 52f, true,
                                    onClick = { r -> onLaunch(app.key, r) },
                                    onLongClick = { onMenu(app.key) })
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(140.dp)) }
    }
}
