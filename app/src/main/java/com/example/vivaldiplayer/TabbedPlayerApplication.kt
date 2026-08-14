package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.chaquo.python.android.PyApplication
import java.util.WeakHashMap

/** Application-level persistent-tab coordinator. */
class TabbedPlayerApplication : PyApplication(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val EXTRA_TAB_ID = "video_tab_id"
        private const val TAB_BUTTON_TAG = "vivaldi_external_player_tabs_button"
    }

    private val activityTabs = WeakHashMap<Activity, String>()
    private var lastBrowserPageTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        VideoTabStore.initialize(this)
        TabPreparationManager.resumePending(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is BackgroundPreparationActivity) {
            UnifiedPreparationCoordinator.onPreparationCreated(activity)
            return
        }
        if (activity !is PlayerActivity) return

        val suppliedTabId = activity.intent.getStringExtra(EXTRA_TAB_ID)
        val originalJson = activity.intent.getStringExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA)
            ?: return
        val tabJson = withLocalBrowserTitle(originalJson)

        val tabId = if (suppliedTabId != null && VideoTabStore.get(suppliedTabId) != null) {
            VideoTabStore.markReady(suppliedTabId, tabJson)
            suppliedTabId
        } else {
            VideoTabStore.createTab(tabJson).id
        }

        activity.intent.putExtra(EXTRA_TAB_ID, tabId)
        activityTabs[activity] = tabId
        activity.window.decorView.post { attachTabButton(activity) }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is PlayerActivity) return

        UnifiedPreparationCoordinator.onHostResumed(activity)

        val tabId = activityTabs[activity] ?: return
        val tab = VideoTabStore.get(tabId) ?: return
        updateTabButton(activity)

        activity.window.decorView.post {
            activity.restoreTabSession(tab.positionMs, tab.playWhenReady)
        }

        activity.window.decorView.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                TabThumbnailCapture.captureIfMissing(activity, tabId)
            }
        }, 1_800L)

        TabPreparationManager.preloadNext(activity, tabId)
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity is PlayerActivity) UnifiedPreparationCoordinator.onHostPaused(activity)

        when (activity) {
            is BrowserResolverActivity -> captureBrowserPageTitle(activity)
            is PlayerActivity -> saveActivityTab(activity)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is BackgroundPreparationActivity) {
            val tabId = activity.intent
                .getStringExtra(BackgroundPreparationActivity.EXTRA_TAB_ID)
                ?.takeIf { it.isNotBlank() }

            UnifiedPreparationCoordinator.onPreparationDestroyed(activity)

            tabId?.let { id ->
                val tab = VideoTabStore.get(id)

                when {
                    /*
                     * A successful BG share should be as identifiable as possible
                     * before the user later opens the dashboard. The hidden WebView
                     * already saved the local page title in the READY payload. Start
                     * best-effort local frame extraction now. This creates no
                     * ExoPlayer and no audio/video playback.
                     */
                    tab?.isReady == true && TabThumbnailCache.load(this, id) == null ->
                        TabThumbnailCapture.captureResolved(this, tab)

                    /*
                     * If Android destroys the hidden Activity unexpectedly while
                     * it is still RESOLVING, never leave a permanently false
                     * "Preparing…" state. Configuration recreation is excluded
                     * because Android will immediately create the replacement.
                     * A real interruption returns to QUEUED and WorkManager gets a
                     * direct/network recovery attempt. On the next foreground host,
                     * the normal browser-capable stage may continue as needed.
                     */
                    tab?.preparationState == VideoTabStore.PreparationState.RESOLVING &&
                        !activity.isChangingConfigurations -> {
                        VideoTabStore.markQueued(id, "Background preparation was interrupted; retry queued")
                        TabPreparationManager.enqueue(this, id, replace = true)
                    }
                }
            }
        }
        activityTabs.remove(activity)
    }

    private fun captureBrowserPageTitle(activity: BrowserResolverActivity) {
        val title = activity.findViewById<WebView>(R.id.browser_web_view)
            ?.title?.trim().orEmpty()
        if (title.isNotBlank()) lastBrowserPageTitle = title
    }

    private fun withLocalBrowserTitle(json: String): String {
        val pageTitle = lastBrowserPageTitle.trim()
        if (pageTitle.isBlank()) return json

        return runCatching {
            val resolved = ResolvedMedia.fromJson(json)
            if (resolved.resolverMode == "browser") resolved.copy(title = pageTitle).toJson() else json
        }.getOrDefault(json)
    }

    private fun saveActivityTab(activity: PlayerActivity) {
        val tabId = activityTabs[activity] ?: return
        val existing = VideoTabStore.get(tabId) ?: return
        val snapshot = activity.tabSessionSnapshot()

        val currentResolved = runCatching {
            ResolvedMedia.fromJson(snapshot.resolvedMediaJson)
        }.getOrNull()

        currentResolved?.displayedHeight?.takeIf { it > 0 }?.let {
            VideoTabStore.setActualQuality(tabId, it)
        }

        /*
         * yt-dlp and browser sibling-URL switches already write a numeric
         * requested_quality into the resolved payload. Persist that as the manual
         * preference without modifying PlayerActivity's validated switch logic.
         * Adaptive masters keep requested_quality=auto and are handled by
         * AdaptiveQualityRuntime, so do not clear its manual preference here.
         */
        currentResolved?.let { resolved ->
            val requestedHeight = resolved.requestedQuality.toIntOrNull()?.takeIf { it > 0 }
            when {
                requestedHeight != null -> VideoTabStore.setManualQuality(tabId, requestedHeight)
                resolved.resolverMode != "browser" || resolved.browserVariants.isNotEmpty() ->
                    VideoTabStore.setManualQuality(tabId, null)
            }
        }

        val json = if (
            currentResolved != null &&
            currentResolved.resolverMode == "browser" &&
            existing.title.isNotBlank() &&
            currentResolved.title != existing.title
        ) {
            currentResolved.copy(title = existing.title).toJson()
        } else {
            snapshot.resolvedMediaJson
        }

        VideoTabStore.update(
            id = tabId,
            resolvedMediaJson = json,
            positionMs = snapshot.positionMs,
            playWhenReady = snapshot.playWhenForeground
        )
    }

    private fun attachTabButton(activity: PlayerActivity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(TAB_BUTTON_TAG) != null) return

        val button = Button(activity).apply {
            tag = TAB_BUTTON_TAG
            isAllCaps = false
            minWidth = 0
            minHeight = 42.dp(activity)
            setPadding(14.dp(activity), 0, 14.dp(activity), 0)
            textSize = 13f
            setTextColor(ContextCompat.getColor(activity, R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.app_surface_active)
            )
            setOnClickListener { openDashboard(activity) }
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = 12.dp(activity) }

        activity.addContentView(button, params)
        updateTabButton(activity)
    }

    private fun updateTabButton(activity: PlayerActivity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val button = decor.findViewWithTag<Button>(TAB_BUTTON_TAG) ?: return
        val tabs = VideoTabStore.allTabs()
        val ready = tabs.count { it.isReady }
        button.text = activity.getString(R.string.tabs_button_ready_count, ready, tabs.size)
    }

    private fun openDashboard(activity: PlayerActivity) {
        saveActivityTab(activity)
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        activity.finish()
    }

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
