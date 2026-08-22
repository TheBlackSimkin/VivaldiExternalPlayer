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
         * Build #215 proved the preparation Activity can stay RESUMED and resolve
         * PH before ExternalPlayer is opened. Build #225 then proved that making
         * the window exactly alpha=0.0 removes the visible preparation/frame flash,
         * but Vivaldi could still remain unresponsive for roughly seven seconds.
         *
         * The remaining issue is input focus, not compositor opacity. The BG host
         * must stay fully transparent, and the window must not accept touch OR
         * input focus. Android's window contract says FLAG_NOT_FOCUSABLE sends
         * focus to a focusable window behind it and also implies NOT_TOUCH_MODAL.
         * We set NOT_TOUCH_MODAL explicitly as documentation of the intended
         * pass-through behavior.
         *
         * Important: the Activity itself still remains top/RESUMED. Build #205
         * proved that putting this phone's preparation Activity into STOPPED state
         * causes Android to destroy it almost immediately. This change therefore
         * tests window-focus ownership without returning to the failed lifecycle.
         */
        private const val BG_PREPARATION_WINDOW_ALPHA = 0.0f
    }

    private val activityTabs = WeakHashMap<Activity, String>()
    private var lastBrowserPageTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        VideoTabStore.initialize(this)

        /* Preserve the best available page identity for tabs created by older builds. */
        VideoTabStore.allTabs().forEach { TabOriginStore.ensureFallback(this, it) }
        VideoTabStore.recentlyClosedTabs().forEach { TabOriginStore.ensureFallback(this, it) }

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

        if (activity is BackgroundShareActivity) {
            rememberOriginalBackgroundShare(activity)
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
             * RESUMED, because #205 proved a STOPPED WebView host is not stable on
             * this phone. However, #225 proved that a focusable transparent host
             * can still leave Vivaldi unresponsive even when alpha is exactly 0.
             *
             * The window is therefore fully transparent, NOT_TOUCHABLE and now
             * also NOT_FOCUSABLE/NOT_TOUCH_MODAL. This deliberately separates the
             * Activity lifecycle needed by WebView from ownership of user input.
             * Real-device QA must confirm that PH discovery still works without
             * window focus; if not, the next architecture must move browser work
             * to a non-Activity private-display window rather than stealing focus.
             */
            configureTransparentPreparationWindow(activity)

            val flags = activity.window.attributes.flags
            OperationLog.record(
                this,
                event = "PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED",
                detail = buildString {
                    append("display=${activity.display?.displayId ?: -1}")
                    append(" alpha=${activity.window.attributes.alpha}")
                    append(" notTouchable=${flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0}")
                    append(" notFocusable=${flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0}")
                    append(" notTouchModal=${flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0}")
                }
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

        /* Foreground-created tabs use the resolver's page URL as the best available origin. */
        runCatching { ResolvedMedia.fromJson(tabJson).webpageUrl }
            .getOrNull()
            ?.let { TabOriginStore.remember(this, tabId, it) }

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

    /** Capture the exact shared page before resolver results can replace temporary metadata. */
    private fun rememberOriginalBackgroundShare(activity: BackgroundShareActivity) {
        val sharedUrl = Regex("https?://\\S+")
            .find(activity.intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            ?.value
            ?.trimEnd('.', ',', ')', ']', '}')
            ?: return

        val candidate = VideoTabStore.allTabs()
            .filter { it.sourceUrl == sharedUrl }
            .maxByOrNull { it.createdAtMs }
            ?: return

        TabOriginStore.remember(this, candidate.id, sharedUrl)
    }

    /**
     * Keep the WebView host RESUMED without letting its invisible window own the
     * user's input. This is deliberately different from putting the Activity
     * behind Vivaldi: #205 proved the latter makes the host STOPPED and unstable.
     *
     * Android 12+ only permits pass-through touches across a NOT_TOUCHABLE window
     * in trusted cases. Exact alpha 0.0 satisfies the fully-transparent case.
     * Build #225 proved opacity alone was not sufficient for this device, so the
     * host is also NOT_FOCUSABLE. FLAG_NOT_FOCUSABLE already implies
     * FLAG_NOT_TOUCH_MODAL, but setting both makes the requirement explicit for
     * future maintenance.
     */
    private fun configureTransparentPreparationWindow(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
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
