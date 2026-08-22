package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/**
 * Coordinator for preparation paths which still need the non-exported
 * BackgroundPreparationActivity (retry, preload and recovery).
 *
 * IMPORTANT: the normal `BG - External Player` share path no longer comes
 * through this coordinator. BackgroundShareActivity is already a valid Activity
 * because the user explicitly selected it, and it now owns direct + hidden
 * browser preparation itself. That removes build #187's fragile second-Activity
 * hand-off from the normal BG path.
 *
 * For the remaining paths we still distinguish requested launches from Activity
 * instances Android actually created, and a watchdog falls back to WorkManager
 * if a hidden launch never materializes.
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
        VideoTabStore.markPreparationHostCreated(id)
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

    fun retry(activity: Activity, tabId: String): Boolean {
        VideoTabStore.markQueued(tabId)
        VideoTabStore.markPreparationRequested(tabId)
        return startNow(activity, tabId)
    }

    /** Feature 29: automatic next-tab preload stays serialized. */
    fun preloadNext(activity: Activity, currentTabId: String): Boolean {
        if (!AppSettings.preloadNextTab(activity)) return false
        val next = VideoTabStore.nextAfter(currentTabId) ?: return false
        if (TabOriginStore.pageUrl(activity, next).isBlank()) return false
        if (next.preparationState != VideoTabStore.PreparationState.QUEUED) return false
        return startNow(activity, next.id)
    }

    /**
     * A WorkManager direct-resolution miss should continue through the same
     * browser-capable stage whenever a usable foreground host exists.
     *
     * Normal BG shares do not need this fallback because their share Activity
     * already owns a WebView. This method is for process-recovery/retry paths.
     */
    @Synchronized
    fun browserStageNeeded(tabId: String, technicalMessage: String = "") {
        if (tabId in activeTabIds || tabId in launchingTabIds) return
        VideoTabStore.markBrowserStageRequested(tabId)
        VideoTabStore.markQueued(tabId, technicalMessage)
        mainHandler.post { launchFirstQueuedIfPossible(preferredTabId = tabId) }
    }

    fun prepareNow(activity: Activity, tabId: String): Boolean = startNow(activity, tabId)

    /** Foreground-driven retry/preload preparation is intentionally one-at-a-time. */
    @Synchronized
    private fun launchFirstQueuedIfPossible(preferredTabId: String? = null) {
        if (!hostIsResumed || activeTabIds.isNotEmpty() || launchingTabIds.isNotEmpty()) return
        val host = foregroundHost.get() ?: return
        if (host.isFinishing || host.isDestroyed) return

        val candidate = preferredTabId
            ?.let(VideoTabStore::get)
            ?.takeIf {
                it.preparationState == VideoTabStore.PreparationState.QUEUED &&
                    TabOriginStore.pageUrl(host, it).isNotBlank()
            }
            ?: VideoTabStore.allTabs().firstOrNull {
                it.preparationState == VideoTabStore.PreparationState.QUEUED &&
                    TabOriginStore.pageUrl(host, it).isNotBlank()
            }
            ?: return

        startNow(host, candidate.id)
    }

    @Synchronized
    private fun startNow(
        activity: Activity,
        tabId: String
    ): Boolean {
        val tab = VideoTabStore.get(tabId) ?: return false
        val originalPageUrl = TabOriginStore.pageUrl(activity, tab)
        if (tab.isReady || originalPageUrl.isBlank()) return false
        if (tabId in activeTabIds || tabId in launchingTabIds) return true

        if (activeTabIds.isNotEmpty() || launchingTabIds.isNotEmpty()) {
            /*
             * Keep retry/preload serialized. Once the current hidden Activity
             * finishes, onPreparationDestroyed() may launch the next queued tab.
             */
            VideoTabStore.markQueued(tabId)
            return true
        }

        launchingTabIds.add(tabId)
        VideoTabStore.markPreparationRequested(tabId)
        VideoTabStore.markQueued(tabId)
        VideoTabStore.markTechnicalStage(tabId, "HIDDEN_ACTIVITY_LAUNCH_REQUESTED")
        TabPreparationManager.cancelScheduled(activity.applicationContext, tabId)

        val launchIntent = Intent(activity, BackgroundPreparationActivity::class.java)
            .putExtra(BackgroundPreparationActivity.EXTRA_URL, originalPageUrl)
            .putExtra(BackgroundPreparationActivity.EXTRA_TAB_ID, tab.id)
            .addFlags(
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )

        val launched = runCatching {
            activity.startActivity(launchIntent)
            true
        }.getOrElse {
            launchingTabIds.remove(tabId)
            VideoTabStore.markQueued(tabId, it.message.orEmpty().take(300))
            VideoTabStore.markTechnicalStage(tabId, "HIDDEN_ACTIVITY_LAUNCH_FAILED")
            false
        }

        if (!launched) {
            TabPreparationManager.enqueue(activity.applicationContext, tabId, replace = true)
            return false
        }

        /*
         * startActivity returning does not prove Android created the Activity.
         * Confirmation comes through Application.onActivityCreated. If it never
         * arrives, clear only this reservation and use WorkManager direct recovery.
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
                        VideoTabStore.markTechnicalStage(tabId, "HIDDEN_ACTIVITY_WATCHDOG_FALLBACK")
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
