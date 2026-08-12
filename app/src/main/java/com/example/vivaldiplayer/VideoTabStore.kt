package com.example.vivaldiplayer

import java.util.UUID

/**
 * Process-local tab/session store for videos opened in External Player.
 *
 * The first tab implementation intentionally does NOT persist across a complete
 * app/process restart. That product decision is still open. While the process is
 * alive, each tab keeps the resolved media payload, playback position and
 * play/pause state. The active PlayerActivity can therefore switch tabs without
 * running the resolver again.
 *
 * Keeping this layer resolver-independent is important: Batch 4 candidate
 * selection remains untouched. A resolver simply supplies its existing JSON and
 * the tab layer remembers it.
 */
object VideoTabStore {

    data class VideoTab(
        val id: String,
        var title: String,
        var resolvedMediaJson: String,
        var positionMs: Long = 0L,
        var playWhenReady: Boolean = true
    )

    private val tabs = mutableListOf<VideoTab>()

    /** Add a newly resolved video as a new independent tab. */
    @Synchronized
    fun createTab(resolvedMediaJson: String): VideoTab {
        val title = runCatching {
            ResolvedMedia.fromJson(resolvedMediaJson).title.trim()
        }.getOrNull().orEmpty().ifBlank { "Video" }

        return VideoTab(
            id = UUID.randomUUID().toString(),
            title = title,
            resolvedMediaJson = resolvedMediaJson
        ).also { tabs.add(it) }
    }

    @Synchronized
    fun allTabs(): List<VideoTab> = tabs.map { it.copy() }

    @Synchronized
    fun get(id: String): VideoTab? = tabs.firstOrNull { it.id == id }?.copy()

    /**
     * Save the currently active playback state. A quality switch also updates
     * resolvedMediaJson, so returning to this tab preserves that chosen source
     * whenever practical.
     */
    @Synchronized
    fun update(
        id: String,
        resolvedMediaJson: String,
        positionMs: Long,
        playWhenReady: Boolean
    ) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        tab.resolvedMediaJson = resolvedMediaJson
        tab.positionMs = positionMs.coerceAtLeast(0L)
        tab.playWhenReady = playWhenReady
        tab.title = runCatching {
            ResolvedMedia.fromJson(resolvedMediaJson).title.trim()
        }.getOrNull().orEmpty().ifBlank { tab.title.ifBlank { "Video" } }
    }

    @Synchronized
    fun close(id: String) {
        tabs.removeAll { it.id == id }
    }

    /** Pick a sensible neighbor after closing the active tab. */
    @Synchronized
    fun neighborAfterClose(id: String): VideoTab? {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return tabs.lastOrNull()?.copy()

        tabs.removeAt(index)
        if (tabs.isEmpty()) return null
        return tabs[index.coerceAtMost(tabs.lastIndex)].copy()
    }
}
