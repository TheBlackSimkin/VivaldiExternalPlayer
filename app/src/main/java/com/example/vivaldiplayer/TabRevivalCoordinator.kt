package com.example.vivaldiplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

/**
 * Serializes user-requested stale-tab revival through the protected foreground-service /
 * private-display preparation architecture.
 *
 * This coordinator never creates an Activity, WebView, PlayerActivity or ExoPlayer. It merely
 * feeds one stored original page URL at a time to BackgroundPreparationKeepAliveService and
 * waits for that tab to reach a terminal preparation state before starting the next one.
 *
 * Revive All may be queued from the dashboard and then the user may open a ready video while
 * revival is active. We keep queued work queued and suspend any active revive-* session as soon
 * as foreground playback resumes. This preserves the protected recovery path while preventing an
 * already-running private-display session from disturbing the visible player.
 */
object TabRevivalCoordinator {
    private const val POLL_MS = 750L
    private const val FOREGROUND_PLAYER_RECHECK_MS = 1_000L

    private data class Request(val tabId: String, val originalPageUrl: String)
    private data class ActiveRequest(val request: Request, val token: String)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<Request>()
    private var active: ActiveRequest? = null
    private var appContext: Context? = null
    private var foregroundRecheckScheduled = false

    @Synchronized
    fun enqueue(context: Context, tabs: List<VideoTabStore.VideoTab>): Int {
        appContext = context.applicationContext

        val knownIds = buildSet {
            active?.let { add(it.request.tabId) }
            pending.forEach { add(it.tabId) }
        }

        var added = 0
        tabs.forEach { tab ->
            val original = TabOriginStore.pageUrl(context, tab).trim()
            if (tab.id !in knownIds && isHttpUrl(original)) {
                pending.addLast(Request(tab.id, original))
                added += 1
            }
        }

        startNextLocked()
        return added
    }

    @Synchronized
    fun cancel(tabId: String) {
        pending.removeAll { it.tabId == tabId }
        val running = active
        if (running?.request?.tabId == tabId) {
            active = null
            BackgroundPreparationKeepAliveService.suspendRevivalSession(running.token)
        }
    }

    @Synchronized
    fun suspendForForegroundPlayback() {
        val running = active ?: return
        active = null
        pending.addFirst(running.request)
        VideoTabStore.markQueued(running.request.tabId, "Revival paused while player is open")
        VideoTabStore.markTechnicalStage(
            running.request.tabId,
            "REVIVAL_SUSPENDED_FOR_FOREGROUND_PLAYER"
        )
        BackgroundPreparationKeepAliveService.suspendRevivalSession(running.token)
        scheduleForegroundRecheckLocked()
    }

    @Synchronized
    private fun startNextLocked() {
        if (active != null) return
        val context = appContext ?: return
        if (pending.isEmpty()) return

        if (ForegroundPlaybackState.isPlayerForeground()) {
            scheduleForegroundRecheckLocked()
            return
        }

        val next = pending.removeFirst()
        val tab = VideoTabStore.get(next.tabId)

        if (tab == null) {
            startNextLocked()
            return
        }

        val token = "revive-${next.tabId}-${System.currentTimeMillis()}"
        active = ActiveRequest(next, token)
        VideoTabStore.markPreparationRequested(next.tabId)
        VideoTabStore.markQueued(next.tabId, "Revival requested")
        VideoTabStore.markTechnicalStage(next.tabId, "REVIVAL_PRIVATE_SERVICE_REQUESTED")

        BackgroundPreparationKeepAliveService.acquire(
            context = context,
            token = token,
            tabId = next.tabId,
            sourceUrl = next.originalPageUrl
        )

        mainHandler.postDelayed(::pollActive, POLL_MS)
    }

    private fun pollActive() {
        synchronized(this) {
            val request = active?.request ?: return
            val tab = VideoTabStore.get(request.tabId)

            val finished = tab == null || when (tab.preparationState) {
                VideoTabStore.PreparationState.READY,
                VideoTabStore.PreparationState.ERROR,
                VideoTabStore.PreparationState.NEEDS_ATTENTION -> true
                VideoTabStore.PreparationState.QUEUED,
                VideoTabStore.PreparationState.RESOLVING -> false
            }

            if (finished) {
                active = null
                startNextLocked()
            } else {
                mainHandler.postDelayed(::pollActive, POLL_MS)
            }
        }
    }

    private fun scheduleForegroundRecheckLocked() {
        if (foregroundRecheckScheduled) return
        foregroundRecheckScheduled = true
        mainHandler.postDelayed({
            synchronized(this) {
                foregroundRecheckScheduled = false
                startNextLocked()
            }
        }, FOREGROUND_PLAYER_RECHECK_MS)
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
}
