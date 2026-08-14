package com.example.vivaldiplayer

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.inspector.frame.FrameExtractor
import androidx.media3.ui.PlayerView
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates one stable local thumbnail per persistent tab.
 *
 * The previous pseudo-random timestamp was intentionally removed after device
 * QA: it could land on an unhelpful frame and made the dashboard feel arbitrary.
 * The new heuristic uses the user's saved/current position when meaningful;
 * otherwise it chooses roughly one third into a known-duration video. No frame
 * content is classified or sent anywhere.
 *
 * No ExoPlayer is created here. Active tabs reuse PlayerActivity's existing
 * FrameExtractor; READY background tabs can use a short-lived FrameExtractor
 * against their already-resolved source so a preview can exist before playback.
 */
object TabThumbnailCapture {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** Fast path for the currently playing tab. */
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
        requestFrame(
            context = activity,
            tabId = tabId,
            extractor = extractor,
            position = choosePosition(duration, current),
            closeExtractor = false,
            onSaved = null
        )
    }

    /**
     * Generate a thumbnail for any READY tab without starting playback.
     * This is best-effort because temporary media URLs can expire independently
     * of the saved tab; a later active-tab capture can try again if necessary.
     */
    fun captureResolved(
        context: Context,
        tab: VideoTabStore.VideoTab,
        onSaved: (() -> Unit)? = null
    ) {
        if (!tab.isReady || tab.resolvedMediaJson.isBlank()) return
        if (TabThumbnailCache.load(context, tab.id) != null || !inFlight.add(tab.id)) return

        val resolved = runCatching { ResolvedMedia.fromJson(tab.resolvedMediaJson) }.getOrNull()
        val source = resolved?.primarySource
        if (source == null || source.url.isBlank()) {
            inFlight.remove(tab.id)
            return
        }

        val item = MediaItem.Builder()
            .setUri(source.url)
            .apply { source.mimeType?.let { setMimeType(normalizeMime(it)) } }
            .build()

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(source.headers)
        val mediaFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(http)

        val extractor = runCatching {
            FrameExtractor.Builder(context, item)
                .setMediaSourceFactory(mediaFactory)
                .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                .build()
        }.getOrElse {
            inFlight.remove(tab.id)
            return
        }

        requestFrame(
            context = context,
            tabId = tab.id,
            extractor = extractor,
            position = choosePosition(C.TIME_UNSET, tab.positionMs),
            closeExtractor = true,
            onSaved = onSaved
        )
    }

    private fun requestFrame(
        context: Context,
        tabId: String,
        extractor: FrameExtractor,
        position: Long,
        closeExtractor: Boolean,
        onSaved: (() -> Unit)?
    ) {
        val request = runCatching { extractor.getFrame(position) }.getOrElse {
            if (closeExtractor) extractor.close()
            inFlight.remove(tabId)
            return
        }

        request.addListener({
            val bitmap = runCatching { request.get().bitmap }.getOrNull()
            if (bitmap == null) {
                if (closeExtractor) extractor.close()
                inFlight.remove(tabId)
                return@addListener
            }

            /* Always copy the result before any extractor can be closed. */
            val thumbnail = scaledCopy(bitmap)
            Thread {
                try {
                    TabThumbnailCache.save(context.applicationContext, tabId, thumbnail)
                    ContextCompat.getMainExecutor(context).execute { onSaved?.invoke() }
                } finally {
                    thumbnail.recycle()
                    if (closeExtractor) extractor.close()
                    inFlight.remove(tabId)
                }
            }.start()
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Prefer something representative and stable rather than random.
     * - If the user already has a meaningful saved/current position, use it.
     * - With a known duration, use ~35% of the video, away from opening/credits.
     * - With unknown duration and no progress, use a conservative 15-second mark.
     */
    private fun choosePosition(duration: Long, current: Long): Long {
        if (current >= 5_000L) {
            if (duration == C.TIME_UNSET || duration <= 0L) return current
            return current.coerceAtMost((duration - 1_000L).coerceAtLeast(1_000L))
        }

        if (duration != C.TIME_UNSET && duration > 5_000L) {
            return ((duration * 35L) / 100L)
                .coerceIn(2_000L, (duration - 1_000L).coerceAtLeast(2_000L))
        }

        return 15_000L
    }

    private fun scaledCopy(bitmap: Bitmap): Bitmap {
        val sourceWidth = bitmap.width.coerceAtLeast(1)
        val sourceHeight = bitmap.height.coerceAtLeast(1)
        val width = minOf(480, sourceWidth)
        val height = ((sourceHeight.toFloat() / sourceWidth.toFloat()) * width)
            .toInt().coerceAtLeast(1)

        if (width == sourceWidth && height == sourceHeight) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        return if (scaled === bitmap) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            scaled
        }
    }

    private fun normalizeMime(value: String): String = when (value) {
        "application/x-mpegURL" -> MimeTypes.APPLICATION_M3U8
        "application/dash+xml" -> MimeTypes.APPLICATION_MPD
        else -> value
    }
}
