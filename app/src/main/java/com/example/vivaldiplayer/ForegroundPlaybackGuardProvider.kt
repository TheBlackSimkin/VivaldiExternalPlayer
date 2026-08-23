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
import java.util.WeakHashMap

/**
 * Process-local foreground playback signal used by maintenance work.
 *
 * It is intentionally tiny and conservative: it does not expose Player or media
 * state, only whether a PlayerActivity is currently resumed. Background revive
 * work can use this to defer starting another private-display resolver session
 * while the user is actively watching a video.
 */
object ForegroundPlaybackState {
    @Volatile
    private var foregroundPlayerActive: Boolean = false

    fun setPlayerForeground(active: Boolean) {
        foregroundPlayerActive = active
    }

    fun isPlayerForeground(): Boolean = foregroundPlayerActive
}

/**
 * Privacy guard for foreground-only playback.
 *
 * Unified preload may briefly foreground BackgroundPreparationActivity before
 * that excluded task moves behind the player. A small delayed check prevents
 * that internal hand-off from pausing playback if the SAME PlayerActivity has
 * already resumed. Home, lock, browser/app switching and dashboard/settings
 * navigation leave the player non-resumed, so they still pause reliably.
 *
 * 0.3.2 also makes codec ownership calmer: dashboard thumbnail warm-up belongs
 * to MainActivity only. Starting PlayerActivity must not launch background
 * FrameExtractors for every missing tab thumbnail while ExoPlayer is acquiring
 * the real video decoder.
 */
class ForegroundPlaybackGuardProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(ForegroundPlaybackGuard)
        AdaptiveQualityRuntime.install(app)
        PlayerNavigationRuntime.install(app)
        return true
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selectionArgs: Array<out String>?): Int = 0
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
    private const val PRIVACY_PAUSE_DELAY_MS = 200L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resumedPlayers = WeakHashMap<PlayerActivity, Boolean>()

    override fun onActivityResumed(activity: Activity) {
        if (activity is PlayerActivity) {
            resumedPlayers[activity] = true
            ForegroundPlaybackState.setPlayerForeground(true)
            TabRevivalCoordinator.suspendForForegroundPlayback()
            TabThumbnailCapture.pauseBackgroundCapture()
        } else if (activity is MainActivity) {
            ForegroundPlaybackState.setPlayerForeground(false)
            TabThumbnailCapture.resumeBackgroundCapture()
            TabThumbnailWarmup.warm(activity.applicationContext)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity !is PlayerActivity) return
        resumedPlayers.remove(activity)
        ForegroundPlaybackState.setPlayerForeground(false)
        schedulePrivacyPause(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity !is PlayerActivity) return
        ForegroundPlaybackState.setPlayerForeground(false)
        schedulePrivacyPause(activity)
    }

    private fun schedulePrivacyPause(activity: PlayerActivity) {
        mainHandler.postDelayed({
            if (!activity.isDestroyed && resumedPlayers[activity] != true) {
                activity.findViewById<PlayerView>(R.id.player_view)?.player?.pause()
            }
        }, PRIVACY_PAUSE_DELAY_MS)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is PlayerActivity) {
            resumedPlayers.remove(activity)
            ForegroundPlaybackState.setPlayerForeground(false)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
