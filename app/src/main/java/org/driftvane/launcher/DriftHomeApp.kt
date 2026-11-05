package org.driftvane.launcher

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Crash guard. If DriftHome crashes twice within a minute, the saved home layout is the most
 * likely cause (a bad widget, a broken folder), so it's cleared and the next start rebuilds a
 * clean home screen instead of crash-looping. Settings like icon shape are kept.
 */
class DriftHomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val guard = getSharedPreferences("crash_guard", Context.MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val now = System.currentTimeMillis()
                val last = guard.getLong("last", 0L)
                if (now - last < 60_000L) {
                    getSharedPreferences("drifthome", Context.MODE_PRIVATE).edit()
                        .remove("layout").remove("hidden_home").commit()
                    guard.edit().putLong("last", 0L).commit()
                } else {
                    guard.edit().putLong("last", now).commit()
                }
            }
            previous?.uncaughtException(t, e)
        }
        // 30 seconds without a crash: we're stable, forget the last one.
        Handler(Looper.getMainLooper()).postAtTime({
            guard.edit().putLong("last", 0L).apply()
        }, SystemClock.uptimeMillis() + 30_000L)
    }
}
