package com.example.vivaldiplayer

import android.content.Context

/**
 * Single authority for deciding whether a persistent tab needs a source revival
 * and for enqueueing that revival through the protected service/private-display
 * architecture.
 *
 * Keep UI classes dumb: a card, the global menu, and future recovery surfaces
 * should all call this controller instead of reimplementing stale-tab rules.
 */
object TabMaintenanceController {

    fun canRevive(context: Context, tab: VideoTabStore.VideoTab): Boolean {
        val health = TabHealthStore.get(context, tab.id).state
        return health == TabHealthStore.State.NEEDS_REFRESH ||
            tab.preparationState == VideoTabStore.PreparationState.ERROR
    }

    fun reviveOne(context: Context, tab: VideoTabStore.VideoTab): Boolean {
        if (!canRevive(context, tab)) return false

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
        VideoTabStore.markQueued(tab.id, context.getString(R.string.refresh_requested))
        TabPreparationManager.cancelScheduled(context.applicationContext, tab.id)
        return TabRevivalCoordinator.enqueue(context, listOf(tab)) > 0
    }

    fun reviveAll(context: Context, tabs: List<VideoTabStore.VideoTab>): Int {
        var queued = 0
        tabs.filter { canRevive(context, it) }.forEach { tab ->
            if (reviveOne(context, tab)) queued += 1
        }
        return queued
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
}
