package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import java.net.UnknownHostException
import java.util.WeakHashMap

/**
 * Registers playback recovery without changing the normal resolver/player path.
 *
 * Refresh source re-resolves the original webpage URL into the same persistent
 * tab through [TabMaintenanceController], exactly like dashboard Revive.
 *
 * Candidate 4 proved direct Refresh from Player works on device, while retrying
 * the same already-failed media source did not provide reliable recovery. The
 * user-facing failed-player recovery path therefore exposes the useful action
 * directly and keeps explanatory options secondary.
 */
class PlayerRecoveryProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(PlayerRecoveryLifecycle)
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

private object PlayerRecoveryLifecycle : Application.ActivityLifecycleCallbacks {
    private val controllers = WeakHashMap<PlayerActivity, PlayerRecoveryController>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is PlayerActivity) return
        activity.window.decorView.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                controllers.getOrPut(activity) { PlayerRecoveryController(activity) }.attach()
            }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is PlayerActivity) controllers.remove(activity)?.detach()
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

private class PlayerRecoveryController(
    private val activity: PlayerActivity
) : Player.Listener {

    private data class RecoveryAction(val label: String, val run: () -> Unit)

    private var player: Player? = null
    private var lastFailureWasDnsLookup = false
    private var lastFailureWasDecoderInit = false
    private var recoveryContainer: LinearLayout? = null

    private val refreshButton = Button(activity).apply {
        isAllCaps = false
        text = activity.getString(R.string.refresh_source)
        setOnClickListener {
            val tab = currentPersistentTab()
            if (tab != null && isHttpUrl(TabOriginStore.pageUrl(activity, tab))) {
                refreshSource(tab)
            } else {
                Toast.makeText(activity, R.string.original_webpage_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val recoveryButton = Button(activity).apply {
        isAllCaps = false
        text = activity.getString(R.string.recovery_options)
        setOnClickListener { showRecoveryDialog() }
    }

    fun attach() {
        val activePlayer = activity.findViewById<PlayerView>(R.id.player_view)?.player ?: return
        if (player === activePlayer && recoveryContainer?.parent != null) return

        player?.removeListener(this)
        player = activePlayer
        activePlayer.addListener(this)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            visibility = View.GONE
            addView(refreshButton, buttonLayoutParams())
            addView(recoveryButton, buttonLayoutParams())
        }
        recoveryContainer = container

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply {
            marginEnd = dp(12)
            bottomMargin = dp(72)
        }
        activity.addContentView(container, params)
    }

    fun detach() {
        player?.removeListener(this)
        player = null
        (recoveryContainer?.parent as? FrameLayout)?.removeView(recoveryContainer)
        recoveryContainer = null
    }

    override fun onPlayerError(error: PlaybackException) {
        lastFailureWasDnsLookup = findCause<UnknownHostException>(error) != null
        lastFailureWasDecoderInit = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        showDirectRecoveryControls()

        if (lastFailureWasDecoderInit) {
            Toast.makeText(activity, R.string.decoder_compatibility_note, Toast.LENGTH_LONG).show()
        } else if (lastFailureWasDnsLookup) {
            Toast.makeText(activity, R.string.media_host_dns_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            recoveryContainer?.visibility = View.GONE
            lastFailureWasDnsLookup = false
            lastFailureWasDecoderInit = false
        }
    }

    private fun showDirectRecoveryControls() {
        refreshButton.visibility = if (currentPersistentTab()
                ?.let { isHttpUrl(TabOriginStore.pageUrl(activity, it)) } == true
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        recoveryContainer?.visibility = View.VISIBLE
    }

    private fun showRecoveryDialog() {
        val resolved = currentResolved()
        val webpageUrl = resolved?.webpageUrl.orEmpty()
        val persistentTab = currentPersistentTab()

        val actions = mutableListOf<RecoveryAction>()

        if (persistentTab != null && isHttpUrl(TabOriginStore.pageUrl(activity, persistentTab))) {
            actions += RecoveryAction(activity.getString(R.string.refresh_source)) {
                refreshSource(persistentTab)
            }
        }

        if (resolved?.resolverMode == "browser") {
            actions += RecoveryAction(activity.getString(R.string.try_another_detected_video)) {
                activity.finish()
            }
        } else if (isHttpUrl(webpageUrl)) {
            actions += RecoveryAction(activity.getString(R.string.try_browser_method)) {
                activity.startActivity(
                    Intent(activity, BrowserResolverActivity::class.java)
                        .putExtra(BrowserResolverActivity.EXTRA_URL, webpageUrl)
                )
                activity.finish()
            }
        }

        val explanationRes = when {
            lastFailureWasDecoderInit -> R.string.decoder_compatibility_note
            lastFailureWasDnsLookup -> R.string.recovery_dns_explanation
            else -> R.string.recovery_explanation
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(4))
            addView(TextView(activity).apply {
                text = activity.getString(explanationRes)
                setPadding(0, dp(8), 0, dp(12))
            })
        }

        lateinit var dialog: AlertDialog
        actions.forEach { action ->
            content.addView(Button(activity).apply {
                isAllCaps = false
                text = action.label
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener {
                    dialog.dismiss()
                    action.run()
                }
            })
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.recovery_options)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    /**
     * Use the SAME revival implementation as the dashboard.
     *
     * Clear PlayerActivity's stale in-memory payload first so its onPause save
     * cannot race the newly queued state back to READY. The controller persists
     * playback position/state, resolves the permanent original page URL, and
     * queues the same persistent tab through the protected private-display path.
     */
    private fun refreshSource(tab: VideoTabStore.VideoTab) {
        val activePlayer = player
        val position = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: tab.positionMs
        val desiredPlayState = activePlayer?.playWhenReady ?: tab.playWhenReady

        clearActivityResolvedPayloadForRefresh()
        val queued = TabMaintenanceController.reviveFromPlayer(
            context = activity,
            tab = tab,
            positionMs = position,
            playWhenReady = desiredPlayState
        )

        if (!queued) {
            Toast.makeText(activity, R.string.original_webpage_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(activity, R.string.refreshing_source, Toast.LENGTH_SHORT).show()
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        activity.finish()
    }

    private fun currentPersistentTab(): VideoTabStore.VideoTab? {
        val tabId = activity.intent
            .getStringExtra(TabbedPlayerApplication.EXTRA_TAB_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return VideoTabStore.get(tabId)
    }

    private fun currentResolved(): ResolvedMedia? = runCatching {
        val field = PlayerActivity::class.java.getDeclaredField("currentResolved")
        field.isAccessible = true
        field.get(activity) as? ResolvedMedia
    }.getOrNull()

    private fun clearActivityResolvedPayloadForRefresh() {
        runCatching {
            val field = PlayerActivity::class.java.getDeclaredField("currentResolved")
            field.isAccessible = true
            field.set(activity, null)
        }
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)

    private inline fun <reified T : Throwable> findCause(error: Throwable): T? {
        var current: Throwable? = error
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun buttonLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(4)
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
