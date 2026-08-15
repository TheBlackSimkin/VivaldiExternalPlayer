package com.example.vivaldiplayer

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread

/**
 * Owns the app-private off-screen displays used by BG preparation.
 *
 * #212 proved that creating this private display works on the real phone; what
 * failed there was launching a normal Activity onto it. The post-#227 design
 * reuses the successful display primitive but attaches a service-owned
 * Presentation/WebView instead of an Activity.
 *
 * No image data is inspected, copied, classified or persisted. ImageReader is
 * used only as the Surface sink required by VirtualDisplay. Frames are acquired
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
     * Create one private presentation display and return its display ID.
     *
     * OWN_CONTENT_ONLY keeps it private to this app instead of mirroring the
     * physical display. PRESENTATION makes it suitable for Presentation windows.
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
                 * pixels, and never save/render/analyse off-screen content.
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
                detail = "token=$sessionToken display=${display.display.displayId} size=${width}x$height dpi=$densityDpi private=true presentation=true"
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
