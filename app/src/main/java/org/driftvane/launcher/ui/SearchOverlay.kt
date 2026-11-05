package org.driftvane.launcher.ui

import android.app.SearchManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.LauncherModel

/** "Drift Search": swipe down on the home screen to find apps or search the web. */
@Composable
fun SearchOverlay(model: LauncherModel, onLaunch: (String, Rect) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val results = remember(query, model.apps.size, model.hiddenApps.size, model.iconTheme) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) model.visibleApps().sortedBy { it.label.lowercase() }.take(8)
        else model.visibleApps()
            .filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
    }

    fun webSearch() {
        if (query.isBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        onClose()
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color(0x66000000))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().glass(22.dp).padding(horizontal = 18.dp, vertical = 16.dp)) {
                if (query.isEmpty()) {
                    Text("Search apps and the web", color = Color(0xFFA6ADB2), fontSize = 17.sp, fontWeight = FontWeight.W700)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.W700),
                    cursorBrush = SolidColor(model.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        val first = results.firstOrNull()
                        if (first != null && query.isNotBlank()) {
                            onLaunch(first.key, Rect.Zero); onClose()
                        } else webSearch()
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().glass(28.dp).padding(14.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Text(
                    if (query.isBlank()) "SUGGESTED" else "APPS",
                    color = model.accent, fontSize = 12.sp, fontWeight = FontWeight.W600, letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().height(if (results.size > 4) 220.dp else 110.dp),
                ) {
                    items(results, key = { it.key }) { app ->
                        Box(contentAlignment = Alignment.Center) {
                            TapIcon(app, 54f, true) { r -> onLaunch(app.key, r); onClose() }
                        }
                    }
                }
                if (query.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().background(model.accent.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .clickable { webSearch() }.padding(14.dp)
                    ) {
                        Text("Search the web for “$query”", color = Color.White, fontWeight = FontWeight.W600, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
