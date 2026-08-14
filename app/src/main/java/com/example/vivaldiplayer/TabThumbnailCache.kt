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
        runCatching { File(context.applicationContext.filesDir, DIRECTORY).deleteRecursively() }
    }

    private fun file(context: Context, tabId: String): File =
        File(File(context.applicationContext.filesDir, DIRECTORY), "$tabId.jpg")
}
