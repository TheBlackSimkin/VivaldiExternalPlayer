package com.example.vivaldiplayer

import android.content.Context

/**
 * Single authority for deciding whether a persistent tab needs a source revival
 * and for enqueueing that revival through the protected service/private-display
 * architecture.
 *
 * Keep UI classes dumb: dashboard cards, the global menu, and Player recovery all
 * call this controller instead of maintaining parallel refresh implementations.
 */
object TabMaintenanceController {

    fun canRevive(context: Context, tab: VideoTabStore.VideoTab): Boolean {
        val health = TabHealthStore.get(context, tab.id).state
        return health == TabHealthStore.State.NEEDS_REFRESH ||
            tab.preparationState == VideoTabStore.PreparationState.ERROR
    }

    /** Normal dashboard/global revival: only known stale/error tabs are accepted. */
    fun reviveOne(context: Context, tab: VideoTabStore.VideoTab): Boolean =
        enqueueRevival(context, tab, requireKnownStale = true)

    /**
     * Player recovery is an explicit user request after playback has already
     * failed. It must be able to refresh the original page even when the health
     * probe has not yet labelled the persistent tab NEEDS_REFRESH.
     *
     * Playback position/state are persisted before the same tab is queued, so the
     * private resolver can replace only its resolved payload rather than creating
     * a duplicate tab.
     */
    fun reviveFromPlayer(
        context: Context,
        tab: VideoTabStore.VideoTab,
        positionMs: Long,
        playWhenReady: Boolean
    ): Boolean {
        VideoTabStore.updatePlayback(
            id = tab.id,
            positionMs = positionMs.coerceAtLeast(0L),
            playWhenReady = playWhenReady
        )
        return enqueueRevival(context, tab, requireKnownStale = false)
    }

    fun reviveAll(context: Context, tabs: List<VideoTabStore.VideoTab>): Int {
        var queued = 0
        tabs.filter { canRevive(context, it) }.forEach { tab ->
            if (reviveOne(context, tab)) queued += 1
        }
        return queued
    }

    private fun enqueueRevival(
        context: Context,
        tab: VideoTabStore.VideoTab,
        requireKnownStale: Boolean
    ): Boolean {
        if (requireKnownStale && !canRevive(context, tab)) return false

        val pageUrl = TabOriginStore.pageUrl(context, tab).trim()
        if (!isHttpUrl(pageUrl)) {
            TabHealthStore.set(
                context,
                tab.id,
                TabHealthStore.State.NEEDS_ATTENTION,
                context.getString(R.string.original_webpage_unavailable)
            )
            return false
        }

        TabHealthStore.set(context, tab.id, TabHealthStore.State.UNKNOWN)
        TabPreparationManager.cancelScheduled(context.applicationContext, tab.id)
        VideoTabStore.markQueued(tab.id, context.getString(R.string.refresh_requested))
        return TabRevivalCoordinator.enqueue(context, listOf(tab)) > 0
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
}
