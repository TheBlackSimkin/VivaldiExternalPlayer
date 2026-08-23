package com.example.vivaldiplayer

/**
 * Tiny process-local navigation handoff from PlayerActivity back to MainActivity.
 *
 * The persistent tab ID is stable across dashboard refreshes, unlike an adapter
 * position which may move while revival/status work updates the list. MainActivity
 * consumes the ID once and scrolls the outer dashboard to that tab card.
 */
object DashboardReturnState {
    @Volatile
    private var pendingTabId: String? = null

    fun remember(tabId: String?) {
        pendingTabId = tabId?.takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun consume(): String? {
        val value = pendingTabId
        pendingTabId = null
        return value
    }
}
