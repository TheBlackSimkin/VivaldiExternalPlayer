package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/**
 * One coordinator for every persistent-tab preparation path.
 *
 * Two states are deliberately tracked separately:
 * - launchingTabId: startActivity was requested, but Android has not created the
 *   BackgroundPreparationActivity yet;
 * - activeTabId: the preparation Activity really exists.
 *
 * Build #162 used a single optimistic activeTabId. If Android dropped the
 * background Activity launch, that stale value blocked every later QUEUED tab.
 * The launch watchdog below prevents that deadlock and falls back to the normal
 * WorkManager direct/network stage instead of leaving tabs permanently queued.
 */
object UnifiedPreparationCoordinator {
    private const val PREPARATION_LAUNCH_WATCHDOG_MS = 2_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var foregroundHost = WeakReference<Activity>(null)
    private var hostIsResumed = false
    private var launchingTabId: String? = null
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

    /** Confirmation that Android actually created the hidden preparation host. */
    @Synchronized
    fun onPreparationCreated(activity: BackgroundPreparationActivity) {
        val id = activity.intent
            .getStringExtra(BackgroundPreparationActivity.EXTRA_TAB_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return

        if (launchingTabId == id) launchingTabId = null
        activeTabId = id
    }

    @Synchronized
    fun onPreparationDestroyed(activity: BackgroundPreparationActivity) {
        val id = activity.intent.getStringExtra(BackgroundPreparationActivity.EXTRA_TAB_ID)
        if (id == null || activeTabId == id) activeTabId = null
        if (id == null || launchingTabId == id) launchingTabId = null
        mainHandler.post { launchFirstQueuedIfPossible() }
    }

    /**
     * Explicit BG share hand-off.
     *
     * The preparer is started INSIDE the tiny share task first. BackgroundShareActivity
     * then finishes itself, leaving BackgroundPreparationActivity as the task root;
     * that Activity immediately moves the task behind Vivaldi. This avoids the
     * build-#162 race where a separate new task could be removed before Android
     * had actually established it.
     */
    fun startFromShare(activity: Activity, tabId: String): Boolean =
        startNow(activity, tabId, reuseCallerTask = true)

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
     * A WorkManager direct-resolution miss should continue through the same
     * browser-capable stage whenever a usable foreground host exists.
     */
    @Synchronized
    fun browserStageNeeded(tabId: String, technicalMessage: String = "") {
        if (activeTabId == tabId || launchingTabId == tabId) return
        VideoTabStore.markQueued(tabId, technicalMessage)
        mainHandler.post { launchFirstQueuedIfPossible(preferredTabId = tabId) }
    }

    fun prepareNow(activity: Activity, tabId: String): Boolean = startNow(activity, tabId)

    @Synchronized
    private fun launchFirstQueuedIfPossible(preferredTabId: String? = null) {
        if (!hostIsResumed || activeTabId != null || launchingTabId != null) return
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
        reuseCallerTask: Boolean = false
    ): Boolean {
        val tab = VideoTabStore.get(tabId) ?: return false
        if (tab.isReady || tab.sourceUrl.isBlank()) return false

        val busyId = activeTabId ?: launchingTabId
        if (busyId != null && busyId != tabId) return false
        if (busyId == tabId) return true

        launchingTabId = tabId
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
            launchingTabId = null
            VideoTabStore.markQueued(tabId, it.message.orEmpty().take(300))
            false
        }

        if (!launched) {
            TabPreparationManager.enqueue(activity.applicationContext, tabId, replace = true)
            return false
        }

        /*
         * startActivity returning does not guarantee that Android created the
         * Activity. Confirm via Application.onActivityCreated; otherwise clear
         * the launch reservation and let WorkManager at least attempt direct prep.
         */
        val appContext = activity.applicationContext
        mainHandler.postDelayed({
            val needsFallback = synchronized(this) {
                if (launchingTabId != tabId) {
                    false
                } else {
                    launchingTabId = null
                    val current = VideoTabStore.get(tabId)
                    if (current != null && !current.isReady &&
                        current.preparationState != VideoTabStore.PreparationState.RESOLVING
                    ) {
                        VideoTabStore.markQueued(tabId, "Background preparer did not start; direct retry queued")
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
