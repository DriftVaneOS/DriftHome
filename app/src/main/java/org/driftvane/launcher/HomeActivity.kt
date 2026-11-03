package org.driftvane.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.driftvane.launcher.ui.HomeActions
import org.driftvane.launcher.ui.HomeScreen

class HomeActivity : ComponentActivity(), HomeActions {

    private lateinit var model: LauncherModel
    private val homePresses = MutableStateFlow(0)
    private val resumes = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        model = LauncherModel(applicationContext, lifecycleScope)
        model.start()
        setContent { HomeScreen(model, homePresses, resumes, this) }
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

    override fun launch(key: String, bounds: Rect) {
        val app = model.apps[key] ?: return
        val la = getSystemService(LauncherApps::class.java)
        val r = android.graphics.Rect(
            bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()
        )
        val opts = if (!r.isEmpty) {
            // The app grows out of its icon.
            ActivityOptions.makeScaleUpAnimation(window.decorView, r.left, r.top, r.width(), r.height()).toBundle()
        } else null
        runCatching { la.startMainActivity(app.component, app.user, r, opts) }
    }

    override fun appInfo(key: String) {
        val app = model.apps[key] ?: return
        runCatching {
            getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(app.component, app.user, null, null)
        }
    }

    override fun uninstall(key: String) {
        val app = model.apps[key] ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.component.packageName)))
        }
    }
}
