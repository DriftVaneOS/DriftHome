package org.driftvane.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.AppEntry
import org.driftvane.launcher.Folder
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.isFolder

val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.W700,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f),
)

/** Squircle app tile, visuals only. */
@Composable
fun AppTile(app: AppEntry, size: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp)
            .shadow(10.dp, Squircle, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(Squircle)
    ) {
        Image(app.icon, contentDescription = app.label, modifier = Modifier.fillMaxSize())
    }
}

/** Folder tile: glass squircle with a 3x3 preview of the apps inside. */
@Composable
fun FolderTile(model: LauncherModel, folder: Folder, size: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp)
            .shadow(10.dp, Squircle, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(Squircle)
            .glass(0.dp, 0.55f)
            .padding((size * 0.12f).dp),
    ) {
        val mini = (size * 0.76f / 3f)
        Column(verticalArrangement = Arrangement.spacedBy((size * 0.02f).dp)) {
            folder.apps.take(9).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy((size * 0.02f).dp)) {
                    row.forEach { k ->
                        model.apps[k]?.let { a ->
                            Image(a.icon, null, Modifier.size(mini.dp).clip(Squircle))
                        }
                    }
                }
            }
        }
    }
}

/** Tile + label for any home item (app or folder). [pressed] drives the squish. */
@Composable
fun HomeItem(
    model: LauncherModel,
    key: String,
    size: Float,
    showLabel: Boolean,
    pressed: Boolean = false,
    modifier: Modifier = Modifier,
    onBounds: (Rect) -> Unit = {},
) {
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "press",
    )
    val label = if (isFolder(key)) model.folders[key]?.name ?: "" else model.apps[key]?.label ?: ""
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val tileMod = Modifier.onGloballyPositioned { onBounds(it.boundsInWindow()) }
        if (isFolder(key)) {
            model.folders[key]?.let { FolderTile(model, it, size, tileMod) }
        } else {
            model.apps[key]?.let { AppTile(it, size, tileMod) }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(label, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width((size + 18).dp))
        }
    }
}

/** Simple tappable icon for search results and lists. */
@Composable
fun TapIcon(app: AppEntry, size: Float, showLabel: Boolean, onClick: (Rect) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bounds = remember { arrayOf(Rect.Zero) }
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(0.55f), label = "p")
    Column(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interaction, indication = null) { onClick(bounds[0]) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppTile(app, size, Modifier.onGloballyPositioned { bounds[0] = it.boundsInWindow() })
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(app.label, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width((size + 18).dp))
        }
    }
}
