package com.example.vivaldiplayer

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread

/**
 * Owns the private off-screen displays used by BG preparation Activities.
 *
 * No image data is inspected, copied, classified or persisted. ImageReader is
 * used only as the required Surface sink for VirtualDisplay; frames are acquired
 * and immediately closed without reading their planes so the renderer never
 * blocks on a full buffer queue.
 */
object BackgroundVirtualDisplayRegistry {

    private data class Session(
        val virtualDisplay: VirtualDisplay,
        val imageReader: ImageReader
    )

    private val lock = Any()
    private val sessions = mutableMapOf<String, Session>()

    private val drainThread: HandlerThread by lazy {
        HandlerThread("ExternalPlayer-VirtualDisplayDrain").apply { start() }
    }

    private val drainHandler: Handler by lazy {
        Handler(drainThread.looper)
    }

    /**
     * Create one private display and return its display ID.
     *
     * The display is OWN_CONTENT_ONLY and not PUBLIC, so only this app's UID may
     * place windows on it. PRESENTATION marks it as a proper secondary display.
     */
    fun create(context: Context, sessionToken: String): Int? {
        synchronized(lock) {
            sessions[sessionToken]?.let {
                return it.virtualDisplay.display.displayId
            }

            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(720)
            val height = metrics.heightPixels.coerceAtLeast(1280)
            val densityDpi = metrics.densityDpi.coerceAtLeast(160)

            val reader = runCatching {
                ImageReader.newInstance(
                    width,
                    height,
                    PixelFormat.RGBA_8888,
                    3
                )
            }.getOrElse { error ->
                OperationLog.record(
                    context,
                    event = "VIRTUAL_DISPLAY_READER_FAILED",
                    detail = error.message ?: error.toString()
                )
                return null
            }

            reader.setOnImageAvailableListener({ source ->
                /*
                 * Drain and close only. Never call Image.getPlanes(), never copy
                 * pixels, and never save/render/analyse the off-screen content.
                 */
                while (true) {
                    val image = runCatching { source.acquireLatestImage() }.getOrNull()
                        ?: break
                    runCatching { image.close() }
                }
            }, drainHandler)

            val manager = context.getSystemService(DisplayManager::class.java)
            val flags =
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

            val display = runCatching {
                manager.createVirtualDisplay(
                    "ExternalPlayer-$sessionToken",
                    width,
                    height,
                    densityDpi,
                    reader.surface,
                    flags
                )
            }.getOrNull()

            if (display == null) {
                runCatching { reader.close() }
                return null
            }

            sessions[sessionToken] = Session(display, reader)

            OperationLog.record(
                context,
                event = "VIRTUAL_DISPLAY_CREATED",
                detail = "token=$sessionToken display=${display.display.displayId} size=${width}x$height dpi=$densityDpi"
            )

            return display.display.displayId
        }
    }

    fun release(sessionToken: String) {
        val session = synchronized(lock) {
            sessions.remove(sessionToken)
        } ?: return

        runCatching { session.virtualDisplay.release() }
        runCatching { session.imageReader.close() }
    }
}
