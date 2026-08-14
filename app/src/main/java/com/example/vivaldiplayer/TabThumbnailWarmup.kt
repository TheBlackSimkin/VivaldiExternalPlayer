package com.example.vivaldiplayer

import android.content.Context

/** Best-effort thumbnail generation for READY tabs without starting playback. */
object TabThumbnailWarmup {
    fun warm(context: Context) {
        VideoTabStore.allTabs()
            .asSequence()
            .filter { it.isReady }
            .forEach { tab ->
                TabThumbnailCapture.captureResolved(context.applicationContext, tab)
            }
    }
}
