package com.example.vivaldiplayer

import android.content.Context

/**
 * Permanent original-page identity for a tab.
 *
 * Resolved media URLs and even resolver-reported webpage URLs are temporary playback state.
 * This store remembers the exact HTTP(S) page URL the app originally accepted for a tab and
 * never replaces it unless the entry did not exist yet. Revival/Favorites should prefer this
 * value and only fall back to VideoTabStore.sourceUrl for pre-migration tabs.
 */
object TabOriginStore {
    private const val PREFS_NAME = "tab_origin_store"

    fun remember(context: Context, tabId: String, originalPageUrl: String) {
        val cleanId = tabId.trim()
        val cleanUrl = originalPageUrl.trim()
        if (cleanId.isBlank() || !isHttpUrl(cleanUrl)) return

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(cleanId)) return
        prefs.edit().putString(cleanId, cleanUrl).apply()
    }

    fun get(context: Context, tabId: String): String? =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(tabId, null)
            ?.trim()
            ?.takeIf(::isHttpUrl)

    /** Best-effort migration for tabs created before this store existed. */
    fun ensureFallback(context: Context, tab: VideoTabStore.VideoTab) {
        if (get(context, tab.id) == null) remember(context, tab.id, tab.sourceUrl)
    }

    fun pageUrl(context: Context, tab: VideoTabStore.VideoTab): String =
        get(context, tab.id) ?: tab.sourceUrl.trim()

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
}
