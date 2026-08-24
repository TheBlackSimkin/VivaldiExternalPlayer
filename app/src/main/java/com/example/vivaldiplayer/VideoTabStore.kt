package com.example.vivaldiplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persistent tab/session store for ExternalPlayer.
 *
 * There are intentionally TWO local lists:
 *
 * 1. [tabs] contains the tabs which are still open. These are restored
 *    automatically after an app/process restart. There is no manual "reload"
 *    step because MainActivity always reads this persisted list directly.
 *
 * 2. [recentlyClosed] is a browser-like bounded recovery history. Closing one
 *    tab, or closing many tabs through the UI, archives snapshots here in close
 *    order so the user can restore them later. The history is deliberately much
 *    larger than the old 12-entry cap, while still bounded to avoid unbounded
 *    SharedPreferences growth.
 *
 * In addition to the resolved source and playback position, each tab keeps:
 * - the user's requested manual quality and Media3's actually observed height;
 * - non-content technical preparation timestamps/stages.
 *
 * The technical fields are deliberately boring lifecycle diagnostics. They never
 * contain thumbnails, media frames, page text, credentials or other media
 * content. Their purpose is to tell us whether Android reached the share host,
 * direct resolver, browser fallback, or READY without relying on a tab click.
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
        var updatedAtMs: Long = System.currentTimeMillis(),
        var preparationRequestedAtMs: Long = 0L,
        var preparationHostCreatedAtMs: Long = 0L,
        var directResolverStartedAtMs: Long = 0L,
        var directResolverFinishedAtMs: Long = 0L,
        var browserStageRequestedAtMs: Long = 0L,
        var browserWebViewCreatedAtMs: Long = 0L,
        var browserDiscoveryStartedAtMs: Long = 0L,
        var readyAtMs: Long = 0L,
        var lastTechnicalPreparationStage: String = "",
        var lastTechnicalStageAtMs: Long = 0L
    ) {
        val isReady: Boolean
            get() = preparationState == PreparationState.READY && resolvedMediaJson.isNotBlank()
    }

    private const val PREFS_NAME = "video_tab_store"
    private const val KEY_TABS = "tabs_json_v2"
    private const val KEY_RECENTLY_CLOSED = "recently_closed_tabs_json_v1"

    /** Browser-like history: large enough for real multi-tab cleanup, still deliberately bounded. */
    private const val MAX_RECENTLY_CLOSED = 100

    private val tabs = mutableListOf<VideoTab>()
    private val recentlyClosed = mutableListOf<VideoTab>()
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return

        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadLocked()

        /* A killed preparation Activity/Worker cannot remain truthfully RESOLVING. */
        var changed = false
        tabs.forEach { tab ->
            if (tab.preparationState == PreparationState.RESOLVING) {
                tab.preparationState = PreparationState.QUEUED
                tab.updatedAtMs = System.currentTimeMillis()
                setTechnicalStageLocked(tab, "PROCESS_RESTART_QUEUED")
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
        val now = System.currentTimeMillis()

        return VideoTab(
            id = UUID.randomUUID().toString(),
            title = title,
            sourceUrl = sourceUrl,
            resolvedMediaJson = resolvedMediaJson,
            actualQualityHeight = resolved?.displayedHeight?.takeIf { it > 0 },
            preparationState = PreparationState.READY,
            createdAtMs = now,
            updatedAtMs = now,
            readyAtMs = now,
            lastTechnicalPreparationStage = "READY",
            lastTechnicalStageAtMs = now
        ).also {
            tabs.add(it)
            persistLocked()
        }.copy()
    }

    @Synchronized
    fun createPendingTab(sourceUrl: String, title: String = "Video"): VideoTab {
        val now = System.currentTimeMillis()
        return VideoTab(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Video" },
            sourceUrl = sourceUrl.trim(),
            resolvedMediaJson = "",
            playWhenReady = true,
            preparationState = PreparationState.QUEUED,
            createdAtMs = now,
            updatedAtMs = now,
            lastTechnicalPreparationStage = "TAB_CREATED",
            lastTechnicalStageAtMs = now
        ).also {
            tabs.add(it)
            persistLocked()
        }.copy()
    }

    @Synchronized
    fun allTabs(): List<VideoTab> = tabs.map { it.copy() }

    @Synchronized
    fun recentlyClosedTabs(): List<VideoTab> = recentlyClosed.map { it.copy() }

    @Synchronized
    fun get(id: String): VideoTab? = tabs.firstOrNull { it.id == id }?.copy()

    /**
     * Restore one genuinely closed tab. Its same id is reused so its position,
     * resolved payload and quality preference survive the round trip.
     */
    @Synchronized
    fun restoreClosed(id: String): VideoTab? {
        val index = recentlyClosed.indexOfFirst { it.id == id }
        if (index < 0) return null

        val restored = recentlyClosed.removeAt(index)
        if (tabs.any { it.id == restored.id }) {
            persistLocked()
            return tabs.first { it.id == restored.id }.copy()
        }

        val now = System.currentTimeMillis()
        restored.updatedAtMs = now
        setTechnicalStageLocked(restored, "RESTORED_FROM_RECENTLY_CLOSED", now)
        tabs.add(restored)
        persistLocked()
        return restored.copy()
    }

    /** The explicit share/retry/preload path requested preparation for this tab. */
    @Synchronized
    fun markPreparationRequested(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        if (tab.preparationRequestedAtMs <= 0L) tab.preparationRequestedAtMs = now
        tab.updatedAtMs = now
        setTechnicalStageLocked(tab, "PREPARATION_REQUESTED", now)
        persistLocked()
    }

    /** Android actually created the Activity which owns preparation. */
    @Synchronized
    fun markPreparationHostCreated(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        tab.preparationHostCreatedAtMs = now
        tab.updatedAtMs = now
        setTechnicalStageLocked(tab, "PREPARATION_HOST_CREATED", now)
        persistLocked()
    }

    @Synchronized
    fun markDirectResolverStarted(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        tab.directResolverStartedAtMs = now
        tab.updatedAtMs = now
        setTechnicalStageLocked(tab, "DIRECT_STARTED", now)
        persistLocked()
    }

    @Synchronized
    fun markDirectResolverFinished(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        tab.directResolverFinishedAtMs = now
        tab.updatedAtMs = now
        setTechnicalStageLocked(tab, "DIRECT_FINISHED", now)
        persistLocked()
    }

    @Synchronized
    fun markBrowserStageRequested(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        tab.browserStageRequestedAtMs = now
        tab.updatedAtMs = now
        setTechnicalStageLocked(tab, "BROWSER_REQUESTED", now)
        persistLocked()
    }

    @Synchronized
    fun markBrowserWebViewCreated(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        tab.browserWebViewCreatedAtMs = now
        tab.updatedAtMs = now
        setTechnicalStageLocked(tab, "BROWSER_WEBVIEW_CREATED", now)
        persistLocked()
    }

    @Synchronized
    fun markBrowserDiscoveryStarted(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        tab.browserDiscoveryStartedAtMs = now
        tab.updatedAtMs = now
        setTechnicalStageLocked(tab, "BROWSER_DISCOVERY_STARTED", now)
        persistLocked()
    }

    /** Used for rare technical recovery markers which do not deserve new fields. */
    @Synchronized
    fun markTechnicalStage(id: String, stage: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        tab.updatedAtMs = now
        setTechnicalStageLocked(tab, stage, now)
        persistLocked()
    }

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
        val now = System.currentTimeMillis()

        tab.resolvedMediaJson = resolvedMediaJson
        tab.sourceUrl = resolved?.webpageUrl?.trim().orEmpty().ifBlank { tab.sourceUrl }
        tab.title = resolved?.title?.trim().orEmpty().ifBlank { tab.title.ifBlank { "Video" } }
        tab.actualQualityHeight = resolved?.displayedHeight?.takeIf { it > 0 } ?: tab.actualQualityHeight
        tab.preparationState = PreparationState.READY
        tab.lastError = ""
        tab.readyAtMs = now
        tab.updatedAtMs = now
        setTechnicalStageLocked(tab, "READY", now)
        persistLocked()
    }

    @Synchronized
    fun markNeedsAttention(id: String, message: String = "") {
        mutateState(id, PreparationState.NEEDS_ATTENTION, message)
        markTechnicalStage(id, "NEEDS_ATTENTION")
    }

    @Synchronized
    fun markError(id: String, message: String = "") {
        mutateState(id, PreparationState.ERROR, message)
        markTechnicalStage(id, "ERROR")
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
            if (tab.readyAtMs <= 0L) tab.readyAtMs = System.currentTimeMillis()
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

    /**
     * Closing one tab means the user may reasonably want an Undo/Restore path.
     * Keep a bounded local snapshot rather than silently deleting it forever.
     */
    @Synchronized
    fun close(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        archiveClosedLocked(tabs.removeAt(index))
        persistLocked()
    }

    /**
     * Low-level hard clear. The normal UI Close All intentionally calls [close]
     * per tab so its items enter the same browser-like Recently Closed history.
     */
    @Synchronized
    fun clearAll() {
        tabs.clear()
        persistLocked()
    }

    @Synchronized
    fun clearRecentlyClosed() {
        recentlyClosed.clear()
        persistLocked()
    }

    @Synchronized
    fun neighborAfterClose(id: String): VideoTab? {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return tabs.lastOrNull()?.copy()

        archiveClosedLocked(tabs.removeAt(index))
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

    private fun archiveClosedLocked(tab: VideoTab) {
        recentlyClosed.removeAll { it.id == tab.id }
        recentlyClosed.add(0, tab.copy())
        while (recentlyClosed.size > MAX_RECENTLY_CLOSED) {
            recentlyClosed.removeAt(recentlyClosed.lastIndex)
        }
    }

    private fun setTechnicalStageLocked(
        tab: VideoTab,
        stage: String,
        atMs: Long = System.currentTimeMillis()
    ) {
        tab.lastTechnicalPreparationStage = stage.trim().take(80)
        tab.lastTechnicalStageAtMs = atMs
    }

    private fun persistLocked() {
        val target = prefs ?: return
        val openArray = JSONArray()
        val closedArray = JSONArray()

        tabs.forEach { tab -> openArray.put(tab.toJsonObject()) }
        recentlyClosed.forEach { tab -> closedArray.put(tab.toJsonObject()) }

        target.edit()
            .putString(KEY_TABS, openArray.toString())
            .putString(KEY_RECENTLY_CLOSED, closedArray.toString())
            .apply()
    }

    private fun loadLocked() {
        tabs.clear()
        recentlyClosed.clear()
        loadListLocked(KEY_TABS, tabs)
        loadListLocked(KEY_RECENTLY_CLOSED, recentlyClosed)

        while (recentlyClosed.size > MAX_RECENTLY_CLOSED) {
            recentlyClosed.removeAt(recentlyClosed.lastIndex)
        }
    }

    private fun loadListLocked(key: String, target: MutableList<VideoTab>) {
        val raw = prefs?.getString(key, null).orEmpty()
        if (raw.isBlank()) return

        runCatching { JSONArray(raw) }
            .onSuccess { array ->
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)
                        ?.toVideoTabOrNull()
                        ?.let(target::add)
                }
            }
            .onFailure {
                target.clear()
                prefs?.edit()?.remove(key)?.apply()
            }
    }

    private fun VideoTab.toJsonObject(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("source_url", sourceUrl)
            .put("resolved_media_json", resolvedMediaJson)
            .put("position_ms", positionMs)
            .put("play_when_ready", playWhenReady)
            .put("preparation_state", preparationState.name)
            .put("last_error", lastError)
            .put("manual_quality_height", manualQualityHeight ?: JSONObject.NULL)
            .put("actual_quality_height", actualQualityHeight ?: JSONObject.NULL)
            .put("created_at_ms", createdAtMs)
            .put("updated_at_ms", updatedAtMs)
            .put("preparation_requested_at_ms", preparationRequestedAtMs)
            .put("preparation_host_created_at_ms", preparationHostCreatedAtMs)
            .put("direct_resolver_started_at_ms", directResolverStartedAtMs)
            .put("direct_resolver_finished_at_ms", directResolverFinishedAtMs)
            .put("browser_stage_requested_at_ms", browserStageRequestedAtMs)
            .put("browser_webview_created_at_ms", browserWebViewCreatedAtMs)
            .put("browser_discovery_started_at_ms", browserDiscoveryStartedAtMs)
            .put("ready_at_ms", readyAtMs)
            .put("last_technical_preparation_stage", lastTechnicalPreparationStage)
            .put("last_technical_stage_at_ms", lastTechnicalStageAtMs)

    private fun JSONObject.toVideoTabOrNull(): VideoTab? {
        val id = optString("id").trim()
        if (id.isBlank()) return null

        val state = runCatching {
            PreparationState.valueOf(optString("preparation_state", "READY"))
        }.getOrDefault(PreparationState.ERROR)

        return VideoTab(
            id = id,
            title = optString("title", "Video").trim().ifBlank { "Video" },
            sourceUrl = optString("source_url").trim(),
            resolvedMediaJson = optString("resolved_media_json"),
            positionMs = optLong("position_ms", 0L).coerceAtLeast(0L),
            playWhenReady = optBoolean("play_when_ready", true),
            preparationState = state,
            lastError = optString("last_error"),
            manualQualityHeight = optionalPositiveInt("manual_quality_height"),
            actualQualityHeight = optionalPositiveInt("actual_quality_height"),
            createdAtMs = optLong("created_at_ms", System.currentTimeMillis()),
            updatedAtMs = optLong("updated_at_ms", System.currentTimeMillis()),
            preparationRequestedAtMs = optLong("preparation_requested_at_ms", 0L),
            preparationHostCreatedAtMs = optLong("preparation_host_created_at_ms", 0L),
            directResolverStartedAtMs = optLong("direct_resolver_started_at_ms", 0L),
            directResolverFinishedAtMs = optLong("direct_resolver_finished_at_ms", 0L),
            browserStageRequestedAtMs = optLong("browser_stage_requested_at_ms", 0L),
            browserWebViewCreatedAtMs = optLong("browser_webview_created_at_ms", 0L),
            browserDiscoveryStartedAtMs = optLong("browser_discovery_started_at_ms", 0L),
            readyAtMs = optLong("ready_at_ms", 0L),
            lastTechnicalPreparationStage = optString("last_technical_preparation_stage"),
            lastTechnicalStageAtMs = optLong("last_technical_stage_at_ms", 0L)
        )
    }

    private fun JSONObject.optionalPositiveInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key, 0).takeIf { it > 0 }
    }
}
