package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/**
 * Single front door for tab preparation while ExternalPlayer has a foreground
 * Activity available.
 *
 * Every user-facing preparation path (BG share, preload-next, retry and queued
 * restart recovery) ultimately launches the SAME BackgroundPreparationActivity,
 * which performs yt-dlp first and then the safe browser-capable discovery stage.
 * WorkManager remains only as a network/restart fallback when Android has not
 * given the app a foreground Activity from which a WebView Activity may start.
 */
object UnifiedPreparationCoordinator {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var foregroundHost = WeakReference<Activity>(null)
    private var hostIsResumed = false
    private var activeTabId: String? = null

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

    @Synchronized
    fun onPreparationCreated(activity: BackgroundPreparationActivity) {
        activeTabId = activity.intent
            .getStringExtra(BackgroundPreparationActivity.EXTRA_TAB_ID)
            ?.takeIf { it.isNotBlank() }
            ?: activeTabId
    }

    @Synchronized
    fun onPreparationDestroyed(activity: BackgroundPreparationActivity) {
        val id = activity.intent.getStringExtra(BackgroundPreparationActivity.EXTRA_TAB_ID)
        if (id == null || activeTabId == id) activeTabId = null
        mainHandler.post { launchFirstQueuedIfPossible() }
    }

    fun startFromShare(activity: Activity, tabId: String): Boolean = startNow(activity, tabId)

    fun retry(activity: Activity, tabId: String): Boolean {
        VideoTabStore.markQueued(tabId)
        return startNow(activity, tabId)
    }

    /** Feature 29: only a genuinely QUEUED next tab is preloaded automatically. */
    fun preloadNext(activity: Activity, currentTabId: String): Boolean {
        if (!AppSettings.preloadNextTab(activity)) return false
        val next = VideoTabStore.nextAfter(currentTabId) ?: return false
        if (next.sourceUrl.isBlank()) return false
        if (next.preparationState != VideoTabStore.PreparationState.QUEUED) return false
        return startNow(activity, next.id)
    }

    /**
     * Worker direct-resolution miss. If this tab already has the browser-capable
     * Activity running, the cancelled Worker is stale and must not overwrite its
     * RESOLVING state back to QUEUED.
     */
    @Synchronized
    fun browserStageNeeded(tabId: String, technicalMessage: String = "") {
        if (activeTabId == tabId) return
        VideoTabStore.markQueued(tabId, technicalMessage)
        mainHandler.post { launchFirstQueuedIfPossible(preferredTabId = tabId) }
    }

    fun prepareNow(activity: Activity, tabId: String): Boolean = startNow(activity, tabId)

    @Synchronized
    private fun launchFirstQueuedIfPossible(preferredTabId: String? = null) {
        if (!hostIsResumed || activeTabId != null) return
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
    private fun startNow(activity: Activity, tabId: String): Boolean {
        val tab = VideoTabStore.get(tabId) ?: return false
        if (tab.isReady || tab.sourceUrl.isBlank()) return false
        if (activeTabId != null && activeTabId != tabId) return false
        if (activeTabId == tabId) return true

        activeTabId = tabId
        VideoTabStore.markQueued(tabId)
        TabPreparationManager.cancelScheduled(activity.applicationContext, tabId)

        return runCatching {
            activity.startActivity(
                Intent(activity, BackgroundPreparationActivity::class.java)
                    .putExtra(BackgroundPreparationActivity.EXTRA_URL, tab.sourceUrl)
                    .putExtra(BackgroundPreparationActivity.EXTRA_TAB_ID, tab.id)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
            )
            true
        }.getOrElse {
            activeTabId = null
            VideoTabStore.markQueued(tabId, it.message.orEmpty().take(300))
            false
        }
    }
}
