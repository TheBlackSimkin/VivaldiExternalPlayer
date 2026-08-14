package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

        /**
         * The preparation window must be effectively invisible while remaining a
         * real top/resumed Activity window. A tiny non-zero alpha avoids relying
         * on compositor behavior for a completely zero-alpha surface, while being
         * far below Android's obscuring-opacity threshold for touch pass-through.
         */
        private const val BG_PREPARATION_WINDOW_ALPHA = 0.01f
    }

    private val activityTabs = WeakHashMap<Activity, String>()
    private var lastBrowserPageTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        VideoTabStore.initialize(this)

        /*
         * VideoTabStore converts stale RESOLVING state to QUEUED after process
         * restart. A normal BG session which had already created its preparation
         * host/WebView must not silently re-enter the legacy Worker architecture.
         */
        convertInterruptedBgPreparationSessionsToError()

        TabPreparationManager.resumePending(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is BackgroundPreparationActivity) {
            UnifiedPreparationCoordinator.onPreparationCreated(activity)
            return
        }

        if (activity is BackgroundShareActivityV2) {
            /* The exported chooser target is only a short share-time handoff. */
            return
        }

        if (activity is BackgroundVirtualPreparationActivity) {
            /*
             * Historical class name notwithstanding, the normal BG path no longer
             * launches this Activity on a virtual display. #212 device QA proved
             * Android 13 denied the first Activity launch onto our untrusted
             * private virtual display.
             *
             * Keep this Activity on the normal/default display and keep it
             * RESUMED. Its window is almost fully transparent and NOT_TOUCHABLE,
             * so Vivaldi remains what the user sees underneath while WebView keeps
             * the real Activity context and full viewport which the successful
             * manual Browser Step uses.
             *
             * We intentionally do NOT add FLAG_NOT_FOCUSABLE: browser/page code
             * can depend on focus. NOT_TOUCHABLE is enough to prevent accidental
             * invisible interaction with the WebView during automatic discovery.
             */
            configureTransparentPreparationWindow(activity)
            OperationLog.record(
                this,
                event = "PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED",
                detail = "display=${activity.display?.displayId ?: -1} alpha=${activity.window.attributes.alpha}"
            )
            return
        }

        if (activity !is PlayerActivity) return

        val suppliedTabId = activity.intent.getStringExtra(EXTRA_TAB_ID)
        val originalJson = activity.intent.getStringExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA)
            ?: return
        val tabJson = withLocalBrowserTitle(originalJson)

        val tabId =
            if (suppliedTabId != null && VideoTabStore.get(suppliedTabId) != null) {
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
        if (activity is BackgroundVirtualPreparationActivity) {
            OperationLog.record(
                this,
                event = "PRIMARY_OVERLAY_PREP_ACTIVITY_STARTED",
                detail = "display=${activity.display?.displayId ?: -1}"
            )
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is BackgroundShareActivityV2) {
            return
        }

        if (activity is BackgroundVirtualPreparationActivity) {
            OperationLog.record(
                this,
                event = "PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED",
                detail = "display=${activity.display?.displayId ?: -1}"
            )
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
            return
        }

        if (activity is BackgroundVirtualPreparationActivity) {
            OperationLog.record(
                this,
                event = "PRIMARY_OVERLAY_PREP_ACTIVITY_PAUSED",
                detail = "finishing=${activity.isFinishing}"
            )
            return
        }

        if (activity is PlayerActivity) {
            UnifiedPreparationCoordinator.onHostPaused(activity)
        }

        when (activity) {
            is BrowserResolverActivity -> captureBrowserPageTitle(activity)
            is PlayerActivity -> saveActivityTab(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is BackgroundVirtualPreparationActivity) {
            OperationLog.record(
                this,
                event = "PRIMARY_OVERLAY_PREP_ACTIVITY_STOPPED",
                detail = "finishing=${activity.isFinishing}"
            )
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is BackgroundVirtualPreparationActivity) {
            /*
             * The preparation Activity itself owns truthful READY/ERROR and
             * interruption handling. Never enqueue the legacy Worker here.
             */
            OperationLog.record(
                this,
                event = "PRIMARY_OVERLAY_PREP_ACTIVITY_DESTROYED_CALLBACK",
                detail = "changingConfig=${activity.isChangingConfigurations}"
            )
            return
        }

        if (activity is BackgroundShareActivityV2) {
            /* The short handoff is expected to finish after starting preparation. */
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
                    tab?.isReady == true &&
                        TabThumbnailCache.load(this, id) == null ->
                        TabThumbnailCapture.captureResolved(this, tab)

                    /*
                     * This older preparer remains only for explicit retry/preload
                     * recovery paths. Normal BG shares never enter it automatically.
                     */
                    tab?.preparationState == VideoTabStore.PreparationState.RESOLVING &&
                        !activity.isChangingConfigurations -> {
                        VideoTabStore.markQueued(
                            id,
                            "Background preparation was interrupted; retry queued"
                        )
                        TabPreparationManager.enqueue(this, id, replace = true)
                    }
                }
            }
        }

        activityTabs.remove(activity)
    }

    /**
     * Make the preparation host visually disappear without putting its Activity
     * into the STOPPED state which #205 proved this phone aggressively destroys.
     */
    private fun configureTransparentPreparationWindow(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        activity.window.decorView.setBackgroundColor(Color.TRANSPARENT)

        val attributes = activity.window.attributes
        attributes.alpha = BG_PREPARATION_WINDOW_ALPHA
        activity.window.attributes = attributes
    }

    /**
     * Process death bypasses Activity.onDestroy(). Identify an unfinished normal
     * BG host by its created host + WebView timestamps and convert it to ERROR
     * before resumePending() can re-enter the old Worker path.
     */
    private fun convertInterruptedBgPreparationSessionsToError() {
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
                    "PROCESS_RESTART_PRIMARY_OVERLAY_BG_ERROR"
                )
                OperationLog.record(
                    this,
                    event = "PROCESS_RESTART_PRIMARY_OVERLAY_BG_ERROR",
                    tabId = tab.id,
                    detail = "Interrupted transparent BG session; legacy Worker recovery skipped"
                )
            }
    }

    private fun captureBrowserPageTitle(activity: BrowserResolverActivity) {
        val title = activity.findViewById<WebView>(R.id.browser_web_view)
            ?.title
            ?.trim()
            .orEmpty()

        if (title.isNotBlank()) {
            lastBrowserPageTitle = title
        }
    }

    private fun withLocalBrowserTitle(json: String): String {
        val pageTitle = lastBrowserPageTitle.trim()
        if (pageTitle.isBlank()) return json

        return runCatching {
            val resolved = ResolvedMedia.fromJson(json)
            if (resolved.resolverMode == "browser") {
                resolved.copy(title = pageTitle).toJson()
            } else {
                json
            }
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

        currentResolved?.let { resolved ->
            val requestedHeight =
                resolved.requestedQuality.toIntOrNull()?.takeIf { it > 0 }

            when {
                requestedHeight != null ->
                    VideoTabStore.setManualQuality(tabId, requestedHeight)

                resolved.resolverMode != "browser" ||
                    resolved.browserVariants.isNotEmpty() ->
                    VideoTabStore.setManualQuality(tabId, null)
            }
        }

        val json =
            if (
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
            setTextColor(
                ContextCompat.getColor(activity, R.color.app_text_primary)
            )
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.app_surface_active)
            )
            setOnClickListener { openDashboard(activity) }
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = 12.dp(activity)
        }

        activity.addContentView(button, params)
        updateTabButton(activity)
    }

    private fun updateTabButton(activity: PlayerActivity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val button = decor.findViewWithTag<Button>(TAB_BUTTON_TAG) ?: return
        val tabs = VideoTabStore.allTabs()
        val ready = tabs.count { it.isReady }

        button.text =
            activity.getString(R.string.tabs_button_ready_count, ready, tabs.size)
    }

    private fun openDashboard(activity: PlayerActivity) {
        saveActivityTab(activity)

        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
        )

        activity.finish()
    }

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
