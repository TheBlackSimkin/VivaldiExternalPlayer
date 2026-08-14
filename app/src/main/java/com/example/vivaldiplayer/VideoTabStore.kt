package com.example.vivaldiplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persistent tab/session store for ExternalPlayer.
 *
 * In addition to the resolved source and playback position, each tab now keeps
 * its user-selected manual quality (when any) and the ACTUAL video height which
 * Media3 most recently reported as selected. Keeping those two values separate
 * is important: the UI must never claim a requested quality was really playing
 * until Media3 confirms it.
 */
object VideoTabStore {

    enum class PreparationState {
        QUEUED,
        RESOLVING,
        READY,
        NEEDS_ATTENTION,
        ERROR
    }

    data class VideoTab(
        val id: String,
        var title: String,
        var sourceUrl: String,
        var resolvedMediaJson: String,
        var positionMs: Long = 0L,
        var playWhenReady: Boolean = true,
        var preparationState: PreparationState = PreparationState.READY,
        var lastError: String = "",
        var manualQualityHeight: Int? = null,
        var actualQualityHeight: Int? = null,
        var createdAtMs: Long = System.currentTimeMillis(),
        var updatedAtMs: Long = System.currentTimeMillis()
    ) {
        val isReady: Boolean
            get() = preparationState == PreparationState.READY && resolvedMediaJson.isNotBlank()
    }

    private const val PREFS_NAME = "video_tab_store"
    private const val KEY_TABS = "tabs_json_v2"

    private val tabs = mutableListOf<VideoTab>()
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return

        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadLocked()

        /* A killed preparation activity/worker cannot remain truthfully RESOLVING. */
        var changed = false
        tabs.forEach { tab ->
            if (tab.preparationState == PreparationState.RESOLVING) {
                tab.preparationState = PreparationState.QUEUED
                tab.updatedAtMs = System.currentTimeMillis()
                changed = true
            }
        }
        if (changed) persistLocked()
    }

    @Synchronized
    fun createTab(resolvedMediaJson: String): VideoTab {
        val resolved = runCatching { ResolvedMedia.fromJson(resolvedMediaJson) }.getOrNull()
        val title = resolved?.title?.trim().orEmpty().ifBlank { "Video" }
        val sourceUrl = resolved?.webpageUrl?.trim().orEmpty()

        return VideoTab(
            id = UUID.randomUUID().toString(),
            title = title,
            sourceUrl = sourceUrl,
            resolvedMediaJson = resolvedMediaJson,
            actualQualityHeight = resolved?.displayedHeight?.takeIf { it > 0 },
            preparationState = PreparationState.READY
        ).also {
            tabs.add(it)
            persistLocked()
        }.copy()
    }

    @Synchronized
    fun createPendingTab(sourceUrl: String, title: String = "Video"): VideoTab {
        return VideoTab(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Video" },
            sourceUrl = sourceUrl.trim(),
            resolvedMediaJson = "",
            playWhenReady = true,
            preparationState = PreparationState.QUEUED
        ).also {
            tabs.add(it)
            persistLocked()
        }.copy()
    }

    @Synchronized
    fun allTabs(): List<VideoTab> = tabs.map { it.copy() }

    @Synchronized
    fun get(id: String): VideoTab? = tabs.firstOrNull { it.id == id }?.copy()

    @Synchronized
    fun markQueued(id: String, message: String = "") {
        mutateState(id, PreparationState.QUEUED, message)
    }

    @Synchronized
    fun markResolving(id: String) {
        mutateState(id, PreparationState.RESOLVING, "")
    }

    /** Store a completed resolver payload so selecting READY never resolves again. */
    @Synchronized
    fun markReady(id: String, resolvedMediaJson: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val resolved = runCatching { ResolvedMedia.fromJson(resolvedMediaJson) }.getOrNull()

        tab.resolvedMediaJson = resolvedMediaJson
        tab.sourceUrl = resolved?.webpageUrl?.trim().orEmpty().ifBlank { tab.sourceUrl }
        tab.title = resolved?.title?.trim().orEmpty().ifBlank { tab.title.ifBlank { "Video" } }
        tab.actualQualityHeight = resolved?.displayedHeight?.takeIf { it > 0 } ?: tab.actualQualityHeight
        tab.preparationState = PreparationState.READY
        tab.lastError = ""
        tab.updatedAtMs = System.currentTimeMillis()
        persistLocked()
    }

    @Synchronized
    fun markNeedsAttention(id: String, message: String = "") {
        mutateState(id, PreparationState.NEEDS_ATTENTION, message)
    }

    @Synchronized
    fun markError(id: String, message: String = "") {
        mutateState(id, PreparationState.ERROR, message)
    }

    private fun mutateState(id: String, state: PreparationState, message: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        tab.preparationState = state
        tab.lastError = message
        tab.updatedAtMs = System.currentTimeMillis()
        persistLocked()
    }

    /** Persist the user's quality MODE separately from Media3's observed result. */
    @Synchronized
    fun setManualQuality(id: String, height: Int?) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        tab.manualQualityHeight = height?.takeIf { it > 0 }
        tab.updatedAtMs = System.currentTimeMillis()
        persistLocked()
    }

    /** Called only from an actual Media3 track observation. */
    @Synchronized
    fun setActualQuality(id: String, height: Int?) {
        val normalized = height?.takeIf { it > 0 }
        val tab = tabs.firstOrNull { it.id == id } ?: return
        if (tab.actualQualityHeight == normalized) return
        tab.actualQualityHeight = normalized
        tab.updatedAtMs = System.currentTimeMillis()
        persistLocked()
    }

    /** Move a tab one position in the user-visible dashboard order. */
    @Synchronized
    fun move(id: String, delta: Int): Boolean {
        if (delta == 0) return false
        val from = tabs.indexOfFirst { it.id == id }
        if (from < 0) return false
        val to = (from + delta).coerceIn(0, tabs.lastIndex)
        if (to == from) return false

        val tab = tabs.removeAt(from)
        tabs.add(to, tab)
        tab.updatedAtMs = System.currentTimeMillis()
        persistLocked()
        return true
    }

    /** Save active playback state without changing per-tab quality preference. */
    @Synchronized
    fun update(
        id: String,
        resolvedMediaJson: String,
        positionMs: Long,
        playWhenReady: Boolean
    ) {
        val tab = tabs.firstOrNull { it.id == id } ?: return

        if (resolvedMediaJson.isNotBlank()) {
            tab.resolvedMediaJson = resolvedMediaJson
            tab.preparationState = PreparationState.READY
            tab.lastError = ""

            val resolved = runCatching { ResolvedMedia.fromJson(resolvedMediaJson) }.getOrNull()
            tab.title = resolved?.title?.trim().orEmpty().ifBlank { tab.title.ifBlank { "Video" } }
            tab.sourceUrl = resolved?.webpageUrl?.trim().orEmpty().ifBlank { tab.sourceUrl }
        }

        tab.positionMs = positionMs.coerceAtLeast(0L)
        tab.playWhenReady = playWhenReady
        tab.updatedAtMs = System.currentTimeMillis()
        persistLocked()
    }

    @Synchronized
    fun updatePlayback(id: String, positionMs: Long, playWhenReady: Boolean) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        tab.positionMs = positionMs.coerceAtLeast(0L)
        tab.playWhenReady = playWhenReady
        tab.updatedAtMs = System.currentTimeMillis()
        persistLocked()
    }

    @Synchronized
    fun close(id: String) {
        tabs.removeAll { it.id == id }
        persistLocked()
    }

    @Synchronized
    fun clearAll() {
        tabs.clear()
        persistLocked()
    }

    @Synchronized
    fun neighborAfterClose(id: String): VideoTab? {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return tabs.lastOrNull()?.copy()

        tabs.removeAt(index)
        persistLocked()
        if (tabs.isEmpty()) return null
        return tabs[index.coerceAtMost(tabs.lastIndex)].copy()
    }

    @Synchronized
    fun nextAfter(id: String): VideoTab? {
        if (tabs.size < 2) return null
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return tabs.firstOrNull()?.copy()
        return tabs[(index + 1) % tabs.size].copy()
    }

    private fun persistLocked() {
        val target = prefs ?: return
        val array = JSONArray()

        tabs.forEach { tab ->
            array.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("title", tab.title)
                    .put("source_url", tab.sourceUrl)
                    .put("resolved_media_json", tab.resolvedMediaJson)
                    .put("position_ms", tab.positionMs)
                    .put("play_when_ready", tab.playWhenReady)
                    .put("preparation_state", tab.preparationState.name)
                    .put("last_error", tab.lastError)
                    .put("manual_quality_height", tab.manualQualityHeight ?: JSONObject.NULL)
                    .put("actual_quality_height", tab.actualQualityHeight ?: JSONObject.NULL)
                    .put("created_at_ms", tab.createdAtMs)
                    .put("updated_at_ms", tab.updatedAtMs)
            )
        }

        target.edit().putString(KEY_TABS, array.toString()).apply()
    }

    private fun loadLocked() {
        tabs.clear()
        val raw = prefs?.getString(KEY_TABS, null).orEmpty()
        if (raw.isBlank()) return

        runCatching { JSONArray(raw) }
            .onSuccess { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isBlank()) continue

                    val state = runCatching {
                        PreparationState.valueOf(item.optString("preparation_state", "READY"))
                    }.getOrDefault(PreparationState.ERROR)

                    tabs += VideoTab(
                        id = id,
                        title = item.optString("title", "Video").trim().ifBlank { "Video" },
                        sourceUrl = item.optString("source_url").trim(),
                        resolvedMediaJson = item.optString("resolved_media_json"),
                        positionMs = item.optLong("position_ms", 0L).coerceAtLeast(0L),
                        playWhenReady = item.optBoolean("play_when_ready", true),
                        preparationState = state,
                        lastError = item.optString("last_error"),
                        manualQualityHeight = item.optionalPositiveInt("manual_quality_height"),
                        actualQualityHeight = item.optionalPositiveInt("actual_quality_height"),
                        createdAtMs = item.optLong("created_at_ms", System.currentTimeMillis()),
                        updatedAtMs = item.optLong("updated_at_ms", System.currentTimeMillis())
                    )
                }
            }
            .onFailure {
                tabs.clear()
                prefs?.edit()?.remove(KEY_TABS)?.apply()
            }
    }

    private fun JSONObject.optionalPositiveInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key, 0).takeIf { it > 0 }
    }
}
