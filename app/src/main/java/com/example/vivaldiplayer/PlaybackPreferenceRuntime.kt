package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import java.util.WeakHashMap

/**
 * Remembers lightweight playback preferences for one persistent tab.
 *
 * Manual quality already belongs to VideoTabStore/AdaptiveQualityRuntime. This
 * runtime adds the missing tab-scoped playback speed without changing source
 * resolution, the one-ExoPlayer architecture, or the automatic quality policy.
 */
object PlaybackPreferenceRuntime {
    private const val PREFS = "tab_playback_preferences"
    private const val SPEED_PREFIX = "speed_"
    private var installed = false

    @Synchronized
    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(PreferenceLifecycle)
    }

    private object PreferenceLifecycle : Application.ActivityLifecycleCallbacks {
        private data class Binding(val player: Player, val listener: Player.Listener)
        private val bindings = WeakHashMap<PlayerActivity, Binding>()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is PlayerActivity) activity.window.decorView.post { attach(activity) }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity is PlayerActivity) activity.window.decorView.post { attach(activity) }
        }

        private fun attach(activity: PlayerActivity) {
            if (activity.isFinishing || activity.isDestroyed) return
            val tabId = tabId(activity) ?: return
            val activePlayer = activity.findViewById<PlayerView>(R.id.player_view)?.player ?: return
            val existing = bindings[activity]
            if (existing?.player === activePlayer) return
            existing?.player?.removeListener(existing.listener)

            val savedSpeed = preferences(activity).getFloat(SPEED_PREFIX + tabId, 1.0f)
                .coerceIn(0.25f, 4.0f)
            if (kotlin.math.abs(activePlayer.playbackParameters.speed - savedSpeed) > 0.01f) {
                activePlayer.setPlaybackSpeed(savedSpeed)
            }

            val listener = object : Player.Listener {
                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    val currentTabId = tabId(activity) ?: return
                    preferences(activity).edit()
                        .putFloat(
                            SPEED_PREFIX + currentTabId,
                            playbackParameters.speed.coerceIn(0.25f, 4.0f)
                        )
                        .apply()
                }
            }
            activePlayer.addListener(listener)
            bindings[activity] = Binding(activePlayer, listener)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity is PlayerActivity) {
                bindings.remove(activity)?.let { it.player.removeListener(it.listener) }
            }
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    private fun tabId(activity: PlayerActivity): String? =
        activity.intent.getStringExtra(TabbedPlayerApplication.EXTRA_TAB_ID)
            ?.takeIf { it.isNotBlank() && VideoTabStore.get(it) != null }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
