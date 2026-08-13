package com.example.vivaldiplayer

import android.content.Context

/** Local, on-device preferences for External Player behavior. */
object AppSettings {
    private const val PREFS_NAME = "external_player_settings"
    private const val KEY_CLEAR_AGE_PROMPTS = "clear_age_prompts"
    private const val KEY_CLEAR_COOKIE_PROMPTS = "clear_cookie_prompts"
    private const val KEY_NETWORK_RETRY = "network_retry"
    private const val KEY_PRELOAD_NEXT_TAB = "preload_next_tab"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun clearAgePrompts(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLEAR_AGE_PROMPTS, true)

    fun setClearAgePrompts(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLEAR_AGE_PROMPTS, enabled).apply()
    }

    fun clearCookiePrompts(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLEAR_COOKIE_PROMPTS, true)

    fun setClearCookiePrompts(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLEAR_COOKIE_PROMPTS, enabled).apply()
    }

    /** Limited retry is used only for ordinary temporary network failures. */
    fun networkRetryEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NETWORK_RETRY, true)

    fun setNetworkRetryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NETWORK_RETRY, enabled).apply()
    }

    /** Preloading means resolving metadata/source URLs, never starting playback. */
    fun preloadNextTab(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRELOAD_NEXT_TAB, true)

    fun setPreloadNextTab(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PRELOAD_NEXT_TAB, enabled).apply()
    }

    const val MAX_TRANSIENT_RETRIES = 2
}
