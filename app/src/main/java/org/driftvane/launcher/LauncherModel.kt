package org.driftvane.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    val category: Int,
    val installedAt: Long,
)

data class Folder(val name: String, val apps: List<String>)
data class AppShortcut(val id: String, val label: String, val pkg: String, val user: UserHandle)

object IconTheme {
    const val ORIGINAL = "original"
    const val THEMED = "themed"
    const val LIGHT = "light"
    const val GLASS = "glass"
    const val MONO = "mono"
    val all = listOf(ORIGINAL to "Original", THEMED to "Themed", LIGHT to "Light", GLASS to "Liquid glass", MONO to "Mono")
}

/** Rows a page's widgets take: half-width widgets sit side by side in pairs. */
fun widgetBlockRows(ws: List<WidgetItem>): Int {
    var total = 0
    var i = 0
    while (i < ws.size) {
        val w = ws[i]
        if (w.half && i + 1 < ws.size && ws[i + 1].half) {
            total += maxOf(w.rows, ws[i + 1].rows); i += 2
        } else {
            total += w.rows; i++
        }
    }
    return total
}
data class WidgetItem(val id: Int, val rows: Int, val half: Boolean = false)
data class Page(val widgets: List<WidgetItem> = emptyList(), val items: List<String> = emptyList())

/** Where a dragged item came from. */
sealed class Source {
    data class OnPage(val page: Int) : Source()
    data object Dock : Source()
    data class InFolder(val folder: String) : Source()
    data object Library : Source()
}

/** AlexLMP accent colors. */
val Accents = listOf(
    Color(0xFFFF4D5E), Color(0xFF8CD0F0), Color(0xFFF09CE2), Color(0xFFFFB59C), Color(0xFFFF8A3D),
)

object LibraryStyle {
    const val SMART = "smart"
    const val CARDS = "cards"
    const val LIST = "list"
    const val DRAWER = "drawer"
}

object SwipeDown {
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val NOTHING = "nothing"
}

fun isFolder(key: String) = key.startsWith("folder:")

/** An empty cell on a home page (apps can sit anywhere on the grid, with gaps). */
const val EMPTY = ""

private fun List<String>.trimEmpty(): List<String> = dropLastWhile { it == EMPTY }

/** Take [key] off a page but keep everyone else where they are. */
private fun List<String>.gapOut(key: String): List<String> = map { if (it == key) EMPTY else it }.trimEmpty()

class LauncherModel(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val prefs = context.getSharedPreferences("drifthome", Context.MODE_PRIVATE)

    // v0.3.4 look: 5 columns with XL icons, applied once (users can change it after).
    init {
        if (prefs.getInt("look", 0) < 5) {
            prefs.edit().putInt("look", 5).putInt("columns", 5).putFloat("icon_scale", 1.18f)
                .putFloat("label_size", 12f).putBoolean("pending_reflow", true).apply()
        }
    }

    val apps = mutableStateMapOf<String, AppEntry>()
    val dock = mutableStateListOf<String>()
    val pages = mutableStateListOf<Page>()
    val folders = mutableStateMapOf<String, Folder>()
    var loaded by mutableStateOf(false)
        private set

    // ---- settings ----
    var accentIndex by mutableStateOf(prefs.getInt("accent", 0))
        private set
    var showLabels by mutableStateOf(prefs.getBoolean("labels", true))
        private set
    var iconScale by mutableStateOf(prefs.getFloat("icon_scale", 1.18f))
        private set
    var columns by mutableStateOf(prefs.getInt("columns", 5))
        private set
    var showGlance by mutableStateOf(prefs.getBoolean("glance", true))
        private set
    var showDots by mutableStateOf(prefs.getBoolean("dots", true))
        private set
    var showDock by mutableStateOf(prefs.getBoolean("dock", true))
        private set
    var libraryStyle by mutableStateOf(prefs.getString("library", LibraryStyle.SMART)!!)
        private set
    var swipeDown by mutableStateOf(prefs.getString("swipe_down", SwipeDown.SEARCH)!!)
        private set
    var glassStrength by mutableStateOf(prefs.getFloat("glass", 0.72f))
        private set
    var iconShape by mutableStateOf(prefs.getString("icon_shape", "squircle")!!)
        private set
    var iconTheme by mutableStateOf(prefs.getString("icon_theme", IconTheme.ORIGINAL)!!)
        private set
    var dockStyle by mutableStateOf(prefs.getString("dock_style", "glass")!!)
        private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true))
        private set
    var lockLayout by mutableStateOf(prefs.getBoolean("lock_layout", false))
        private set
    var labelSize by mutableStateOf(prefs.getFloat("label_size", 12f))
        private set
    var hiddenApps by mutableStateOf(prefs.getStringSet("hidden_apps", emptySet())!!.toSet())
        private set

    val accent: Color get() = Accents[accentIndex.coerceIn(0, Accents.lastIndex)]
    val rows = 6

    /** Height of one grid row; HomeScreen sets it so the 6 rows fill the screen evenly. */
    var cellH by mutableStateOf(112.dp)
    private val glanceRows = 2

    fun changeAccent(i: Int) {
        accentIndex = i; prefs.edit().putInt("accent", i).apply()
        if (iconTheme == IconTheme.THEMED || iconTheme == IconTheme.LIGHT) reload()
    }
    fun changeLabels(v: Boolean) { showLabels = v; prefs.edit().putBoolean("labels", v).apply() }
    fun changeIconScale(v: Float) { iconScale = v; prefs.edit().putFloat("icon_scale", v).apply() }
    fun changeDots(v: Boolean) { showDots = v; prefs.edit().putBoolean("dots", v).apply() }
    fun changeDock(v: Boolean) { showDock = v; prefs.edit().putBoolean("dock", v).apply() }
    fun changeLibrary(v: String) { libraryStyle = v; prefs.edit().putString("library", v).apply() }
    fun changeSwipeDown(v: String) { swipeDown = v; prefs.edit().putString("swipe_down", v).apply() }
    fun changeGlass(v: Float) { glassStrength = v; prefs.edit().putFloat("glass", v).apply() }
    fun changeShape(v: String) { iconShape = v; prefs.edit().putString("icon_shape", v).apply() }
    fun changeTheme(v: String) { iconTheme = v; prefs.edit().putString("icon_theme", v).apply(); reload() }
    fun changeDockStyle(v: String) { dockStyle = v; prefs.edit().putString("dock_style", v).apply() }
    fun changeHaptics(v: Boolean) { haptics = v; prefs.edit().putBoolean("haptics", v).apply() }
    fun changeLock(v: Boolean) { lockLayout = v; prefs.edit().putBoolean("lock_layout", v).apply() }
    fun changeLabelSize(v: Float) { labelSize = v; prefs.edit().putFloat("label_size", v).apply() }

    fun setHidden(key: String, hidden: Boolean) {
        hiddenApps = if (hidden) hiddenApps + key else hiddenApps - key
        prefs.edit().putStringSet("hidden_apps", hiddenApps).apply()
        if (hidden) {
            dock.remove(key)
            for (i in pages.indices) pages[i] = pages[i].copy(items = pages[i].items.gapOut(key))
            for (id in folders.keys.toList()) folders[id]?.let { f -> folders[id] = f.copy(apps = f.apps - key) }
            cleanupFolders(); compact(); save()
        } else {
            addToHome(key)
        }
    }

    /** Apps that aren't hidden (library, search, suggestions). */
    fun visibleApps(): List<AppEntry> = apps.values.filter { it.key !in hiddenApps }
    fun changeColumns(v: Int) {
        columns = v; prefs.edit().putInt("columns", v).apply(); reflowAll()
    }
    fun changeGlance(v: Boolean) {
        showGlance = v; prefs.edit().putBoolean("glance", v).apply(); reflowAll()
    }

    /** Grid rows left for icons on a page after the glance card and widgets. */
    fun iconRows(page: Int): Int {
        val used = (if (page == 0 && showGlance) glanceRows else 0) +
            widgetBlockRows(pages.getOrNull(page)?.widgets ?: emptyList())
        return (rows - used).coerceAtLeast(0)
    }
    fun capacity(page: Int) = columns * iconRows(page)

    // ---- apps ----
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(p: Array<out String>, user: UserHandle, r: Boolean) = reload()
        override fun onPackagesUnavailable(p: Array<out String>, user: UserHandle, r: Boolean) = reload()
    }

    fun start() { launcherApps.registerCallback(callback); reload() }
    fun stop() = launcherApps.unregisterCallback(callback)

    fun reload() {
        scope.launch {
            val list = withContext(Dispatchers.Default) { loadApps() }
            apps.clear()
            list.forEach { apps[it.key] = it }
            syncLayout()
            if (prefs.getBoolean("pending_reflow", false)) {
                prefs.edit().remove("pending_reflow").apply()
                reflowAll()
            }
            loaded = true
        }
    }

    private fun loadApps(): List<AppEntry> {
        val me = Process.myUserHandle()
        val size = (context.resources.displayMetrics.density * 72).toInt()
        return launcherApps.getActivityList(null, me)
            .filter { it.componentName.packageName != context.packageName }
            .map { entryFor(it, size) }
            .sortedBy { it.label.lowercase() }
    }

    private fun entryFor(info: LauncherActivityInfo, size: Int): AppEntry {
        val d = info.getIcon(context.resources.displayMetrics.densityDpi)
        return AppEntry(
            key = info.componentName.flattenToString(),
            label = info.label.toString(),
            component = info.componentName,
            user = info.user,
            icon = renderTile(d, size).asImageBitmap(),
            category = info.applicationInfo.category,
            installedAt = info.firstInstallTime,
        )
    }

    private fun renderTile(d: Drawable, s: Int): Bitmap {
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inset = s / 4
        val accent = accent.toArgb()
        fun full(x: Drawable) { x.setBounds(-inset, -inset, s + inset, s + inset); x.draw(c) }
        fun padded(x: Drawable) { val p = (s * 0.18f).toInt(); x.setBounds(p, p, s - p, s - p); x.draw(c) }
        /** silhouette of the icon in one color (monochrome layer when the app has one) */
        fun mono(color: Int) {
            if (d is AdaptiveIconDrawable) {
                val m = d.monochrome?.mutate()
                if (m != null) { m.setTint(color); full(m); return }
                d.foreground?.mutate()?.let { it.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN); full(it); it.colorFilter = null }
            } else {
                val x = d.mutate(); x.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN); padded(x); x.colorFilter = null
            }
        }
        when (iconTheme) {
            IconTheme.THEMED -> { c.drawColor(0xFF23202C.toInt()); mono(lighten(accent)) }
            IconTheme.LIGHT -> { c.drawColor(0xFFF3F0F6.toInt()); mono(darken(accent)) }
            IconTheme.MONO -> { c.drawColor(0xFF19181D.toInt()); mono(0xFFF2F2F2.toInt()) }
            IconTheme.GLASS -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = LinearGradient(0f, 0f, 0f, s.toFloat(), 0x66FFFFFF, 0x22FFFFFF, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), paint)
                if (d is AdaptiveIconDrawable) d.foreground?.let { full(it) } else padded(d)
            }
            else -> {
                if (d is AdaptiveIconDrawable) {
                    d.background?.let { full(it) }
                    d.foreground?.let { full(it) }
                } else {
                    c.drawColor(0xFF2A2733.toInt())
                    padded(d)
                }
            }
        }
        return bmp
    }

    private fun lighten(c: Int): Int {
        val r = (android.graphics.Color.red(c) + 255 * 2) / 3
        val g = (android.graphics.Color.green(c) + 255 * 2) / 3
        val b = (android.graphics.Color.blue(c) + 255 * 2) / 3
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun darken(c: Int): Int = android.graphics.Color.rgb(
        android.graphics.Color.red(c) * 2 / 3, android.graphics.Color.green(c) * 2 / 3, android.graphics.Color.blue(c) * 2 / 3,
    )

    // ---- app shortcuts (long-press menu) ----
    fun shortcutsFor(key: String): List<AppShortcut> = runCatching {
        val app = apps[key] ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.component.packageName)
            .setActivity(app.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        (launcherApps.getShortcuts(q, app.user) ?: emptyList())
            .sortedBy { it.rank }
            .take(4)
            .map { AppShortcut(it.id, (it.shortLabel ?: it.longLabel ?: "").toString(), it.`package`, it.userHandle) }
            .filter { it.label.isNotBlank() }
    }.getOrDefault(emptyList())

    fun startShortcut(sc: AppShortcut) {
        runCatching { launcherApps.startShortcut(sc.pkg, sc.id, null, null, sc.user) }
    }

    // ---- launch stats for Suggestions ----
    fun recordLaunch(key: String) {
        val n = prefs.getInt("launch_$key", 0)
        prefs.edit().putInt("launch_$key", n + 1).apply()
    }
    fun suggestions(n: Int = 8): List<AppEntry> =
        visibleApps().sortedByDescending { prefs.getInt("launch_${it.key}", 0) }
            .filter { prefs.getInt("launch_${it.key}", 0) > 0 }.take(n)
    fun recentlyAdded(n: Int = 8): List<AppEntry> {
        val week = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        return visibleApps().filter { it.installedAt > week && !isSystemish(it) }
            .sortedByDescending { it.installedAt }.take(n)
    }

    // ---- layout ----
    private fun resolveDefault(intent: Intent): String? {
        val ri = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        return apps.values.firstOrNull { it.component.packageName == pkg }?.key
    }

    private fun defaultDock(): List<String> = listOfNotNull(
        resolveDefault(Intent(Intent.ACTION_DIAL)),
        resolveDefault(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
        resolveDefault(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
        resolveDefault(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
    ).distinct()

    /** The apps a fresh home screen starts with; everything else lives in the App Library. */
    private fun essentials(): List<String> {
        fun cat(c: String) = resolveDefault(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c))
        fun pkg(vararg names: String) = names.firstNotNullOfOrNull { n -> apps.values.firstOrNull { it.component.packageName == n }?.key }
        val picks = listOfNotNull(
            pkg("com.google.android.apps.photos") ?: cat(Intent.CATEGORY_APP_GALLERY),
            pkg("com.google.android.calendar") ?: cat(Intent.CATEGORY_APP_CALENDAR),
            pkg("com.google.android.deskclock", "com.android.deskclock"),
            pkg("com.google.android.gm") ?: cat(Intent.CATEGORY_APP_EMAIL),
            pkg("com.google.android.apps.maps") ?: cat(Intent.CATEGORY_APP_MAPS),
            apps.values.firstOrNull { it.label.contains("alexlmp", ignoreCase = true) }?.key ?: cat(Intent.CATEGORY_APP_MUSIC),
            pkg("com.google.android.youtube"),
            pkg("com.android.vending"),
            cat(Intent.CATEGORY_APP_FILES),
            cat(Intent.CATEGORY_APP_CALCULATOR),
            pkg("com.android.settings"),
        )
        return picks.distinct().filter { it !in dock }.take(15)
    }

    /** Apps DriftHome has already seen; only apps installed after that are added to home. */
    private fun seenApps(): Set<String>? = prefs.getStringSet("seen_apps", null)
    private fun markSeen(keys: Collection<String>) =
        prefs.edit().putStringSet("seen_apps", (seenApps() ?: emptySet()) + keys).apply()

    private fun hiddenFromHome(): Set<String> = prefs.getStringSet("hidden_home", emptySet()) ?: emptySet()

    private fun placedApps(): Set<String> =
        (dock + pages.flatMap { it.items } + folders.values.flatMap { it.apps }).toSet()

    private fun syncLayout() {
        if (dock.isEmpty() && pages.isEmpty()) restore()
        if (dock.isEmpty() && pages.isEmpty()) {
            dock.addAll(defaultDock())
            pages.add(Page())
            essentials().forEach { append(it) }
            markSeen(apps.keys)
        } else {
            dock.removeAll { !isFolder(it) && it !in apps }
            for (id in folders.keys.toList()) {
                val f = folders[id]!!
                folders[id] = f.copy(apps = f.apps.filter { it in apps })
            }
            for (i in pages.indices) {
                pages[i] = pages[i].copy(items = pages[i].items.map {
                    if (it == EMPTY || (if (isFolder(it)) it in folders else it in apps)) it else EMPTY
                }.trimEmpty())
            }
            cleanupFolders()
            val placed = placedApps()
            val hidden = hiddenFromHome() + hiddenApps
            // first run of this version: everything already installed counts as seen
            val seen = seenApps() ?: apps.keys.toSet().also { markSeen(it) }
            // newly installed apps go to the first free spot on home
            apps.keys.filter { it !in seen && it !in placed && it !in hidden }.forEach { append(it) }
            markSeen(apps.keys)
        }
        if (pages.isEmpty()) pages.add(Page())
        compact()
        save()
    }

    /** Add to the first page with room, starting from the last page. */
    private fun append(key: String) {
        if (pages.isEmpty()) pages.add(Page())
        for (p in pages.indices) {
            val items = pages[p].items
            val gap = items.indexOf(EMPTY)
            if (gap >= 0 && gap < capacity(p)) {
                pages[p] = pages[p].copy(items = items.toMutableList().also { it[gap] = key }); return
            }
            if (items.size < capacity(p)) { pages[p] = pages[p].copy(items = items + key); return }
        }
        pages.add(Page(items = listOf(key)))
    }

    /**
     * Put [key] in grid cell [index] of [page]. An empty cell just takes it; a taken cell
     * shifts the apps after it forward into the next gap (or onto the next page).
     */
    private fun placeAt(page: Int, index: Int, key: String) {
        while (pages.size <= page) pages.add(Page())
        val list = pages[page].items.toMutableList()
        while (list.size <= index) list.add(EMPTY)
        if (list[index] == EMPTY) {
            list[index] = key
        } else {
            list.add(index, key)
            val gap = list.subList(index + 1, list.size).indexOf(EMPTY)
            if (gap >= 0) list.removeAt(index + 1 + gap)
        }
        pages[page] = pages[page].copy(items = list.trimEmpty())
        spill(page)
    }

    /** Insert at a position; anything that no longer fits spills onto the next page. */
    fun insertAt(page: Int, index: Int, key: String) {
        while (pages.size <= page) pages.add(Page())
        val list = pages[page].items.toMutableList()
        list.add(index.coerceIn(0, list.size), key)
        pages[page] = pages[page].copy(items = list)
        spill(page)
    }

    private fun spill(from: Int) {
        var p = from
        while (p < pages.size) {
            val cap = capacity(p)
            var items = pages[p].items.trimEmpty()
            if (items.size > cap) {
                // close gaps at the end of the page before moving apps to the next one
                val m = items.toMutableList()
                while (m.size > cap && m.lastIndexOf(EMPTY) >= 0) m.removeAt(m.lastIndexOf(EMPTY))
                items = m
            }
            pages[p] = pages[p].copy(items = items)
            if (items.size <= cap) { p++; continue }
            val keep = items.take(cap)
            val extra = items.drop(cap)
            pages[p] = pages[p].copy(items = keep)
            if (p + 1 >= pages.size) pages.add(Page())
            pages[p + 1] = pages[p + 1].copy(items = (extra + pages[p + 1].items).trimEmpty())
            p++
        }
    }

    private fun reflowAll() {
        for (i in pages.indices) spill(i)
        compact()
        save()
    }

    private fun compact() {
        val keep = pages.map { it.copy(items = it.items.trimEmpty()) }
            .filterIndexed { i, p -> i == 0 || p.items.isNotEmpty() || p.widgets.isNotEmpty() }
        pages.clear(); pages.addAll(keep)
        if (pages.isEmpty()) pages.add(Page())
    }

    /** Remove a key from wherever it is (dock, pages, folders). */
    fun detach(key: String, source: Source) {
        when (source) {
            is Source.Dock -> dock.remove(key)
            is Source.OnPage -> if (source.page in pages.indices) {
                pages[source.page] = pages[source.page].copy(items = pages[source.page].items.gapOut(key))
            }
            is Source.InFolder -> folders[source.folder]?.let {
                folders[source.folder] = it.copy(apps = it.apps - key)
            }
            is Source.Library -> {
                // Dragging out of the App Library copies the app to home: take it off its old spot.
                prefs.edit().putStringSet("hidden_home", hiddenFromHome() - key).apply()
                if (key in dock) dock.remove(key)
                for (i in pages.indices) if (key in pages[i].items) pages[i] = pages[i].copy(items = pages[i].items.gapOut(key))
                for (id in folders.keys.toList()) folders[id]?.let { f -> if (key in f.apps) folders[id] = f.copy(apps = f.apps - key) }
            }
        }
    }

    fun moveToPage(key: String, source: Source, page: Int, index: Int) {
        detach(key, source)
        placeAt(page, index, key)
        cleanupFolders(); compact(); save()
    }

    fun moveToDock(key: String, source: Source, index: Int): Boolean {
        if (source !is Source.Dock && dock.size >= 5) return false
        detach(key, source)
        dock.add(index.coerceIn(0, dock.size), key)
        cleanupFolders(); compact(); save()
        return true
    }

    /** Drop an app onto another app (new folder) or onto a folder (add to it). */
    fun dropOnto(key: String, source: Source, page: Int, target: String) {
        if (isFolder(key) || key == target) return
        if (isFolder(target)) {
            detach(key, source)
            val f = folders[target] ?: return
            folders[target] = f.copy(apps = f.apps + key)
        } else {
            val idx = pages.getOrNull(page)?.items?.indexOf(target) ?: return
            if (idx < 0) return
            detach(key, source)
            val id = "folder:" + System.currentTimeMillis()
            folders[id] = Folder(suggestFolderName(target, key), listOf(target, key))
            val list = pages[page].items.toMutableList()
            val at = list.indexOf(target)
            if (at >= 0) list[at] = id
            pages[page] = pages[page].copy(items = list)
        }
        cleanupFolders(); compact(); save()
    }

    fun renameFolder(id: String, name: String) {
        folders[id]?.let { folders[id] = it.copy(name = name); save() }
    }

    /** Folders with 0 apps disappear; folders with 1 app turn back into that app. */
    private fun cleanupFolders() {
        for (id in folders.keys.toList()) {
            val f = folders[id] ?: continue
            if (f.apps.size >= 2) continue
            val replacement = f.apps.firstOrNull()
            folders.remove(id)
            for (i in pages.indices) {
                val items = pages[i].items
                val at = items.indexOf(id)
                if (at >= 0) {
                    val list = items.toMutableList()
                    list[at] = replacement ?: EMPTY
                    pages[i] = pages[i].copy(items = list)
                }
            }
            val d = dock.indexOf(id)
            if (d >= 0) { if (replacement != null) dock[d] = replacement else dock.removeAt(d) }
        }
    }

    private fun suggestFolderName(a: String, b: String): String {
        val ca = apps[a]?.let { categoryOf(it) }
        val cb = apps[b]?.let { categoryOf(it) }
        return if (ca != null && ca == cb && ca != "More") ca else "Folder"
    }

    fun addToHome(key: String) {
        prefs.edit().putStringSet("hidden_home", hiddenFromHome() - key).apply()
        if (key in placedApps()) return
        append(key); save()
    }

    fun removeFromHome(key: String) {
        if (!isFolder(key)) prefs.edit().putStringSet("hidden_home", hiddenFromHome() + key).apply()
        dock.remove(key)
        for (i in pages.indices) pages[i] = pages[i].copy(items = pages[i].items.gapOut(key))
        // removing a folder puts its apps back on the home screen
        folders.remove(key)?.apps?.forEach { append(it) }
        compact(); save()
    }

    fun toggleDock(key: String) {
        if (key in dock) {
            dock.remove(key); append(key)
        } else if (dock.size < 5) {
            for (i in pages.indices) pages[i] = pages[i].copy(items = pages[i].items.gapOut(key))
            dock.add(key)
        }
        compact(); save()
    }

    fun setPageItems(page: Int, items: List<String>) {
        if (page in pages.indices) { pages[page] = pages[page].copy(items = items); save() }
    }

    // ---- widgets ----
    fun addWidget(page: Int, id: Int, rows: Int) {
        while (pages.size <= page) pages.add(Page())
        val r = rows.coerceIn(1, 4)
        pages[page] = pages[page].copy(widgets = pages[page].widgets + WidgetItem(id, r))
        spill(page); save()
    }

    fun removeWidget(id: Int): Boolean {
        for (i in pages.indices) {
            if (pages[i].widgets.any { it.id == id }) {
                pages[i] = pages[i].copy(widgets = pages[i].widgets.filter { it.id != id })
                onWidgetRemoved?.invoke(id)
                compact(); save(); return true
            }
        }
        return false
    }

    /** Set by the activity so removed widgets are also released from the widget host. */
    var onWidgetRemoved: ((Int) -> Unit)? = null

    fun ensurePage(i: Int) { while (pages.size <= i) pages.add(Page()) }
    fun tidy() { cleanupFolders(); compact(); save() }

    private fun findWidget(id: Int): Pair<Int, Int>? {
        for (p in pages.indices) {
            val i = pages[p].widgets.indexOfFirst { it.id == id }
            if (i >= 0) return p to i
        }
        return null
    }

    fun resizeWidget(id: Int, rows: Int) {
        val (p, i) = findWidget(id) ?: return
        val ws = pages[p].widgets.toMutableList()
        ws[i] = ws[i].copy(rows = rows.coerceIn(1, 4))
        pages[p] = pages[p].copy(widgets = ws)
        spill(p); save()
    }

    fun toggleWidgetHalf(id: Int) {
        val (p, i) = findWidget(id) ?: return
        val ws = pages[p].widgets.toMutableList()
        ws[i] = ws[i].copy(half = !ws[i].half)
        pages[p] = pages[p].copy(widgets = ws)
        spill(p); save()
    }

    /** Move a widget up (-1) or down (+1) among the widgets on its page. */
    fun moveWidget(id: Int, dir: Int) {
        val (p, i) = findWidget(id) ?: return
        val j = i + dir
        val ws = pages[p].widgets.toMutableList()
        if (j !in ws.indices) return
        val t = ws[i]; ws[i] = ws[j]; ws[j] = t
        pages[p] = pages[p].copy(widgets = ws)
        save()
    }

    /** Move a widget to the previous (-1) or next (+1) page. */
    fun moveWidgetPage(id: Int, dir: Int) {
        val (p, i) = findWidget(id) ?: return
        val target = p + dir
        if (target < 0) return
        ensurePage(target)
        val w = pages[p].widgets[i]
        pages[p] = pages[p].copy(widgets = pages[p].widgets.filter { it.id != id })
        pages[target] = pages[target].copy(widgets = pages[target].widgets + w)
        spill(target); compact(); save()
    }

    fun allWidgetIds(): List<Int> = pages.flatMap { p -> p.widgets.map { it.id } }

    fun resetLayout() {
        prefs.edit().remove("layout").remove("hidden_home").apply()
        dock.clear(); pages.clear(); folders.clear()
        syncLayout()
    }

    // ---- persistence ----
    private fun save() {
        val o = JSONObject()
        o.put("dock", JSONArray(dock.toList()))
        o.put("pages", JSONArray(pages.map { p ->
            JSONObject()
                .put("items", JSONArray(p.items))
                .put("widgets", JSONArray(p.widgets.map { JSONObject().put("id", it.id).put("rows", it.rows).put("half", it.half) }))
        }))
        o.put("folders", JSONObject().apply {
            folders.forEach { (id, f) -> put(id, JSONObject().put("name", f.name).put("apps", JSONArray(f.apps))) }
        })
        prefs.edit().putString("layout", o.toString()).apply()
    }

    private fun restore() {
        val saved = prefs.getString("layout", null) ?: return
        runCatching {
            val o = JSONObject(saved)
            o.optJSONArray("dock")?.let { d -> for (i in 0 until d.length()) dock.add(d.getString(i)) }
            o.optJSONObject("folders")?.let { fo ->
                fo.keys().forEach { id ->
                    val f = fo.getJSONObject(id)
                    val a = f.getJSONArray("apps")
                    folders[id] = Folder(f.optString("name", "Folder"), List(a.length()) { a.getString(it) })
                }
            }
            val p = o.getJSONArray("pages")
            for (i in 0 until p.length()) {
                val e = p.get(i)
                if (e is JSONArray) { // v0.1 format
                    pages.add(Page(items = List(e.length()) { e.getString(it) }))
                } else if (e is JSONObject) {
                    val it2 = e.getJSONArray("items")
                    val w = e.optJSONArray("widgets") ?: JSONArray()
                    pages.add(Page(
                        widgets = List(w.length()) {
                            val o2 = w.getJSONObject(it)
                            WidgetItem(o2.getInt("id"), o2.getInt("rows"), o2.optBoolean("half", false))
                        },
                        items = List(it2.length()) { it2.getString(it) },
                    ))
                }
            }
        }
    }

    // ---- categories ----
    private fun isSystemish(e: AppEntry): Boolean {
        val p = e.component.packageName
        return p.startsWith("com.android") || p.startsWith("org.lineageos") ||
            p.startsWith("com.google.android") || p.startsWith("org.driftvane")
    }

    fun categoryOf(e: AppEntry): String {
        val p = e.component.packageName.lowercase()
        val l = e.label.lowercase()
        fun has(vararg w: String) = w.any { p.contains(it) || l.contains(it) }
        return when {
            e.category == ApplicationInfo.CATEGORY_SOCIAL ||
                has("telegram", "hachigram", "whatsapp", "discord", "snapchat", "instagram", "messenger",
                    "signal", "twitter", "reddit", "tiktok", "messag", "mms") -> "Social"
            e.category == ApplicationInfo.CATEGORY_AUDIO ||
                has("music", "spotify", "alexlmp", "soundcloud", "podcast", "audio", "radio") -> "Music"
            e.category == ApplicationInfo.CATEGORY_VIDEO || e.category == ApplicationInfo.CATEGORY_IMAGE ||
                has("youtube", "netflix", "video", "camera", "photo", "gallery", "twitch", "glimpse") -> "Photo & Video"
            e.category == ApplicationInfo.CATEGORY_GAME || has("game", "roblox", "minecraft") -> "Games"
            e.category == ApplicationInfo.CATEGORY_PRODUCTIVITY ||
                has("docs", "drive", "sheets", "slides", "office", "notes", "calendar", "mail", "gmail",
                    "zoom", "classroom", "notion", "files") -> "Productivity"
            e.category == ApplicationInfo.CATEGORY_MAPS || e.category == ApplicationInfo.CATEGORY_NEWS ||
                has("maps", "uber", "news", "weather") -> "Travel & News"
            has("chrome", "browser", "jelly", "firefox", "brave") -> "Browsers"
            has("bank", "pay", "wallet", "cash", "venmo", "shop", "amazon") -> "Money & Shopping"
            isSystemish(e) -> "Utilities"
            else -> "More"
        }
    }

    fun categories(): List<Pair<String, List<AppEntry>>> {
        val order = listOf("Social", "Music", "Photo & Video", "Games", "Productivity", "Browsers",
            "Money & Shopping", "Travel & News", "More", "Utilities")
        return visibleApps().sortedBy { it.label.lowercase() }.groupBy(::categoryOf).toList()
            .sortedBy { order.indexOf(it.first).let { i -> if (i < 0) 99 else i } }
    }
}
