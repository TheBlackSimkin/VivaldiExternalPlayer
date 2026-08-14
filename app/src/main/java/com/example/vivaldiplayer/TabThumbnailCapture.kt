package com.example.vivaldiplayer

import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.inspector.frame.FrameExtractor
import androidx.media3.ui.PlayerView
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates one stable pseudo-random thumbnail while a tab is actively playable.
 * It reuses PlayerActivity's existing FrameExtractor, so thumbnail generation
 * creates no additional ExoPlayer and no separate playback session.
 */
object TabThumbnailCapture {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun captureIfMissing(activity: PlayerActivity, tabId: String) {
        if (TabThumbnailCache.load(activity, tabId) != null || !inFlight.add(tabId)) return

        val extractor = runCatching {
            PlayerActivity::class.java.getDeclaredField("frameExtractor").run {
                isAccessible = true
                get(activity) as? FrameExtractor
            }
        }.getOrNull()

        if (extractor == null) {
            inFlight.remove(tabId)
            return
        }

        val player = activity.findViewById<PlayerView>(R.id.player_view)?.player
        val duration = player?.duration ?: C.TIME_UNSET
        val current = player?.currentPosition ?: 0L
        val position = choosePosition(tabId, duration, current)

        val request = runCatching { extractor.getFrame(position) }.getOrElse {
            inFlight.remove(tabId)
            return
        }

        request.addListener({
            val bitmap = runCatching { request.get().bitmap }.getOrNull()
            if (bitmap == null) {
                inFlight.remove(tabId)
                return@addListener
            }

            val scaled = scale(bitmap)
            Thread {
                try {
                    TabThumbnailCache.save(activity.applicationContext, tabId, scaled)
                } finally {
                    if (scaled !== bitmap) scaled.recycle()
                    inFlight.remove(tabId)
                }
            }.start()
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun choosePosition(tabId: String, duration: Long, current: Long): Long {
        if (duration != C.TIME_UNSET && duration > 4_000L) {
            val hash = tabId.hashCode().toLong() and 0x7fffffffL
            val percent = 18L + (hash % 55L)
            return ((duration * percent) / 100L)
                .coerceIn(1_000L, (duration - 1_000L).coerceAtLeast(1_000L))
        }
        return current.takeIf { it > 2_000L } ?: 10_000L
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= 480 || bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val height = ((bitmap.height.toFloat() / bitmap.width.toFloat()) * 480f)
            .toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, 480, height, true)
    }
}
