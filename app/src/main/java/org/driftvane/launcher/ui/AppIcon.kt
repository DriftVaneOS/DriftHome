package org.driftvane.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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

val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.W700,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppEntry,
    iconSize: Float,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val bounds = remember { arrayOf(Rect.Zero) }
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(iconSize.dp)
                .shadow(10.dp, Squircle, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(Squircle)
                .onGloballyPositioned { bounds[0] = it.boundsInWindow() }
                .then(
                    if (enabled) Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onClick(bounds[0]) },
                        onLongClick = onLongClick,
                    ) else Modifier
                )
        ) {
            Image(app.icon, contentDescription = app.label, modifier = Modifier.size(iconSize.dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = LabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width((iconSize + 18).dp),
            )
        }
    }
}
