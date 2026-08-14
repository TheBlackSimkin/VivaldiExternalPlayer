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
        private const val DESTROYED_HOST_RECOVERY_WINDOW_MS = 10_000L
    }

    private val activityTabs = WeakHashMap<Activity, String>()

    /**
     * Token for each user-launched BG share Activity. The token is deliberately
     * independent from the tab ID: Activity lifecycle callbacks do not need to
     * reach into BackgroundShareActivityV2's private fields.
     */
    private val bgShareTokens = WeakHashMap<Activity, String>()

    private var lastBrowserPageTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        VideoTabStore.initialize(this)

        /*
         * VideoTabStore truthfully converts any stale RESOLVING state to QUEUED
         * after a process restart. For the newer self-owned V2 BG share host we
         * must NOT then silently hand that tab to the legacy direct-only Worker:
         * its WebView lifecycle was interrupted, so report a technical ERROR and
         * let QA see the interruption explicitly in the operations log.
         *
         * A V2 tab is distinguishable from older retry/preload paths because it
         * records both a preparation host creation and its normally sized WebView
         * creation before it starts work.
         */
        convertInterruptedV2ProcessSessionsToError()

        TabPreparationManager.resumePending(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is BackgroundShareActivityV2) {
            /*
             * Android calls Application.ActivityLifecycleCallbacks after the
             * Activity's onCreate() returns. V2 has already created the persistent
             * tab and started direct preparation, but its posted moveTaskToBack()
             * has not run yet. Starting the foreground keep-alive here therefore
             * remains directly tied to the user's explicit BG share action.
             */
            val token = "bg-${System.identityHashCode(activity)}-${System.currentTimeMillis()}"
            bgShareTokens[activity] = token
            OperationLog.record(
                this,
                event = "BG_SHARE_ACTIVITY_CREATED",
                detail = "token=$token"
            )
            BackgroundPreparationKeepAliveService.acquire(this, token)
            return
        }

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

    override fun onActivityStarted(activity: Activity) {
        if (activity is BackgroundShareActivityV2) {
            logBgLifecycle(activity, "BG_SHARE_ACTIVITY_STARTED")
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is BackgroundShareActivityV2) {
            logBgLifecycle(activity, "BG_SHARE_ACTIVITY_RESUMED")
            return
        }
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
        if (activity is BackgroundShareActivityV2) {
            logBgLifecycle(activity, "BG_SHARE_ACTIVITY_PAUSED")
            return
        }

        if (activity is PlayerActivity) UnifiedPreparationCoordinator.onHostPaused(activity)

        when (activity) {
            is BrowserResolverActivity -> captureBrowserPageTitle(activity)
            is PlayerActivity -> saveActivityTab(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is BackgroundShareActivityV2) {
            logBgLifecycle(activity, "BG_SHARE_ACTIVITY_STOPPED")
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is BackgroundShareActivityV2) {
            val token = bgShareTokens.remove(activity)
            OperationLog.record(
                this,
                event = "BG_SHARE_ACTIVITY_DESTROYED",
                detail = "token=${token ?: "unknown"} changingConfig=${activity.isChangingConfigurations}"
            )

            token?.let(BackgroundPreparationKeepAliveService::release)

            /*
             * #202's Activity-level onDestroy used to silently requeue an
             * unfinished tab through WorkManager. That returned the app to the
             * older architecture which cannot own the browser WebView and could
             * later end at Browser Step only when another foreground Activity
             * appeared.
             *
             * Cancel that legacy recovery immediately. An unexpectedly destroyed
             * V2 host is now a truthful technical ERROR. The operations log keeps
             * the preceding lifecycle so QA can tell us where the interruption
             * occurred instead of hiding it behind several minutes of retries.
             */
            if (!activity.isChangingConfigurations) {
                val now = System.currentTimeMillis()
                VideoTabStore.allTabs()
                    .filter { tab ->
                        tab.preparationState == VideoTabStore.PreparationState.QUEUED &&
                            tab.lastTechnicalPreparationStage == "BG_HOST_DESTROYED_RECOVERY_QUEUED" &&
                            now - tab.updatedAtMs in 0..DESTROYED_HOST_RECOVERY_WINDOW_MS
                    }
                    .forEach { tab ->
                        TabPreparationManager.cancelScheduled(this, tab.id)
                        VideoTabStore.markError(
                            tab.id,
                            "BG preparation Activity was destroyed before automatic browser discovery completed"
                        )
                        VideoTabStore.markTechnicalStage(
                            tab.id,
                            "BG_HOST_DESTROYED_NO_LEGACY_FALLBACK"
                        )
                        OperationLog.record(
                            this,
                            event = "LEGACY_RECOVERY_CANCELLED",
                            tabId = tab.id,
                            detail = "Destroyed V2 host is an ERROR; WorkManager/browser-step fallback was cancelled"
                        )
                    }
            }

            activityTabs.remove(activity)
            return
        }

        if (activity is BackgroundPreparationActivity) {
            val tabId = activity.intent
                .getStringExtra(BackgroundPreparationActivity.EXTRA_TAB_ID)
                ?.takeIf { it.isNotBlank() }

            UnifiedPreparationCoordinator.onPreparationDestroyed(activity)

            tabId?.let { id ->
                val tab = VideoTabStore.get(id)

                when {
                    /*
                     * A successful retry/preload should be as identifiable as
                     * possible before the user later opens the dashboard. The
                     * hidden WebView already saved the local page title in READY.
                     */
                    tab?.isReady == true && TabThumbnailCache.load(this, id) == null ->
                        TabThumbnailCapture.captureResolved(this, tab)

                    /*
                     * This old preparer remains only for explicit retry/preload
                     * recovery paths. Its historical WorkManager recovery behavior
                     * is intentionally preserved here; normal BG shares no longer
                     * depend on this Activity.
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

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    /**
     * A process death bypasses Activity.onDestroy(), so handle that second case
     * before resumePending() can revive the old WorkManager path.
     */
    private fun convertInterruptedV2ProcessSessionsToError() {
        VideoTabStore.allTabs()
            .filter { tab ->
                tab.preparationState == VideoTabStore.PreparationState.QUEUED &&
                    tab.lastTechnicalPreparationStage == "PROCESS_RESTART_QUEUED" &&
                    tab.preparationHostCreatedAtMs > 0L &&
                    tab.browserWebViewCreatedAtMs > 0L &&
                    tab.readyAtMs <= 0L
            }
            .forEach { tab ->
                VideoTabStore.markError(
                    tab.id,
                    "BG preparation process was interrupted before completion"
                )
                VideoTabStore.markTechnicalStage(
                    tab.id,
                    "PROCESS_RESTART_BG_HOST_ERROR"
                )
                OperationLog.record(
                    this,
                    event = "PROCESS_RESTART_BG_HOST_ERROR",
                    tabId = tab.id,
                    detail = "V2 BG session was interrupted; legacy WorkManager recovery intentionally skipped"
                )
            }
    }

    private fun logBgLifecycle(activity: Activity, event: String) {
        OperationLog.record(
            this,
            event = event,
            detail = "token=${bgShareTokens[activity] ?: "unknown"} finishing=${activity.isFinishing}"
        )
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
}
