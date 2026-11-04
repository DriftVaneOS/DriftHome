package org.driftvane.launcher

import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.driftvane.launcher.ui.HomeActions
import org.driftvane.launcher.ui.HomeScreen
import org.driftvane.launcher.ui.cellHeight

class HomeActivity : ComponentActivity(), HomeActions {

    private lateinit var model: LauncherModel
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private val homePresses = MutableStateFlow(0)
    private val resumes = MutableStateFlow(0)

    // widget being added: id + target page
    private var pendingWidget: Int = -1
    private var pendingPage: Int = 0

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) configureOrAdd(pendingWidget) else cancelPending()
    }
    private val configureLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == RESULT_OK) addPending() else cancelPending()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(applicationContext, HOST_ID)
        model = LauncherModel(applicationContext, lifecycleScope)
        model.onWidgetRemoved = { id -> runCatching { widgetHost.deleteAppWidgetId(id) } }
        model.start()
        setContent {
            HomeScreen(model, homePresses, resumes, this) { item, modifier -> WidgetView(item, modifier) }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { widgetHost.stopListening() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePresses.value = homePresses.value + 1
        }
    }

    override fun onResume() {
        super.onResume()
        resumes.value = resumes.value + 1
    }

    override fun onDestroy() {
        model.stop()
        super.onDestroy()
    }

    // ---------------- HomeActions ----------------

    override fun launch(key: String, bounds: Rect) {
        val app = model.apps[key] ?: return
        val la = getSystemService(LauncherApps::class.java)
        val r = android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        val opts = if (!r.isEmpty) {
            ActivityOptions.makeScaleUpAnimation(window.decorView, r.left, r.top, r.width(), r.height()).toBundle()
        } else null
        runCatching { la.startMainActivity(app.component, app.user, r, opts) }
        model.recordLaunch(key)
    }

    override fun appInfo(key: String) {
        val app = model.apps[key] ?: return
        runCatching { getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null) }
    }

    override fun uninstall(key: String) {
        val app = model.apps[key] ?: return
        runCatching { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.component.packageName))) }
    }

    override fun expandNotifications() {
        runCatching {
            val sbm = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }

    override fun openWallpaper() {
        runCatching {
            startActivity(Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun widgetProviders(): List<AppWidgetProviderInfo> = widgetManager.installedProviders

    override fun pickWidget(info: AppWidgetProviderInfo, page: Int) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidget = id
        pendingPage = page
        pendingInfo = info
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configureOrAdd(id)
        } else {
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            )
        }
    }

    private var pendingInfo: AppWidgetProviderInfo? = null

    private fun configureOrAdd(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id) ?: pendingInfo
        if (info?.configure != null) {
            runCatching {
                val sender = widgetHost.getIntentSenderForConfigureActivity(id, 0)
                configureLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
            }.onFailure { addPending() }
        } else {
            addPending()
        }
    }

    private fun addPending() {
        val id = pendingWidget
        if (id == -1) return
        val info = widgetManager.getAppWidgetInfo(id) ?: pendingInfo
        val cellPx = cellHeight(model).value * resources.displayMetrics.density
        val heightPx = info?.let {
            if (it.targetCellHeight > 0) it.targetCellHeight * cellPx else it.minHeight.toFloat()
        } ?: cellPx * 2
        val rows = kotlin.math.ceil(heightPx / cellPx).toInt().coerceIn(1, 4)
        model.addWidget(pendingPage, id, rows)
        pendingWidget = -1
        pendingInfo = null
    }

    private fun cancelPending() {
        if (pendingWidget != -1) runCatching { widgetHost.deleteAppWidgetId(pendingWidget) }
        pendingWidget = -1
        pendingInfo = null
    }

    @Composable
    private fun WidgetView(item: WidgetItem, modifier: Modifier) {
        val info = remember(item.id) { widgetManager.getAppWidgetInfo(item.id) }
        if (info == null) return
        BoxWithConstraints(modifier) {
            val density = LocalDensity.current
            val wDp = with(density) { constraints.maxWidth.toDp() }.value
            val hDp = with(density) { constraints.maxHeight.toDp() }.value
            AndroidView(
                factory = { ctx ->
                    widgetHost.createView(ctx, item.id, info).apply { setPadding(0, 0, 0, 0) }
                },
                update = { v: AppWidgetHostView ->
                    runCatching { v.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp))) }
                },
                modifier = Modifier.matchParentSize(),
            )
        }
    }

    companion object {
        private const val HOST_ID = 2026
    }
}
