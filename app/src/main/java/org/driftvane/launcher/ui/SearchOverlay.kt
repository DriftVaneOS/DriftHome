package org.driftvane.launcher.ui

import android.app.SearchManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
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

    val suggested = remember(model.apps.size, model.hiddenApps.size) {
        model.suggestions(4).ifEmpty { model.visibleApps().sortedBy { it.label.lowercase() }.take(4) }
    }
    val card = Color(0xFF1B1F27)
    val hint = Color(0xFFB8BEC9)

    Box(
        Modifier.fillMaxSize()
            .background(Color(0xF20B0E14))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            // search field + Cancel
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(card)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.SEARCH, hint, 19.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search", color = hint, fontSize = 17.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
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
                }
                Text("Cancel", color = model.accent, fontSize = 17.sp, fontWeight = FontWeight.W500,
                    modifier = Modifier.clickable { onClose() }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp))
            }
            Spacer(Modifier.height(18.dp))

            val shown = if (query.isBlank()) suggested else results
            Text(if (query.isBlank()) "Suggestions" else "Apps", color = Color.White,
                fontSize = 20.sp, fontWeight = FontWeight.W700, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            if (shown.isEmpty()) {
                Text("No apps found", color = hint, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)
                        .clip(RoundedCornerShape(20.dp)).background(card)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .padding(vertical = 14.dp),
                ) {
                    items(shown, key = { it.key }) { app ->
                        Box(contentAlignment = Alignment.Center) {
                            TapIcon(app, 56f, true) { r -> onLaunch(app.key, r); onClose() }
                        }
                    }
                }
            }
            if (query.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(card)
                        .clickable { webSearch() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.SEARCH, model.accent, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Search the web", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W600)
                        Text("“$query”", color = hint, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
