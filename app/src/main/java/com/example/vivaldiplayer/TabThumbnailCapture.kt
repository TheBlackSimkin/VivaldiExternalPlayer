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
import com.google.common.util.concurrent.ListenableFuture
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
 * FrameExtractor. READY dashboard tabs can use a short-lived FrameExtractor, but
 * 0.3.2 deliberately serializes that background work and suspends/cancels it as
 * soon as PlayerActivity resumes. This avoids competing for hardware decoder
 * resources while the real single ExoPlayer is starting.
 */
object TabThumbnailCapture {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val backgroundLock = Any()

    @Volatile
    private var backgroundAllowed = true

    private var backgroundTabId: String? = null
    private var backgroundExtractor: FrameExtractor? = null
    private var backgroundFuture: ListenableFuture<FrameExtractor.Frame>? = null

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
            onSaved = null,
            background = false
        )
    }

    /** Called when PlayerActivity takes foreground decoder ownership. */
    fun pauseBackgroundCapture() {
        backgroundAllowed = false
        synchronized(backgroundLock) {
            backgroundFuture?.cancel(true)
            backgroundFuture = null
            backgroundExtractor?.close()
            backgroundExtractor = null
            backgroundTabId?.let(inFlight::remove)
            backgroundTabId = null
        }
    }

    /** Re-enable best-effort background previews when the dashboard is foreground. */
    fun resumeBackgroundCapture() {
        backgroundAllowed = true
    }

    /**
     * Generate a thumbnail for one READY dashboard tab without starting playback.
     * Only one background FrameExtractor is allowed at a time. A later dashboard
     * refresh naturally tries the next missing tab after the current request ends.
     */
    fun captureResolved(
        context: Context,
        tab: VideoTabStore.VideoTab,
        onSaved: (() -> Unit)? = null
    ) {
        if (!backgroundAllowed || !tab.isReady || tab.resolvedMediaJson.isBlank()) return
        if (TabThumbnailCache.load(context, tab.id) != null) return

        synchronized(backgroundLock) {
            if (!backgroundAllowed || backgroundExtractor != null || backgroundFuture != null) return
            if (!inFlight.add(tab.id)) return
        }

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

        synchronized(backgroundLock) {
            if (!backgroundAllowed) {
                extractor.close()
                inFlight.remove(tab.id)
                return
            }
            backgroundTabId = tab.id
            backgroundExtractor = extractor
        }

        requestFrame(
            context = context,
            tabId = tab.id,
            extractor = extractor,
            position = choosePosition(C.TIME_UNSET, tab.positionMs),
            closeExtractor = true,
            onSaved = onSaved,
            background = true
        )
    }

    private fun requestFrame(
        context: Context,
        tabId: String,
        extractor: FrameExtractor,
        position: Long,
        closeExtractor: Boolean,
        onSaved: (() -> Unit)?,
        background: Boolean
    ) {
        val request = runCatching { extractor.getFrame(position) }.getOrElse {
            finishRequest(tabId, extractor, closeExtractor, background)
            return
        }

        if (background) {
            synchronized(backgroundLock) {
                if (!backgroundAllowed || backgroundExtractor !== extractor) {
                    request.cancel(true)
                    finishRequest(tabId, extractor, closeExtractor, background)
                    return
                }
                backgroundFuture = request
            }
        }

        request.addListener({
            if (request.isCancelled) {
                finishRequest(tabId, extractor, closeExtractor, background)
                return@addListener
            }

            val bitmap = runCatching { request.get().bitmap }.getOrNull()
            if (bitmap == null) {
                finishRequest(tabId, extractor, closeExtractor, background)
                return@addListener
            }

            /* Always copy the result before any extractor can be closed. */
            val thumbnail = scaledCopy(bitmap)
            Thread {
                try {
                    if (!background || backgroundAllowed) {
                        TabThumbnailCache.save(context.applicationContext, tabId, thumbnail)
                        ContextCompat.getMainExecutor(context).execute { onSaved?.invoke() }
                    }
                } finally {
                    thumbnail.recycle()
                    finishRequest(tabId, extractor, closeExtractor, background)
                }
            }.start()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun finishRequest(
        tabId: String,
        extractor: FrameExtractor,
        closeExtractor: Boolean,
        background: Boolean
    ) {
        if (closeExtractor) runCatching { extractor.close() }
        inFlight.remove(tabId)

        if (background) {
            synchronized(backgroundLock) {
                if (backgroundTabId == tabId) {
                    backgroundFuture = null
                    backgroundExtractor = null
                    backgroundTabId = null
                }
            }
        }
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
