package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import java.util.WeakHashMap

/**
 * Keeps normal Android Back navigation out of BrowserResolverActivity.
 *
 * BrowserResolverActivity is an implementation detail / explicit recovery tool,
 * not a screen the user should unexpectedly land on after watching a video.
 * The explicit recovery button may still return there when requested.
 */
object PlayerNavigationRuntime {
    private var installed = false

    @Synchronized
    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(NavigationLifecycle)
    }

    private object NavigationLifecycle : Application.ActivityLifecycleCallbacks {
        private val callbacks = WeakHashMap<PlayerActivity, OnBackPressedCallback>()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity !is PlayerActivity) return

            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    /*
                     * CLEAR_TOP removes a BrowserResolverActivity which may be
                     * underneath PlayerActivity in the same app task. SINGLE_TOP
                     * lets an existing dashboard receive the navigation cleanly.
                     */
                    activity.startActivity(
                        Intent(activity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    activity.finish()
                }
            }

            callbacks[activity] = callback
            activity.onBackPressedDispatcher.addCallback(activity, callback)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity is PlayerActivity) callbacks.remove(activity)?.remove()
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
