package org.driftvane.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.Source
import org.driftvane.launcher.isFolder

/** Blocks taps from falling through to whatever is behind a card. */
@Composable
fun Modifier.swallowTaps(): Modifier = clickable(remember { MutableInteractionSource() }, indication = null) {}

/** Open folder: rename it, open apps, or drag apps out. */
@Composable
fun FolderOverlay(model: LauncherModel, id: String, handler: ItemHandler) {
    val folder = model.folders[id] ?: return
    var name by remember(id) { mutableStateOf(folder.name) }
    Column(
        Modifier.fillMaxWidth().padding(22.dp).glass(34.dp, 0.88f).swallowTaps().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it; model.renameFolder(id, it) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.W900, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(model.accent),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        folder.apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        var pressed by remember { mutableStateOf(false) }
                        val topLeft = remember { arrayOf(Offset.Zero) }
                        val bounds = remember { arrayOf(Rect.Zero) }
                        Box(
                            Modifier.onGloballyPositioned { topLeft[0] = it.positionInRoot() }
                                .iconGestures(
                                    editMode = false,
                                    rootTopLeft = { topLeft[0] },
                                    onPress = { pressed = it },
                                    onTap = { handler.tap(k, bounds[0]) },
                                    onMenu = { handler.menu(k) },
                                    onDragStart = { handler.dragStart(k, Source.InFolder(id), it) },
                                    onDragMove = { handler.dragMove(it) },
                                    onDragEnd = { handler.dragEnd() },
                                )
                        ) { HomeItem(model, k, 56f, true, pressed, onBounds = { bounds[0] = it }) }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Hold and drag an app to take it out", color = Color(0xFFA6ADB2), fontSize = 12.sp, fontWeight = FontWeight.W700)
    }
}

/** Long-press on an empty spot of the home screen. */
@Composable
fun HomeMenu(
    model: LauncherModel,
    onEdit: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit,
    onWallpaper: () -> Unit,
) {
    Column(Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().glass(30.dp, 0.9f).swallowTaps().padding(18.dp)) {
        Text("Home screen", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.W900)
        Spacer(Modifier.height(12.dp))
        MenuRow("Edit home screen", model.accent, onEdit)
        MenuRow("Add widget", model.accent, onWidgets)
        MenuRow("Wallpaper & style", model.accent, onWallpaper)
        MenuRow("Home settings", model.accent, onSettings)
    }
}

/** Long-press on an app or folder. */
@Composable
fun ItemMenu(
    model: LauncherModel,
    key: String,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onClose: () -> Unit,
) {
    val folder = isFolder(key)
    val title = if (folder) model.folders[key]?.name ?: "Folder" else model.apps[key]?.label ?: return
    val inDock = key in model.dock
    val onHome = inDock || model.pages.any { key in it.items } || model.folders.values.any { key in it.apps }
    Column(
        Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().glass(30.dp, 0.9f).swallowTaps().padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HomeItem(model, key, 52f, false)
            Spacer(Modifier.width(14.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.W900)
        }
        Spacer(Modifier.height(14.dp))
        MenuRow("Edit home screen", model.accent, onEdit)
        if (!folder) MenuRow(if (inDock) "Remove from dock" else "Add to dock", model.accent) { model.toggleDock(key); onClose() }
        if (onHome) MenuRow(if (folder) "Remove folder (keep apps)" else "Remove from home", model.accent) { model.removeFromHome(key); onClose() }
        else MenuRow("Add to home", model.accent) { model.addToHome(key); onClose() }
        if (!folder) {
            MenuRow("App info", model.accent, onInfo)
            MenuRow("Uninstall", Color(0xFFFF4D5E), onUninstall)
        }
    }
}

@Composable
fun MenuRow(text: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(50)))
            Spacer(Modifier.width(12.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.W700, fontSize = 15.sp)
        }
    }
}

/** Pick a widget: grouped by app, with previews. */
@Composable
fun WidgetPicker(model: LauncherModel, providers: List<AppWidgetProviderInfo>, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val grouped = remember(providers) {
        providers.groupBy { it.provider.packageName }.toList().sortedBy { (pkg, _) ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString() }
                .getOrDefault(pkg).lowercase()
        }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(14.dp)
            .glass(30.dp, 0.92f).swallowTaps().padding(16.dp)
    ) {
        Text("Add a widget", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.W900)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            grouped.forEach { (pkg, list) ->
                item(key = pkg) {
                    val appName = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
                    }.getOrDefault(pkg)
                    Column(Modifier.fillMaxWidth().background(Color(0x14FFFFFF), RoundedCornerShape(20.dp)).padding(12.dp)) {
                        Text(appName, color = model.accent, fontSize = 13.sp, fontWeight = FontWeight.W800)
                        list.forEach { info ->
                            val label = runCatching { info.loadLabel(pm) }.getOrDefault("Widget")
                            val preview = remember(info) {
                                runCatching {
                                    val d = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
                                    val w = d.intrinsicWidth.takeIf { it > 0 } ?: 300
                                    val h = d.intrinsicHeight.takeIf { it > 0 } ?: 200
                                    val f = 360f / maxOf(w, h)
                                    d.toBitmap((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1)).asImageBitmap()
                                }.getOrNull()
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
                                    .clickable { onPick(info) }.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(120.dp).heightIn(min = 60.dp, max = 90.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                                    if (preview != null) Image(preview, null, Modifier.fillMaxWidth())
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W700)
                            }
                        }
                    }
                }
            }
        }
    }
}
