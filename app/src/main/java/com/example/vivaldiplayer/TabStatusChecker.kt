package com.example.vivaldiplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Conservative, serialized health checks for cached playback URLs.
 *
 * The checker never re-resolves a page and never guesses alternate hosts. It only asks whether
 * the exact already-stored playback URLs still answer a tiny HTTP request. One transient network
 * failure is not enough to label a tab unavailable.
 */
object TabStatusChecker {

    data class Summary(
        val ready: Int,
        val needsRefresh: Int,
        val unavailable: Int,
        val needsAttention: Int,
        val unknown: Int
    )

    private data class ProbeResult(
        val state: TabHealthStore.State,
        val detail: String,
        val transientFailure: Boolean = false
    )

    suspend fun checkAll(
        context: Context,
        tabs: List<VideoTabStore.VideoTab>,
        onChanged: (() -> Unit)? = null
    ): Summary = withContext(Dispatchers.IO) {
        tabs.forEach { tab ->
            TabHealthStore.markChecking(context, tab.id)
            withContext(Dispatchers.Main) { onChanged?.invoke() }

            val result = evaluateTab(tab)
            val previous = TabHealthStore.get(context, tab.id)
            val failures = if (result.transientFailure) previous.consecutiveFailures + 1 else 0

            val finalState = if (result.transientFailure && failures < 2) {
                TabHealthStore.State.UNKNOWN
            } else {
                result.state
            }

            TabHealthStore.set(
                context = context,
                tabId = tab.id,
                state = finalState,
                detail = result.detail,
                consecutiveFailures = failures
            )
            withContext(Dispatchers.Main) { onChanged?.invoke() }
        }

        val statuses = tabs.map { TabHealthStore.get(context, it.id).state }
        Summary(
            ready = statuses.count { it == TabHealthStore.State.READY },
            needsRefresh = statuses.count { it == TabHealthStore.State.NEEDS_REFRESH },
            unavailable = statuses.count { it == TabHealthStore.State.UNAVAILABLE },
            needsAttention = statuses.count { it == TabHealthStore.State.NEEDS_ATTENTION },
            unknown = statuses.count { it == TabHealthStore.State.UNKNOWN }
        )
    }

    private fun evaluateTab(tab: VideoTabStore.VideoTab): ProbeResult {
        if (tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION) {
            return ProbeResult(
                TabHealthStore.State.NEEDS_ATTENTION,
                "Foreground interaction is required"
            )
        }

        if (tab.preparationState == VideoTabStore.PreparationState.ERROR) {
            return ProbeResult(
                TabHealthStore.State.NEEDS_REFRESH,
                tab.lastError.ifBlank { "Preparation failed" }
            )
        }

        if (!tab.isReady || tab.resolvedMediaJson.isBlank()) {
            return ProbeResult(TabHealthStore.State.UNKNOWN, "Not ready yet")
        }

        val resolved = runCatching { ResolvedMedia.fromJson(tab.resolvedMediaJson) }.getOrNull()
            ?: return ProbeResult(TabHealthStore.State.NEEDS_REFRESH, "Stored playback data is invalid")

        val sources = buildList {
            resolved.primarySource?.let(::add)
            if (resolved.mode == "merged") resolved.audio?.let(::add)
        }

        if (sources.isEmpty()) {
            return ProbeResult(TabHealthStore.State.NEEDS_REFRESH, "No stored playback URL")
        }

        for (source in sources) {
            val probe = probeSource(source)
            if (probe.state != TabHealthStore.State.READY) return probe
        }

        return ProbeResult(TabHealthStore.State.READY, "Stored playback source responded")
    }

    private fun probeSource(source: StreamSource): ProbeResult {
        val cleanUrl = source.url.trim()
        if (!cleanUrl.startsWith("http://", true) && !cleanUrl.startsWith("https://", true)) {
            return ProbeResult(TabHealthStore.State.NEEDS_REFRESH, "Stored playback URL is invalid")
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = true
                setRequestProperty("Range", "bytes=0-0")
                source.headers.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank()) setRequestProperty(name, value)
                }
            }

            val code = connection.responseCode
            when {
                code in 200..399 -> ProbeResult(TabHealthStore.State.READY, "HTTP $code")
                code == 401 || code == 403 || code == 404 || code == 410 ->
                    ProbeResult(TabHealthStore.State.NEEDS_REFRESH, "HTTP $code")
                code == 429 || code in 500..599 ->
                    ProbeResult(
                        TabHealthStore.State.UNAVAILABLE,
                        "Temporary HTTP $code",
                        transientFailure = true
                    )
                else -> ProbeResult(TabHealthStore.State.NEEDS_REFRESH, "HTTP $code")
            }
        } catch (error: Exception) {
            ProbeResult(
                state = TabHealthStore.State.UNAVAILABLE,
                detail = error.javaClass.simpleName,
                transientFailure = true
            )
        } finally {
            connection?.disconnect()
        }
    }
}
