package org.driftvane.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.driftvane.launcher.LauncherModel

interface HomeActions {
    fun launch(key: String, bounds: Rect)
    fun appInfo(key: String)
    fun uninstall(key: String)
}

@Composable
fun HomeScreen(
    model: LauncherModel,
    homePresses: StateFlow<Int>,
    resumes: StateFlow<Int>,
    actions: HomeActions,
) {
    val scope = rememberCoroutineScope()
    val pageCount = model.pages.size + 1 // + App Shelf
    val pager = rememberPagerState { pageCount }

    var editMode by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    // Home button: close everything and glide back to the first page.
    val home by homePresses.collectAsStateCompat()
    LaunchedEffect(home) {
        if (home > 0) {
            searchOpen = false; menuKey = null; editMode = false; settingsOpen = false
            pager.animateScrollToPage(0, animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f))
        }
    }
    // Coming back home: everything settles in with a soft spring.
    val entrance = remember { Animatable(1f) }
    val resumed by resumes.collectAsStateCompat()
    LaunchedEffect(resumed) {
        entrance.snapTo(0.93f)
        entrance.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 260f))
    }

    BackHandler(searchOpen || menuKey != null || editMode) {
        when {
            menuKey != null -> menuKey = null
            searchOpen -> searchOpen = false
            else -> editMode = false
        }
    }

    val blur by animateDpAsState(if (searchOpen || menuKey != null) 28.dp else 0.dp, tween(260), label = "blur")
    val density = LocalDensity.current
    val pullThreshold = with(density) { 90.dp.toPx() }
    val onShelf = pager.currentPage >= model.pages.size

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .blur(blur)
                .graphicsLayer {
                    scaleX = entrance.value; scaleY = entrance.value
                    alpha = (entrance.value - 0.93f) / 0.07f * 0.6f + 0.4f
                }
                .pointerInput(editMode, onShelf) {
                    if (editMode || onShelf) return@pointerInput
                    var pulled = 0f
                    detectVerticalDragGestures(
                        onDragStart = { pulled = 0f },
                        onVerticalDrag = { _, dy -> pulled += dy },
                        onDragEnd = { if (pulled > pullThreshold) searchOpen = true },
                    )
                }
                .pointerInput(editMode) {
                    if (editMode) detectTapGestures { editMode = false }
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !dragging,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    if (page < model.pages.size) {
                        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp)) {
                            if (page == 0) {
                                GlanceCard(model.accent, Modifier.padding(horizontal = 16.dp))
                                Spacer(Modifier.height(14.dp))
                            }
                            PageGrid(
                                model = model,
                                pageIndex = page,
                                items = model.pages[page],
                                rows = if (page == 0) 4 else model.rows,
                                editMode = editMode,
                                onEnterEdit = { editMode = true },
                                onDragging = { dragging = it },
                                onLaunch = actions::launch,
                                onMenu = { menuKey = it },
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            AppShelf(model, actions::launch, onMenu = { menuKey = it }, onSettings = { settingsOpen = true })
                        }
                    }
                }

                PageDots(pageCount, pager.currentPage, model.accent)
                Spacer(Modifier.height(10.dp))
                Dock(model, editMode, actions::launch) { menuKey = it }
            }

            // "Done" button while editing
            AnimatedVisibility(
                editMode,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
            ) {
                Box(
                    Modifier.background(model.accent, RoundedCornerShape(50)).clickable { editMode = false }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text("Done", color = Color.White, fontWeight = FontWeight.W800, fontSize = 15.sp) }
            }
        }

        AnimatedVisibility(
            searchOpen,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { -it / 6 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 8 },
        ) {
            SearchOverlay(model, actions::launch) { searchOpen = false }
        }

        AnimatedVisibility(
            menuKey != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0x55000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { menuKey = null },
                contentAlignment = Alignment.BottomCenter,
            ) {
                val k = menuKey
                if (k != null) {
                    AppMenu(
                        model, k,
                        onEdit = { menuKey = null; editMode = true; scope.launch { } },
                        onInfo = { menuKey = null; actions.appInfo(k) },
                        onUninstall = { menuKey = null; actions.uninstall(k) },
                        onClose = { menuKey = null },
                    )
                }
            }
        }

        if (settingsOpen) SettingsSheet(model) { settingsOpen = false }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, accent: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 22.dp else 7.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "dot")
            Box(
                Modifier.padding(horizontal = 3.dp).height(7.dp).width(w)
                    .background(if (i == current) accent else Color(0x66FFFFFF), RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun Dock(model: LauncherModel, editMode: Boolean, onLaunch: (String, Rect) -> Unit, onMenu: (String) -> Unit) {
    Row(
        Modifier.navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 10.dp)
            .fillMaxWidth().glass(32.dp, 0.6f).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model.dock.isEmpty()) {
            Text("Long-press an app → Add to dock", color = Color(0xFFA6ADB2), fontWeight = FontWeight.W700, fontSize = 13.sp)
        }
        model.dock.forEach { key ->
            val app = model.apps[key] ?: return@forEach
            AppIcon(app, 58f * model.iconScale, showLabel = false, enabled = !editMode,
                onClick = { r -> onLaunch(key, r) }, onLongClick = { onMenu(key) })
        }
    }
}

@Composable
private fun AppMenu(
    model: LauncherModel,
    key: String,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onClose: () -> Unit,
) {
    val app = model.apps[key] ?: return
    val inDock = key in model.dock
    val onHome = inDock || model.pages.any { key in it }
    Column(
        Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().glass(30.dp, 0.9f).padding(18.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 52f, false, onClick = {}, onLongClick = {})
            Spacer(Modifier.width(14.dp))
            Text(app.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.W900)
        }
        Spacer(Modifier.height(14.dp))
        MenuRow("Edit home screen", model.accent, onEdit)
        MenuRow(if (inDock) "Remove from dock" else "Add to dock", model.accent) { model.toggleDock(key); onClose() }
        if (onHome) MenuRow("Remove from home", model.accent) { model.removeFromHome(key); onClose() }
        else MenuRow("Add to home", model.accent) { model.addToHome(key); onClose() }
        MenuRow("App info", model.accent, onInfo)
        MenuRow("Uninstall", Color(0xFFFF4D5E), onUninstall)
    }
}

@Composable
private fun MenuRow(text: String, accent: Color, onClick: () -> Unit) {
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
