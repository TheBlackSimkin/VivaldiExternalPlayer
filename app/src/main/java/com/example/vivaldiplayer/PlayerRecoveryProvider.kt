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
import java.util.WeakHashMap

/** Registers a playback-recovery listener without changing source-selection logic. */
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
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
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

/** Limited automatic retry plus safe manual recovery choices. */
private class PlayerRecoveryController(
    private val activity: PlayerActivity
) : Player.Listener {

    private var player: Player? = null
    private var automaticRetryCount = 0
    private var retryScheduled = false

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
        }
    }

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

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.recovery_options)
            .setMessage(R.string.recovery_explanation)
            .setPositiveButton(R.string.retry_same_source) { _, _ -> retrySameSource() }
            .setNegativeButton(R.string.cancel, null)

        if (resolved?.resolverMode == "browser") {
            /* Return to the existing resolver/candidate list already underneath the player. */
            builder.setNeutralButton(R.string.try_another_detected_video) { _, _ ->
                activity.finish()
            }
        } else if (webpageUrl.startsWith("http://") || webpageUrl.startsWith("https://")) {
            builder.setNeutralButton(R.string.try_browser_method) { _, _ ->
                activity.startActivity(
                    Intent(activity, BrowserResolverActivity::class.java)
                        .putExtra(BrowserResolverActivity.EXTRA_URL, webpageUrl)
                )
                activity.finish()
            }
        }

        builder.show()
    }

    private fun currentResolved(): ResolvedMedia? = runCatching {
        val field = PlayerActivity::class.java.getDeclaredField("currentResolved")
        field.isAccessible = true
        field.get(activity) as? ResolvedMedia
    }.getOrNull()

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

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
