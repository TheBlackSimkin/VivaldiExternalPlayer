package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/**
 * One coordinator for every persistent-tab preparation path.
 *
 * Build #162 optimistically reserved one active tab before Android had actually
 * created BackgroundPreparationActivity. If that launch was dropped, every
 * later tab could stay QUEUED. We now distinguish requested launches from real
 * Activity instances and use a watchdog to clear stale reservations.
 *
 * Explicit BG shares are allowed to establish their own hidden preparation
 * attempts even when another BG tab is already preparing. User-triggered
 * preload/retry remains serialized to avoid unnecessary hidden WebViews.
 */
object UnifiedPreparationCoordinator {
    private const val PREPARATION_LAUNCH_WATCHDOG_MS = 2_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var foregroundHost = WeakReference<Activity>(null)
    private var hostIsResumed = false

    /** startActivity requested, Activity not yet confirmed by lifecycle. */
    private val launchingTabIds = mutableSetOf<String>()

    /** BackgroundPreparationActivity actually exists for these tabs. */
    private val activeTabIds = mutableSetOf<String>()

    @Synchronized
    fun onHostResumed(activity: Activity) {
        if (activity is BackgroundPreparationActivity || activity is BrowserResolverActivity) return
        foregroundHost = WeakReference(activity)
        hostIsResumed = true
        launchFirstQueuedIfPossible()
    }

    @Synchronized
    fun onHostPaused(activity: Activity) {
        if (foregroundHost.get() === activity) hostIsResumed = false
    }

    /** Confirmation that Android actually created one hidden preparation host. */
    @Synchronized
    fun onPreparationCreated(activity: BackgroundPreparationActivity) {
        val id = activity.intent
            .getStringExtra(BackgroundPreparationActivity.EXTRA_TAB_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return

        launchingTabIds.remove(id)
        activeTabIds.add(id)
    }

    @Synchronized
    fun onPreparationDestroyed(activity: BackgroundPreparationActivity) {
        val id = activity.intent.getStringExtra(BackgroundPreparationActivity.EXTRA_TAB_ID)
        if (id != null) {
            activeTabIds.remove(id)
            launchingTabIds.remove(id)
        }

        /* Foreground preload/retry may continue serially when a visible host exists. */
        mainHandler.post { launchFirstQueuedIfPossible() }
    }

    /**
     * Explicit BG share hand-off.
     *
     * This path deliberately allows parallel hidden preparation attempts. If the
     * user sends several links from Vivaldi, later links must not remain queued
     * merely because the first hidden WebView is still resolving.
     */
    fun startFromShare(activity: Activity, tabId: String): Boolean =
        startNow(
            activity = activity,
            tabId = tabId,
            reuseCallerTask = true,
            allowParallel = true
        )

    fun retry(activity: Activity, tabId: String): Boolean {
        VideoTabStore.markQueued(tabId)
        return startNow(activity, tabId)
    }

    /** Feature 29: automatic next-tab preload stays serialized. */
    fun preloadNext(activity: Activity, currentTabId: String): Boolean {
        if (!AppSettings.preloadNextTab(activity)) return false
        val next = VideoTabStore.nextAfter(currentTabId) ?: return false
        if (next.sourceUrl.isBlank()) return false
        if (next.preparationState != VideoTabStore.PreparationState.QUEUED) return false
        return startNow(activity, next.id)
    }

    /**
     * A WorkManager direct-resolution miss should continue through the same
     * browser-capable stage whenever a usable foreground host exists.
     */
    @Synchronized
    fun browserStageNeeded(tabId: String, technicalMessage: String = "") {
        if (tabId in activeTabIds || tabId in launchingTabIds) return
        VideoTabStore.markQueued(tabId, technicalMessage)
        mainHandler.post { launchFirstQueuedIfPossible(preferredTabId = tabId) }
    }

    fun prepareNow(activity: Activity, tabId: String): Boolean = startNow(activity, tabId)

    /**
     * Foreground-driven preparation is intentionally one-at-a-time. Explicit BG
     * shares bypass this gate through allowParallel=true in startFromShare().
     */
    @Synchronized
    private fun launchFirstQueuedIfPossible(preferredTabId: String? = null) {
        if (!hostIsResumed || activeTabIds.isNotEmpty() || launchingTabIds.isNotEmpty()) return
        val host = foregroundHost.get() ?: return
        if (host.isFinishing || host.isDestroyed) return

        val candidate = preferredTabId
            ?.let(VideoTabStore::get)
            ?.takeIf { it.preparationState == VideoTabStore.PreparationState.QUEUED }
            ?: VideoTabStore.allTabs().firstOrNull {
                it.preparationState == VideoTabStore.PreparationState.QUEUED &&
                    it.sourceUrl.isNotBlank()
            }
            ?: return

        startNow(host, candidate.id)
    }

    @Synchronized
    private fun startNow(
        activity: Activity,
        tabId: String,
        reuseCallerTask: Boolean = false,
        allowParallel: Boolean = false
    ): Boolean {
        val tab = VideoTabStore.get(tabId) ?: return false
        if (tab.isReady || tab.sourceUrl.isBlank()) return false
        if (tabId in activeTabIds || tabId in launchingTabIds) return true

        if (!allowParallel && (activeTabIds.isNotEmpty() || launchingTabIds.isNotEmpty())) {
            /*
             * Keep it queued. Once the current hidden Activity finishes,
             * onPreparationDestroyed() will launch the next one if a foreground
             * host is still available.
             */
            VideoTabStore.markQueued(tabId)
            return true
        }

        launchingTabIds.add(tabId)
        VideoTabStore.markQueued(tabId)
        TabPreparationManager.cancelScheduled(activity.applicationContext, tabId)

        val launchIntent = Intent(activity, BackgroundPreparationActivity::class.java)
            .putExtra(BackgroundPreparationActivity.EXTRA_URL, tab.sourceUrl)
            .putExtra(BackgroundPreparationActivity.EXTRA_TAB_ID, tab.id)
            .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .apply {
                /*
                 * Normal preload/retry gets an isolated tiny task so moving it
                 * behind the app does not move PlayerActivity/MainActivity too.
                 * BG share deliberately reuses its already-isolated share task.
                 */
                if (!reuseCallerTask) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
            }

        val launched = runCatching {
            activity.startActivity(launchIntent)
            true
        }.getOrElse {
            launchingTabIds.remove(tabId)
            VideoTabStore.markQueued(tabId, it.message.orEmpty().take(300))
            false
        }

        if (!launched) {
            TabPreparationManager.enqueue(activity.applicationContext, tabId, replace = true)
            return false
        }

        /*
         * startActivity returning does not guarantee Android created the Activity.
         * Confirmation comes through Application.onActivityCreated. If it never
         * arrives, clear only this tab's reservation and fall back to WorkManager.
         */
        val appContext = activity.applicationContext
        mainHandler.postDelayed({
            val needsFallback = synchronized(this) {
                if (tabId !in launchingTabIds) {
                    false
                } else {
                    launchingTabIds.remove(tabId)
                    val current = VideoTabStore.get(tabId)
                    if (
                        current != null &&
                        !current.isReady &&
                        current.preparationState != VideoTabStore.PreparationState.RESOLVING
                    ) {
                        VideoTabStore.markQueued(
                            tabId,
                            "Background preparer did not start; direct retry queued"
                        )
                        true
                    } else {
                        false
                    }
                }
            }

            if (needsFallback) {
                TabPreparationManager.enqueue(appContext, tabId, replace = true)
            }
        }, PREPARATION_LAUNCH_WATCHDOG_MS)

        return true
    }
}
