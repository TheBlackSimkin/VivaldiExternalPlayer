package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import java.net.UnknownHostException
import java.util.WeakHashMap

/** Failed-player recovery UI and same-tab source refresh coordinator. */
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

private class PlayerRecoveryController(private val activity: PlayerActivity) : Player.Listener {
    private data class RecoveryAction(val label: String, val run: () -> Unit)

    private var player: Player? = null
    private var lastFailureWasDnsLookup = false
    private var lastFailureWasDecoderInit = false
    private var recoveryContainer: LinearLayout? = null
    private lateinit var messageText: TextView
    private lateinit var refreshButton: Button
    private lateinit var dashboardButton: Button

    private var refreshingTabId: String? = null
    private var refreshPositionMs: Long = 0L
    private var refreshPlayWhenReady: Boolean = true

    private val refreshPollRunnable = Runnable { pollRefreshedTab() }

    fun attach() {
        val activePlayer = activity.findViewById<PlayerView>(R.id.player_view)?.player ?: return
        if (player === activePlayer && recoveryContainer?.parent != null) return
        player?.removeListener(this)
        player = activePlayer
        activePlayer.addListener(this)

        messageText = TextView(activity).apply {
            textSize = 13f
            setTextColor(color(R.color.app_text_secondary))
            setPadding(0, dp(5), 0, dp(10))
        }

        refreshButton = Button(activity).apply {
            isAllCaps = false
            text = activity.getString(R.string.refresh_source)
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_accent))
            setTextColor(color(R.color.white))
            setOnClickListener {
                val tab = currentPersistentTab()
                if (tab != null && isHttpUrl(TabOriginStore.pageUrl(activity, tab))) {
                    refreshSourceInPlayer(tab)
                } else {
                    Toast.makeText(activity, R.string.original_webpage_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }

        val technicalButton = Button(activity).apply {
            isAllCaps = false
            text = activity.getString(R.string.technical_details)
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setTextColor(color(R.color.app_text_primary))
            setOnClickListener {
                activity.findViewById<Button>(R.id.diagnostics_button)?.performClick()
            }
        }

        val moreButton = Button(activity).apply {
            isAllCaps = false
            text = activity.getString(R.string.recovery_options)
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setTextColor(color(R.color.app_text_secondary))
            setOnClickListener { showRecoveryDialog() }
        }

        dashboardButton = Button(activity).apply {
            isAllCaps = false
            text = activity.getString(R.string.refresh_view_dashboard)
            visibility = View.GONE
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setTextColor(color(R.color.app_text_primary))
            setOnClickListener { openDashboard() }
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(color(R.color.app_surface))
                setStroke(dp(1), color(R.color.app_outline))
            }
            addView(TextView(activity).apply {
                text = activity.getString(R.string.playback_failed_short)
                textSize = 17f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(color(R.color.app_text_primary))
            })
            addView(messageText)
            addView(refreshButton, fullButtonParams())
            addView(technicalButton, fullButtonParams())
            addView(moreButton, fullButtonParams())
            addView(dashboardButton, fullButtonParams())
        }
        recoveryContainer = container

        activity.addContentView(container, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(72)
        })
    }

    fun detach() {
        activity.window.decorView.removeCallbacks(refreshPollRunnable)
        player?.removeListener(this)
        player = null
        (recoveryContainer?.parent as? ViewGroup)?.removeView(recoveryContainer)
        recoveryContainer = null
    }

    override fun onPlayerError(error: PlaybackException) {
        if (refreshingTabId != null) return
        lastFailureWasDnsLookup = findCause<UnknownHostException>(error) != null
        lastFailureWasDecoderInit = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        messageText.text = when {
            lastFailureWasDecoderInit -> activity.getString(R.string.decoder_compatibility_note)
            lastFailureWasDnsLookup -> activity.getString(R.string.recovery_dns_explanation)
            else -> activity.getString(R.string.recovery_refresh_explanation)
        }
        showPanel()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY && refreshingTabId == null) {
            recoveryContainer?.visibility = View.GONE
            lastFailureWasDnsLookup = false
            lastFailureWasDecoderInit = false
        }
    }

    private fun showPanel() {
        val canRefresh = currentPersistentTab()?.let { isHttpUrl(TabOriginStore.pageUrl(activity, it)) } == true
        refreshButton.visibility = if (canRefresh) View.VISIBLE else View.GONE
        refreshButton.isEnabled = canRefresh
        dashboardButton.visibility = View.GONE
        recoveryContainer?.visibility = View.VISIBLE
    }

    private fun refreshSourceInPlayer(tab: VideoTabStore.VideoTab) {
        if (refreshingTabId != null) return
        val activePlayer = player
        refreshPositionMs = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: tab.positionMs
        refreshPlayWhenReady = activePlayer?.playWhenReady ?: tab.playWhenReady
        activePlayer?.pause()

        val queued = TabMaintenanceController.reviveFromPlayer(
            context = activity,
            tab = tab,
            positionMs = refreshPositionMs,
            playWhenReady = refreshPlayWhenReady
        )
        if (!queued) {
            Toast.makeText(activity, R.string.original_webpage_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        clearActivityResolvedPayloadForRefresh()
        refreshingTabId = tab.id
        refreshButton.isEnabled = false
        refreshButton.text = activity.getString(R.string.refreshing_source_in_player)
        messageText.text = activity.getString(R.string.refresh_source_waiting)
        dashboardButton.visibility = View.GONE
        recoveryContainer?.visibility = View.VISIBLE
        activity.findViewById<TextView>(R.id.playback_status)?.apply {
            text = activity.getString(R.string.refreshing_source_in_player)
            visibility = View.VISIBLE
        }
        activity.window.decorView.removeCallbacks(refreshPollRunnable)
        activity.window.decorView.postDelayed(refreshPollRunnable, 500L)
    }

    private fun pollRefreshedTab() {
        val tabId = refreshingTabId ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val tab = VideoTabStore.get(tabId)

        when (tab?.preparationState) {
            VideoTabStore.PreparationState.READY -> {
                val resolved = runCatching { ResolvedMedia.fromJson(tab.resolvedMediaJson) }.getOrNull()
                if (resolved != null && loadRefreshedSource(resolved)) {
                    refreshingTabId = null
                    refreshButton.text = activity.getString(R.string.refresh_source)
                    refreshButton.isEnabled = true
                    dashboardButton.visibility = View.GONE
                    PlayerTitleRuntime.update(activity, resolved.title)
                    activity.findViewById<TextView>(R.id.playback_status)?.visibility = View.GONE
                    return
                }
                finishRefreshFailure()
            }

            VideoTabStore.PreparationState.ERROR,
            VideoTabStore.PreparationState.NEEDS_ATTENTION,
            null -> finishRefreshFailure()

            VideoTabStore.PreparationState.QUEUED,
            VideoTabStore.PreparationState.RESOLVING ->
                activity.window.decorView.postDelayed(refreshPollRunnable, 750L)
        }
    }

    private fun finishRefreshFailure() {
        refreshingTabId = null
        refreshButton.text = activity.getString(R.string.refresh_source)
        refreshButton.isEnabled = true
        messageText.text = activity.getString(R.string.refresh_source_failed)
        dashboardButton.visibility = View.VISIBLE
        recoveryContainer?.visibility = View.VISIBLE
    }

    private fun loadRefreshedSource(resolved: ResolvedMedia): Boolean = runCatching {
        val method = PlayerActivity::class.java.declaredMethods.firstOrNull {
            it.name == "loadResolvedMedia" && it.parameterTypes.size == 3
        } ?: return@runCatching false
        method.isAccessible = true
        method.invoke(activity, resolved, refreshPositionMs, refreshPlayWhenReady)
        true
    }.getOrDefault(false)

    private fun showRecoveryDialog() {
        val resolved = currentResolved()
        val webpageUrl = resolved?.webpageUrl.orEmpty()
        val persistentTab = currentPersistentTab()
        val actions = mutableListOf<RecoveryAction>()

        if (persistentTab != null && isHttpUrl(TabOriginStore.pageUrl(activity, persistentTab))) {
            actions += RecoveryAction(activity.getString(R.string.refresh_source)) { refreshSourceInPlayer(persistentTab) }
        }
        if (resolved?.resolverMode == "browser") {
            actions += RecoveryAction(activity.getString(R.string.try_another_detected_video)) { openDashboard() }
        } else if (isHttpUrl(webpageUrl)) {
            actions += RecoveryAction(activity.getString(R.string.try_browser_method)) {
                activity.startActivity(Intent(activity, BrowserResolverActivity::class.java)
                    .putExtra(BrowserResolverActivity.EXTRA_URL, SourceLanguagePolicy.preferAppLanguage(activity, webpageUrl)))
                activity.finish()
            }
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.recovery_options)
            .setItems(actions.map { it.label }.toTypedArray()) { _, which -> actions.getOrNull(which)?.run?.invoke() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openDashboard() {
        activity.startActivity(Intent(activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        activity.finish()
    }

    private fun currentPersistentTab(): VideoTabStore.VideoTab? {
        val tabId = activity.intent.getStringExtra(TabbedPlayerApplication.EXTRA_TAB_ID)?.takeIf { it.isNotBlank() } ?: return null
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

    private fun isHttpUrl(value: String): Boolean = value.startsWith("https://", true) || value.startsWith("http://", true)

    private inline fun <reified T : Throwable> findCause(error: Throwable): T? {
        var current: Throwable? = error
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun fullButtonParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(44)
    ).apply { topMargin = dp(5) }

    private fun color(resId: Int): Int = ContextCompat.getColor(activity, resId)
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
