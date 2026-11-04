package org.driftvane.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.LibraryStyle
import org.driftvane.launcher.Source
import org.driftvane.launcher.SwipeDown
import org.driftvane.launcher.WidgetItem
import org.driftvane.launcher.isFolder
import kotlin.math.abs

interface HomeActions {
    fun launch(key: String, bounds: Rect)
    fun appInfo(key: String)
    fun uninstall(key: String)
    fun expandNotifications()
    fun openWallpaper()
    fun widgetProviders(): List<AppWidgetProviderInfo>
    fun pickWidget(info: AppWidgetProviderInfo, page: Int)
}

@Composable
fun HomeScreen(
    model: LauncherModel,
    homePresses: StateFlow<Int>,
    resumes: StateFlow<Int>,
    actions: HomeActions,
    widget: @Composable (WidgetItem, Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val drawerMode = model.libraryStyle == LibraryStyle.DRAWER
    val pageCount = model.pages.size + if (drawerMode) 0 else 1
    val pager = rememberPagerState { pageCount }
    val drag = remember { DragState() }

    var editMode by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var homeMenu by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var openCategory by remember { mutableStateOf<String?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }

    // Swipe-up drawer: pulling down at the top of the list drags the drawer down; let go to close.
    var drawerPull by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerPull = 0f }
    val drawerScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // dragging back up while pulled down: shrink the pull first
                if (available.y < 0 && drawerPull > 0f) {
                    val new = (drawerPull + available.y).coerceAtLeast(0f)
                    val used = new - drawerPull
                    drawerPull = new
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // list is already at the top and you keep pulling down
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    drawerPull += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawerPull <= 0f) return Velocity.Zero
                val close = drawerPull > with(density) { 140.dp.toPx() } || available.y > 1800f
                if (close) {
                    drawerOpen = false
                } else {
                    animate(drawerPull, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)) { v, _ -> drawerPull = v }
                }
                return available
            }
        }
    }

    fun closeAll() {
        searchOpen = false; menuKey = null; homeMenu = false; settingsOpen = false
        widgetPicker = false; openFolder = null; openCategory = null; drawerOpen = false; editMode = false
    }

    fun hoverAt(pos: Offset): Hover {
        val k = drag.key ?: return Hover.None
        if (model.showDock && drag.dockBounds.contains(pos)) {
            val n = model.dock.count { it != k }
            val slot = drag.dockBounds.width / (n + 1)
            return Hover.Dock(((pos.x - drag.dockBounds.left) / slot).toInt().coerceIn(0, n))
        }
        val page = pager.currentPage
        if (page >= model.pages.size) return Hover.None
        val g = drag.grids[page] ?: return Hover.None
        val rows = model.iconRows(page)
        if (rows <= 0) return Hover.None
        val col = ((pos.x - g.bounds.left) / g.cellW).toInt().coerceIn(0, g.cols - 1)
        val row = ((pos.y - g.bounds.top) / g.cellH).toInt().coerceIn(0, rows - 1)
        val idx = row * g.cols + col
        val items = model.pages[page].items
        val current = previewOrder(items, page, k, drag.hover)
        val base = items.filter { it != k }
        if (idx < current.size) {
            val under = current[idx]
            if (under == k) return drag.hover
            val cx = g.bounds.left + (col + 0.5f) * g.cellW
            val cy = g.bounds.top + (row + 0.5f) * g.cellH
            val near = abs(pos.x - cx) < g.cellW * 0.3f && abs(pos.y - cy) < g.cellH * 0.28f
            if (near && !isFolder(k)) return Hover.Onto(page, under)
        }
        return Hover.Insert(page, idx.coerceAtMost(base.size))
    }

    val handler = remember(model) {
        object : ItemHandler {
            override fun tap(key: String, bounds: Rect) {
                if (isFolder(key)) openFolder = key
                else { actions.launch(key, bounds); openFolder = null; drawerOpen = false; openCategory = null }
            }
            override fun menu(key: String) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuKey = key
            }
            override fun dragStart(key: String, source: Source, at: Offset) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuKey = null; openCategory = null
                editMode = true
                drag.start(key, source, at)
                if (source is Source.Library && pager.currentPage >= model.pages.size) {
                    scope.launch { pager.animateScrollToPage(model.pages.lastIndex) }
                }
            }
            override fun dragMove(at: Offset) {
                drag.pointer = at
                val h = hoverAt(at)
                if (h != drag.hover) {
                    if (h is Hover.Onto || h is Hover.Dock) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    drag.hover = h
                }
            }
            override fun dragEnd() {
                val k = drag.key
                val src = drag.source
                val h = drag.hover
                // Apply the move while the preview still shows it, then end the drag, so the
                // layout never flashes back to the old order.
                if (k != null && src != null) when (h) {
                    is Hover.Dock -> model.moveToDock(k, src, h.index)
                    is Hover.Insert -> model.moveToPage(k, src, h.page, h.index)
                    is Hover.Onto -> model.dropOnto(k, src, h.page, h.target)
                    Hover.None -> Unit
                }
                drag.end()
                model.tidy()
                if (src is Source.InFolder) openFolder = null
                if (src is Source.Library) drawerOpen = false
            }
        }
    }

    // Drag to a screen edge and hold: flip to the next page (a new page appears at the end).
    LaunchedEffect(drag.active) {
        var held = 0
        val edge = with(density) { 36.dp.toPx() }
        while (drag.active) {
            delay(60)
            val x = drag.pointer.x
            val dir = when { x < edge -> -1; x > screenW - edge -> 1; else -> 0 }
            if (dir == 0) { held = 0; continue }
            held += 60
            if (held >= 480) {
                held = 0
                val target = pager.currentPage + dir
                if (target >= 0) {
                    if (target >= model.pages.size) model.ensurePage(target)
                    pager.animateScrollToPage(target, animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f))
                }
            }
        }
    }

    val home by homePresses.collectAsStateCompat()
    LaunchedEffect(home) {
        if (home > 0) {
            closeAll()
            pager.animateScrollToPage(0, animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f))
        }
    }
    val entrance = remember { Animatable(1f) }
    val resumed by resumes.collectAsStateCompat()
    LaunchedEffect(resumed) {
        entrance.snapTo(0.93f)
        entrance.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 260f))
    }

    val anyOverlay = searchOpen || menuKey != null || homeMenu || widgetPicker || openFolder != null ||
        openCategory != null || drawerOpen || settingsOpen
    BackHandler(anyOverlay || editMode) {
        when {
            settingsOpen -> settingsOpen = false
            widgetPicker -> widgetPicker = false
            menuKey != null -> menuKey = null
            homeMenu -> homeMenu = false
            openFolder != null -> openFolder = null
            openCategory != null -> openCategory = null
            drawerOpen -> drawerOpen = false
            searchOpen -> searchOpen = false
            else -> editMode = false
        }
    }

    val blur by animateDpAsState(if (anyOverlay && !drag.active && !settingsOpen) 26.dp else 0.dp, tween(240), label = "blur")
    val pull = with(density) { 90.dp.toPx() }
    val onLibrary = pager.currentPage >= model.pages.size

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .blur(blur)
                .graphicsLayer {
                    scaleX = entrance.value; scaleY = entrance.value
                    alpha = (entrance.value - 0.93f) / 0.07f * 0.6f + 0.4f
                }
                .pointerInput(editMode, onLibrary, model.swipeDown, drawerMode) {
                    if (editMode || onLibrary) return@pointerInput
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onVerticalDrag = { _, d -> dy += d },
                        onDragEnd = {
                            if (dy > pull) when (model.swipeDown) {
                                SwipeDown.SEARCH -> searchOpen = true
                                SwipeDown.NOTIFICATIONS -> actions.expandNotifications()
                            }
                            if (dy < -pull && drawerMode) drawerOpen = true
                        },
                    )
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !drag.active,
                    beyondViewportPageCount = 2,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    if (page < model.pages.size) {
                        Box(Modifier.fillMaxSize()) {
                            // empty space: long-press for the home menu, tap to leave edit mode
                            Box(
                                Modifier.matchParentSize().pointerInput(editMode) {
                                    detectTapGestures(
                                        onLongPress = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            homeMenu = true
                                        },
                                        onTap = { if (editMode) editMode = false },
                                    )
                                }
                            )
                            Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp)) {
                                HomePage(model, page, editMode, drag, handler, widget)
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            AppLibrary(model, handler,
                                onSearch = { searchOpen = true },
                                onSettings = { settingsOpen = true },
                                onOpenCategory = { openCategory = it })
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        model.showDots && !editMode, enter = fadeIn(), exit = fadeOut(),
                    ) { PageDots(pageCount, pager.currentPage, model.accent) }
                    androidx.compose.animation.AnimatedVisibility(
                        editMode,
                        enter = fadeIn() + scaleIn(initialScale = 0.85f),
                        exit = fadeOut() + scaleOut(targetScale = 0.85f),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Pill("+ Widget", Color(0x99262330)) { widgetPicker = true }
                            Pill("Done", model.accent) { editMode = false }
                        }
                    }
                }
                if (model.showDock) Dock(model, editMode, drag, handler)
                else Spacer(Modifier.navigationBarsPadding().height(12.dp))
            }

        }

        // the icon under your finger while dragging
        val dk = drag.key
        if (dk != null) {
            val size = 60f * model.iconScale
            val half = with(density) { (size / 2).dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((drag.pointer.x - half).toInt(), (drag.pointer.y - half).toInt()) }
                    .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }
            ) { HomeItem(model, dk, size, false) }
        }

        val hideForDrag = drag.active
        Overlay(searchOpen) { SearchOverlay(model, actions::launch) { searchOpen = false } }
        Overlay(openFolder != null, onDismiss = { openFolder = null }, center = true, hidden = hideForDrag && drag.source is Source.InFolder) {
            val id = openFolder
            if (id != null) FolderOverlay(model, id, handler)
        }
        Overlay(openCategory != null, onDismiss = { openCategory = null }, center = true, hidden = hideForDrag) {
            val c = openCategory
            if (c != null) CategorySheet(model, c, handler)
        }
        AnimatedVisibility(
            drawerOpen,
            enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            Box(
                Modifier.fillMaxSize()
                    .offset { IntOffset(0, drawerPull.toInt()) }
                    .graphicsLayer {
                        alpha = if (hideForDrag && drag.source is Source.Library) 0f
                        else 1f - (drawerPull / (screenW * 2f)).coerceIn(0f, 0.5f)
                    }
                    .background(Color(0xD90B0A0D), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .statusBarsPadding()
                    .nestedScroll(drawerScroll)
            ) { AppDrawer(model, handler, onClose = { drawerOpen = false }) { searchOpen = true } }
        }
        Overlay(homeMenu, onDismiss = { homeMenu = false }) {
            HomeMenu(model,
                onEdit = { homeMenu = false; editMode = true },
                onWidgets = { homeMenu = false; widgetPicker = true },
                onSettings = { homeMenu = false; settingsOpen = true },
                onWallpaper = { homeMenu = false; actions.openWallpaper() })
        }
        Overlay(menuKey != null, onDismiss = { menuKey = null }) {
            val k = menuKey
            if (k != null) ItemMenu(model, k,
                onEdit = { menuKey = null; openFolder = null; editMode = true },
                onInfo = { menuKey = null; actions.appInfo(k) },
                onUninstall = { menuKey = null; actions.uninstall(k) },
                onClose = { menuKey = null })
        }
        Overlay(widgetPicker, onDismiss = { widgetPicker = false }, center = true) {
            WidgetPicker(model, remember { actions.widgetProviders() }) { info ->
                widgetPicker = false
                editMode = false
                actions.pickWidget(info, pager.currentPage.coerceAtMost(model.pages.lastIndex))
            }
        }
        AnimatedVisibility(
            settingsOpen,
            enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
        ) { SettingsPage(model) { settingsOpen = false } }
    }
}

@Composable
private fun Overlay(
    visible: Boolean,
    onDismiss: (() -> Unit)? = null,
    center: Boolean = false,
    hidden: Boolean = false,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.96f),
    ) {
        if (onDismiss == null) {
            content()
        } else {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = if (hidden) 0f else 1f }
                    .background(Color(0x55000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss() },
                contentAlignment = if (center) Alignment.Center else Alignment.BottomCenter,
            ) { content() }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.background(color, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) { Text(text, color = Color.White, fontWeight = FontWeight.W800, fontSize = 15.sp) }
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
private fun Dock(model: LauncherModel, editMode: Boolean, drag: DragState, handler: ItemHandler) {
    val dk = drag.key
    val base = model.dock.filter { it != dk }
    val h = drag.hover
    val order = when {
        dk == null -> model.dock.toList()
        h is Hover.Dock -> base.toMutableList().apply { add(h.index.coerceIn(0, base.size), dk) }
        dk in model.dock -> base + dk
        else -> model.dock.toList()
    }
    val size = 58f * model.iconScale
    Row(
        Modifier.navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 10.dp)
            .fillMaxWidth()
            .onGloballyPositioned { drag.dockBounds = it.boundsInRoot() }
            .glass(32.dp, model.glassStrength * 0.85f)
            .padding(vertical = 14.dp)
            .height((size + 4).dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (order.isEmpty()) {
            Text("Drag apps here", color = Color(0xFFA6ADB2), fontWeight = FontWeight.W700, fontSize = 13.sp)
        }
        order.forEach { k ->
            key(k) {
                var pressed by remember { mutableStateOf(false) }
                val topLeft = remember { arrayOf(Offset.Zero) }
                val bounds = remember { arrayOf(Rect.Zero) }
                val hidden = dk == k
                Box(
                    Modifier
                        .onGloballyPositioned { topLeft[0] = it.positionInRoot() }
                        .graphicsLayer { alpha = if (hidden) 0f else 1f }
                        .iconGestures(
                            editMode = editMode,
                            rootTopLeft = { topLeft[0] },
                            onPress = { pressed = it },
                            onTap = { handler.tap(k, bounds[0]) },
                            onMenu = { handler.menu(k) },
                            onDragStart = { handler.dragStart(k, Source.Dock, it) },
                            onDragMove = { handler.dragMove(it) },
                            onDragEnd = { handler.dragEnd() },
                        )
                ) {
                    HomeItem(model, k, size, false, pressed, onBounds = { bounds[0] = it })
                    if (editMode && !hidden) RemoveBadge(Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp)) {
                        model.toggleDock(k)
                    }
                }
            }
        }
    }
}
