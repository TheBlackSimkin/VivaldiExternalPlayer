package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.ui.PlayerView

/**
 * Privacy guard which guarantees that PlayerActivity cannot continue audio/video
 * after Android moves it out of the foreground.
 *
 * The pause is posted to the main queue from onActivityPaused. This lets the tab
 * coordinator first save the user's foreground play intention, then applies the
 * automatic privacy pause. Returning to the same tab can therefore restore the
 * saved intention without confusing it with a deliberate user pause.
 */
class ForegroundPlaybackGuardProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(ForegroundPlaybackGuard)

        /*
         * Reuse this already-registered process entry point to install the
         * adaptive quality switching correction without another manifest
         * provider. The quality helper changes only Media3 track selection.
         */
        AdaptiveQualityRuntime.install(app)
        return true
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null
}

private object ForegroundPlaybackGuard : Application.ActivityLifecycleCallbacks {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onActivityPaused(activity: Activity) {
        if (activity !is PlayerActivity) return

        /*
         * Run after all synchronous onActivityPaused callbacks. The persistent
         * tab coordinator has then captured position and playWhenReady already.
         */
        mainHandler.post {
            if (!activity.isDestroyed) {
                activity.findViewById<PlayerView>(R.id.player_view)?.player?.pause()
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity !is PlayerActivity) return

        // Belt-and-suspenders enforcement for Home/app switch/lock transitions.
        activity.findViewById<PlayerView>(R.id.player_view)?.player?.pause()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
