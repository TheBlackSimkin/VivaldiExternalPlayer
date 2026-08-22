package com.example.vivaldiplayer

import android.content.Context
import org.json.JSONObject

/**
 * Lightweight user-facing health state for persisted tabs.
 *
 * This is deliberately separate from [VideoTabStore.PreparationState]. PreparationState
 * describes resolver/background lifecycle, while this store answers the simpler question
 * "does the currently cached playback source still look usable?". Keeping them separate
 * avoids disturbing the protected preparation architecture.
 */
object TabHealthStore {

    enum class State {
        UNKNOWN,
        CHECKING,
        READY,
        NEEDS_REFRESH,
        UNAVAILABLE,
        NEEDS_ATTENTION
    }

    data class Status(
        val state: State = State.UNKNOWN,
        val lastCheckedAtMs: Long = 0L,
        val consecutiveFailures: Int = 0,
        val detail: String = ""
    )

    private const val PREFS_NAME = "tab_health_store"

    fun get(context: Context, tabId: String): Status {
        if (tabId.isBlank()) return Status()
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(tabId, null)
            .orEmpty()
        if (raw.isBlank()) return Status()

        return runCatching {
            val json = JSONObject(raw)
            Status(
                state = runCatching {
                    State.valueOf(json.optString("state", State.UNKNOWN.name))
                }.getOrDefault(State.UNKNOWN),
                lastCheckedAtMs = json.optLong("last_checked_at_ms", 0L),
                consecutiveFailures = json.optInt("consecutive_failures", 0).coerceAtLeast(0),
                detail = json.optString("detail").take(180)
            )
        }.getOrDefault(Status())
    }

    fun set(
        context: Context,
        tabId: String,
        state: State,
        detail: String = "",
        checkedAtMs: Long = System.currentTimeMillis(),
        consecutiveFailures: Int = 0
    ) {
        if (tabId.isBlank()) return
        val json = JSONObject()
            .put("state", state.name)
            .put("last_checked_at_ms", checkedAtMs)
            .put("consecutive_failures", consecutiveFailures.coerceAtLeast(0))
            .put("detail", detail.take(180))

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(tabId, json.toString())
            .apply()
    }

    fun markChecking(context: Context, tabId: String) {
        val previous = get(context, tabId)
        set(
            context = context,
            tabId = tabId,
            state = State.CHECKING,
            detail = previous.detail,
            checkedAtMs = previous.lastCheckedAtMs,
            consecutiveFailures = previous.consecutiveFailures
        )
    }

    fun clear(context: Context, tabId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(tabId)
            .apply()
    }
}
