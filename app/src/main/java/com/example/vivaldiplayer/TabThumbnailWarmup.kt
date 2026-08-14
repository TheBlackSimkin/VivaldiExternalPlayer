package com.example.vivaldiplayer

import android.content.Context

/** Best-effort thumbnail generation for READY tabs without starting playback. */
object TabThumbnailWarmup {
    fun warm(context: Context) {
        val tabs = VideoTabStore.allTabs()
        TabThumbnailCache.prune(context, tabs.map { it.id }.toSet())

        tabs.asSequence()
            .filter { it.isReady }
            .forEach { tab ->
                TabThumbnailCapture.captureResolved(context.applicationContext, tab)
            }
    }
}
