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
 * revival is still pending. In that case we keep queued revive work queued, but defer starting
 * the next private-display session until foreground playback is no longer resumed. That preserves
 * the protected recovery path while avoiding repeated player blink/lifecycle disturbance.
 */
object TabRevivalCoordinator {
    private const val POLL_MS = 750L
    private const val FOREGROUND_PLAYER_RECHECK_MS = 1_000L

    private data class Request(val tabId: String, val originalPageUrl: String)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<Request>()
    private var active: Request? = null
    private var appContext: Context? = null
    private var foregroundRecheckScheduled = false

    @Synchronized
    fun enqueue(context: Context, tabs: List<VideoTabStore.VideoTab>): Int {
        appContext = context.applicationContext

        val knownIds = buildSet {
            active?.let { add(it.tabId) }
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
        /*
         * An already-running private-display session owns its own safe cleanup lifecycle.
         * We intentionally do not tear it down from outside the service. Closing a tab only
         * prevents queued revival work from starting later.
         */
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

        active = next
        val token = "revive-${next.tabId}-${System.currentTimeMillis()}"
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
            val request = active ?: return
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
