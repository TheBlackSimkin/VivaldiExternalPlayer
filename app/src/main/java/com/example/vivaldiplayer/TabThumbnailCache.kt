package com.example.vivaldiplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/** Small app-private JPEG cache keyed by persistent tab ID. */
object TabThumbnailCache {
    private const val DIRECTORY = "tab_thumbnails"

    fun load(context: Context, tabId: String): Bitmap? {
        val file = file(context, tabId)
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun save(context: Context, tabId: String, bitmap: Bitmap) {
        val file = file(context, tabId)
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            }
        }
    }

    fun delete(context: Context, tabId: String) {
        runCatching { file(context, tabId).delete() }
    }

    fun clear(context: Context) {
        runCatching { directory(context).deleteRecursively() }
    }

    /** Remove thumbnails whose persistent tab no longer exists. */
    fun prune(context: Context, validTabIds: Set<String>) {
        directory(context).listFiles()?.forEach { file ->
            val tabId = file.name.removeSuffix(".jpg")
            if (file.extension.equals("jpg", ignoreCase = true) && tabId !in validTabIds) {
                runCatching { file.delete() }
            }
        }
    }

    private fun directory(context: Context): File =
        File(context.applicationContext.filesDir, DIRECTORY)

    private fun file(context: Context, tabId: String): File =
        File(directory(context), "$tabId.jpg")
}
