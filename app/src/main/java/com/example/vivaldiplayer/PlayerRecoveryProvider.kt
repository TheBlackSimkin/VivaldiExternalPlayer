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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.PlayerView
import java.net.UnknownHostException
import java.util.WeakHashMap

/**
 * Registers playback recovery without changing the normal resolver/player path.
 *
 * There are deliberately two different recovery concepts:
 *
 * 1. Retry playback prepares the SAME already-resolved stream again. This is
 *    appropriate for a short network interruption.
 * 2. Refresh source re-resolves the ORIGINAL webpage URL into the SAME persistent
 *    tab. This is appropriate when a signed/temporary media URL has expired or a
 *    page may now advertise a different working manifest.
 *
 * Refresh source uses the protected service-owned private-display preparation
 * architecture established by Build #234. It does not create a new tab, does not
 * use the historical display-0 preparation Activity, and does not create a second
 * ExoPlayer.
 *
 * Decoder initialization failures are also identified here. PlayerActivity first
 * lets Media3 try its documented same-player decoder fallback; if every compatible
 * decoder still fails, these manual recovery choices remain available. We do not
 * forge frame-rate/codec metadata to force a hardware decoder to accept a stream.
 *
 * A rare HLS case can fail because a child playlist/segment host has no DNS
 * address. We recognize UnknownHostException only to explain that situation more
 * clearly. We never guess a replacement host, rewrite DNS, or bypass access rules.
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

/** Limited automatic retry plus explicit safe manual recovery choices. */
private class PlayerRecoveryController(
    private val activity: PlayerActivity
) : Player.Listener {

    private data class RecoveryAction(
        val label: String,
        val run: () -> Unit
    )

    private var player: Player? = null
    private var automaticRetryCount = 0
    private var retryScheduled = false
    private var lastFailureWasDnsLookup = false
    private var lastFailureWasDecoderInit = false

    private val recoveryButton = Button(activity).apply {
        isAllCaps = false
        text = activity.getString(R.string.recovery_options)
        visibility = View.GONE
        setOnClickListener { showRecoveryDialog() }
    }

    fun attach() {
        val activePlayer = activity.findViewById<PlayerView>(R.id.player_view)?.player ?: return
        player = activePlayer
        activePlayer.addListener(this)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply {
            marginEnd = dp(12)
            bottomMargin = dp(72)
        }
        activity.addContentView(recoveryButton, params)
    }

    fun detach() {
        player?.removeListener(this)
        player = null
    }

    override fun onPlayerError(error: PlaybackException) {
        recoveryButton.visibility = View.VISIBLE
        lastFailureWasDnsLookup = findCause<UnknownHostException>(error) != null
        lastFailureWasDecoderInit =
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED

        if (lastFailureWasDecoderInit) {
            Toast.makeText(
                activity,
                R.string.decoder_compatibility_note,
                Toast.LENGTH_LONG
            ).show()
        } else if (lastFailureWasDnsLookup) {
            /*
             * This is informational only. Retry remains useful for a temporary
             * DNS problem, and Refresh source may obtain a different legitimate
             * manifest. We intentionally do not manufacture a replacement host.
             */
            Toast.makeText(
                activity,
                R.string.media_host_dns_unavailable,
                Toast.LENGTH_LONG
            ).show()
        }

        if (
            AppSettings.networkRetryEnabled(activity) &&
            automaticRetryCount < AppSettings.MAX_TRANSIENT_RETRIES &&
            isTransient(error) &&
            !retryScheduled
        ) {
            automaticRetryCount += 1
            retryScheduled = true
            Toast.makeText(activity, R.string.temporary_network_retry, Toast.LENGTH_SHORT).show()

            activity.window.decorView.postDelayed({
                retryScheduled = false
                retrySameSource(showToast = false)
            }, 1_200L)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            recoveryButton.visibility = View.GONE
            retryScheduled = false
            automaticRetryCount = 0
            lastFailureWasDnsLookup = false
            lastFailureWasDecoderInit = false
        }
    }

    /** Retry the existing resolved URL only; do not perform a new webpage resolution. */
    private fun retrySameSource(showToast: Boolean = true) {
        val activePlayer = player ?: return
        val position = activePlayer.currentPosition.coerceAtLeast(0L)
        activePlayer.prepare()
        if (position > 0L) activePlayer.seekTo(position)
        activePlayer.playWhenReady = true
        if (showToast) Toast.makeText(activity, R.string.retrying_playback, Toast.LENGTH_SHORT).show()
    }

    private fun showRecoveryDialog() {
        val resolved = currentResolved()
        val webpageUrl = resolved?.webpageUrl.orEmpty()
        val persistentTab = currentPersistentTab()

        val actions = mutableListOf<RecoveryAction>()
        actions += RecoveryAction(activity.getString(R.string.retry_playback)) {
            retrySameSource()
        }

        /*
         * A persistent tab has the original page URL independently from the
         * temporary resolved stream URL. That is the key prerequisite for a true
         * refresh instead of repeatedly retrying an expired or dead media URL.
         */
        if (persistentTab != null && isHttpUrl(persistentTab.sourceUrl)) {
            actions += RecoveryAction(activity.getString(R.string.refresh_source)) {
                refreshSource(persistentTab)
            }
        }

        if (resolved?.resolverMode == "browser") {
            /* Existing explicit fallback: return to the candidate/resolver screen underneath. */
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

        AlertDialog.Builder(activity)
            .setTitle(R.string.recovery_options)
            .setMessage(
                when {
                    lastFailureWasDecoderInit -> R.string.decoder_compatibility_note
                    lastFailureWasDnsLookup -> R.string.recovery_dns_explanation
                    else -> R.string.recovery_explanation
                }
            )
            .setItems(actions.map { it.label }.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.run?.invoke()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Re-resolve the original page URL into the SAME tab using the protected
     * #234 private-display service path.
     *
     * One subtle but important detail: TabbedPlayerApplication normally saves
     * PlayerActivity.currentResolved during onPause. If we left the stale object
     * there, that lifecycle save could mark this tab READY again with the expired
     * payload immediately after we mark it QUEUED. Clear only the Activity's
     * in-memory currentResolved reference before navigation; the persistent tab
     * still retains its sourceUrl and playback position while the fresh resolver
     * works.
     */
    private fun refreshSource(tab: VideoTabStore.VideoTab) {
        val sourceUrl = tab.sourceUrl.trim()
        if (!isHttpUrl(sourceUrl)) return

        val activePlayer = player
        val position = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: tab.positionMs
        val desiredPlayState = activePlayer?.playWhenReady ?: tab.playWhenReady

        VideoTabStore.updatePlayback(
            id = tab.id,
            positionMs = position,
            playWhenReady = desiredPlayState
        )
        clearActivityResolvedPayloadForRefresh()

        TabPreparationManager.cancelScheduled(activity.applicationContext, tab.id)
        VideoTabStore.markQueued(tab.id)
        VideoTabStore.markTechnicalStage(tab.id, "SOURCE_REFRESH_PRIVATE_REQUESTED")

        val token = "refresh-${tab.id}-${System.currentTimeMillis()}"
        OperationLog.record(
            activity,
            event = "SOURCE_REFRESH_PRIVATE_SERVICE_REQUESTED",
            tabId = tab.id,
            detail = "token=$token"
        )

        BackgroundPreparationKeepAliveService.acquire(
            context = activity.applicationContext,
            token = token,
            tabId = tab.id,
            sourceUrl = sourceUrl
        )

        Toast.makeText(activity, R.string.refreshing_source, Toast.LENGTH_SHORT).show()

        /* Return to the dashboard while the same tab is refreshed privately. */
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

    private fun isTransient(error: PlaybackException): Boolean {
        if (
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        ) {
            return true
        }

        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode == 429 || cause.responseCode in 500..599
            }
            cause = cause.cause
        }
        return false
    }

    private inline fun <reified T : Throwable> findCause(error: Throwable): T? {
        var current: Throwable? = error
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
