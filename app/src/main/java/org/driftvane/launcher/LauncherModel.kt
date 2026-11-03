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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
)

/** AlexLMP accent colors. */
val Accents = listOf(
    Color(0xFFFF4D5E), // coral
    Color(0xFF8CD0F0), // sky
    Color(0xFFF09CE2), // pink
    Color(0xFFFFB59C), // peach
    Color(0xFFFF8A3D), // orange
)

class LauncherModel(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val prefs = context.getSharedPreferences("drifthome", Context.MODE_PRIVATE)

    val apps = mutableStateMapOf<String, AppEntry>()
    val dock = mutableStateListOf<String>()
    val pages = mutableStateListOf<List<String>>()
    var loaded by mutableStateOf(false)
        private set

    // Settings
    var accentIndex by mutableStateOf(prefs.getInt("accent", 0))
        private set
    var showLabels by mutableStateOf(prefs.getBoolean("labels", true))
        private set
    var iconScale by mutableStateOf(prefs.getFloat("icon_scale", 1f))
        private set
    var columns by mutableStateOf(prefs.getInt("columns", 4))
        private set

    val accent: Color get() = Accents[accentIndex.coerceIn(0, Accents.lastIndex)]
    val rows = 6
    fun capacity(page: Int) = columns * (if (page == 0) 4 else rows)

    fun setAccent(i: Int) { accentIndex = i; prefs.edit().putInt("accent", i).apply() }
    fun setLabels(v: Boolean) { showLabels = v; prefs.edit().putBoolean("labels", v).apply() }
    fun changeIconScale(v: Float) { iconScale = v; prefs.edit().putFloat("icon_scale", v).apply() }
    fun changeColumns(v: Int) {
        columns = v
        prefs.edit().putInt("columns", v).apply()
        reflow()
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(p: Array<out String>, user: UserHandle, r: Boolean) = reload()
        override fun onPackagesUnavailable(p: Array<out String>, user: UserHandle, r: Boolean) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback)
        reload()
    }

    fun stop() = launcherApps.unregisterCallback(callback)

    fun reload() {
        scope.launch {
            val list = withContext(Dispatchers.Default) { loadApps() }
            apps.clear()
            list.forEach { apps[it.key] = it }
            syncLayout()
            loaded = true
        }
    }

    private fun loadApps(): List<AppEntry> {
        val me = Process.myUserHandle()
        val size = (context.resources.displayMetrics.density * 72).toInt()
        return launcherApps.getActivityList(null, me)
            .filter { it.componentName.packageName != context.packageName }
            .map { info -> entryFor(info, size) }
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
        )
    }

    /** Full-bleed tile: adaptive icons fill the whole squircle, legacy icons sit on a glass tile. */
    private fun renderTile(d: Drawable, s: Int): Bitmap {
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            val inset = s / 4
            d.background?.let { it.setBounds(-inset, -inset, s + inset, s + inset); it.draw(c) }
            d.foreground?.let { it.setBounds(-inset, -inset, s + inset, s + inset); it.draw(c) }
        } else {
            c.drawColor(0xFF2A2733.toInt())
            val pad = (s * 0.16f).toInt()
            d.setBounds(pad, pad, s - pad, s - pad)
            d.draw(c)
        }
        return bmp
    }

    // ---------------- layout ----------------

    private fun resolveDefault(intent: Intent): String? {
        val ri = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        return apps.values.firstOrNull { it.component.packageName == pkg }?.key
    }

    private fun defaultDock(): List<String> = listOfNotNull(
        resolveDefault(Intent(Intent.ACTION_DIAL)),
        resolveDefault(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
        resolveDefault(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
        resolveDefault(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
    ).distinct()

    private fun syncLayout() {
        val saved = prefs.getString("layout", null)
        if (dock.isEmpty() && pages.isEmpty() && saved != null) {
            runCatching {
                val o = JSONObject(saved)
                val d = o.getJSONArray("dock")
                for (i in 0 until d.length()) dock.add(d.getString(i))
                val p = o.getJSONArray("pages")
                for (i in 0 until p.length()) {
                    val a = p.getJSONArray(i)
                    pages.add(List(a.length()) { a.getString(it) })
                }
            }
        }
        if (dock.isEmpty() && pages.isEmpty()) {
            dock.addAll(defaultDock())
            val rest = apps.keys.filter { it !in dock }.sortedBy { apps[it]!!.label.lowercase() }
            pages.addAll(distribute(rest))
        } else {
            // drop removed apps, append new ones
            dock.removeAll { it !in apps }
            val cleaned = pages.map { pg -> pg.filter { it in apps } }
            pages.clear()
            pages.addAll(cleaned)
            val placed = (dock + pages.flatten()).toSet()
            val hidden = hiddenFromHome()
            val fresh = apps.keys.filter { it !in placed && it !in hidden }
            fresh.forEach { addToHome(it, save = false) }
            compact()
        }
        if (pages.isEmpty()) pages.add(emptyList())
        save()
    }

    private fun distribute(keys: List<String>): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var i = 0
        var page = 0
        while (i < keys.size) {
            val cap = capacity(page)
            out.add(keys.subList(i, minOf(keys.size, i + cap)).toList())
            i += cap
            page++
        }
        return out
    }

    private fun reflow() {
        val all = pages.flatten()
        pages.clear()
        pages.addAll(distribute(all))
        if (pages.isEmpty()) pages.add(emptyList())
        save()
    }

    private fun compact() {
        val keep = pages.filterIndexed { i, p -> i == 0 || p.isNotEmpty() }
        pages.clear()
        pages.addAll(keep)
    }

    private fun hiddenFromHome(): Set<String> =
        prefs.getStringSet("hidden_home", emptySet()) ?: emptySet()

    fun addToHome(key: String, save: Boolean = true) {
        prefs.edit().putStringSet("hidden_home", hiddenFromHome() - key).apply()
        if (key in dock || pages.any { key in it }) return
        val last = pages.lastIndex
        if (last >= 0 && pages[last].size < capacity(last)) {
            pages[last] = pages[last] + key
        } else {
            pages.add(listOf(key))
        }
        if (save) save()
    }

    fun removeFromHome(key: String) {
        prefs.edit().putStringSet("hidden_home", hiddenFromHome() + key).apply()
        dock.remove(key)
        for (i in pages.indices) if (key in pages[i]) pages[i] = pages[i] - key
        compact()
        if (pages.isEmpty()) pages.add(emptyList())
        save()
    }

    fun setPage(index: Int, keys: List<String>) {
        if (index in pages.indices) {
            pages[index] = keys
            save()
        }
    }

    fun toggleDock(key: String) {
        if (key in dock) {
            dock.remove(key)
            addToHome(key, save = false)
        } else if (dock.size < 5) {
            for (i in pages.indices) if (key in pages[i]) pages[i] = pages[i] - key
            dock.add(key)
            compact()
        }
        save()
    }

    fun resetLayout() {
        prefs.edit().remove("layout").remove("hidden_home").apply()
        dock.clear()
        pages.clear()
        syncLayout()
    }

    private fun save() {
        val o = JSONObject()
        o.put("dock", JSONArray(dock.toList()))
        o.put("pages", JSONArray(pages.map { JSONArray(it) }))
        prefs.edit().putString("layout", o.toString()).apply()
    }

    // ---------------- categories for the App Shelf ----------------

    fun shelfGroups(): List<Pair<String, List<AppEntry>>> {
        val all = apps.values.sortedBy { it.label.lowercase() }
        fun cat(e: AppEntry) = when (e.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE -> "Music & Media"
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_NEWS -> "Travel & News"
            else -> if (e.component.packageName.startsWith("com.android") ||
                e.component.packageName.startsWith("org.lineageos") ||
                e.component.packageName.startsWith("com.google.android")
            ) "System" else "More"
        }
        val order = listOf("Social", "Music & Media", "Productivity", "Games", "Travel & News", "More", "System")
        return all.groupBy(::cat).toList().sortedBy { order.indexOf(it.first) }
    }
}
