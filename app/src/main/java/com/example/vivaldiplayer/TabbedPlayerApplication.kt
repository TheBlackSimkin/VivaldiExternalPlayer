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

/**
 * Application-level persistent-tab coordinator.
 *
 * The home dashboard is now the canonical tab UI. PlayerActivity keeps only a
 * compact floating Tabs button which saves the current session and returns to
 * MainActivity; there is no second tab-popup implementation to drift out of sync.
 */
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

        /*
         * AdaptiveQualityRuntime is registered before Application callbacks.
         * Writing the resolved tab ID back into the Intent lets its posted attach
         * step read the persistent tab even for a newly-created Play-now tab.
         */
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

        /* Feature 29 now enters the same browser-capable engine as BG Add. */
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
            UnifiedPreparationCoordinator.onPreparationDestroyed(activity)
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

        val json = runCatching {
            val current = ResolvedMedia.fromJson(snapshot.resolvedMediaJson)
            if (
                current.resolverMode == "browser" &&
                existing.title.isNotBlank() &&
                current.title != existing.title
            ) current.copy(title = existing.title).toJson() else snapshot.resolvedMediaJson
        }.getOrDefault(snapshot.resolvedMediaJson)

        VideoTabStore.update(
            id = tabId,
            resolvedMediaJson = json,
            positionMs = snapshot.positionMs,
            playWhenReady = snapshot.playWhenForeground
        )
    }

    /** Compact floating control which opens the full dashboard. */
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
