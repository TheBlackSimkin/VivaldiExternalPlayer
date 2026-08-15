package com.example.vivaldiplayer

import org.json.JSONArray
import org.json.JSONObject

/**
 * One concrete media source which Media3 can request.
 *
 * The resolver intentionally passes technical metadata such as protocol,
 * container and codecs along with the URL. Playback does not require every
 * field, but these values are useful when a phone reports a playback problem
 * and we need to understand which source was selected.
 */
data class StreamSource(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val height: Int?,
    val width: Int?,
    val protocol: String?,
    val extension: String?,
    val container: String?,
    val formatId: String?,
    val videoCodec: String?,
    val audioCodec: String?
)

/**
 * Small resolver-independent contract consumed by PlayerActivity.
 *
 * `mode == "single"` means one source contains everything needed for playback.
 * `mode == "merged"` means video and audio arrived as separate URLs and Media3
 * must play them together with MergingMediaSource.
 *
 * `browserVariants` is used only by the browser-assisted resolver. Some web
 * players expose one complete URL per quality instead of one adaptive master
 * manifest. Keeping those sibling URLs lets the quality button switch between
 * 360p/480p/720p/1080p without returning to the webpage.
 */
data class ResolvedMedia(
    val mode: String,
    val title: String,
    val webpageUrl: String,
    val requestedQuality: String,
    val resolverMode: String,
    val single: StreamSource?,
    val video: StreamSource?,
    val audio: StreamSource?,
    val browserVariants: List<StreamSource> = emptyList()
) {

    /** Height shown by the quality button when it is known. */
    val displayedHeight: Int?
        get() = video?.height ?: single?.height

    /** Primary source used for video playback and diagnostics. */
    val primarySource: StreamSource?
        get() = video ?: single

    /**
     * Serialize the resolver-independent model back to its JSON contract.
     *
     * Tab sessions use this after a quality switch so a tab can restore the
     * exact currently selected source without re-running the resolver.
     */
    fun toJson(): String {
        val root = JSONObject()
            .put("mode", mode)
            .put("title", title)
            .put("webpage_url", webpageUrl)
            .put("requested_quality", requestedQuality)
            .put("resolver_mode", resolverMode)

        single?.let { root.put("media", it.toJsonObject()) }
        video?.let { root.put("video", it.toJsonObject()) }
        audio?.let { root.put("audio", it.toJsonObject()) }

        if (browserVariants.isNotEmpty()) {
            val array = JSONArray()
            browserVariants.forEach { array.put(it.toJsonObject()) }
            root.put("browser_variants", array)
        }

        return root.toString()
    }

    companion object {

        /** Parse the JSON returned either by resolver.py or browser discovery. */
        fun fromJson(json: String): ResolvedMedia {
            val root = JSONObject(json)
            val mode = root.getString("mode")
            val requestedQuality = root.optString("requested_quality", "auto")

            /*
             * Older yt-dlp payloads did not include resolver_mode, so keep a
             * safe backwards-compatible default.
             */
            val resolverMode = root.optString("resolver_mode", "ytdlp")
            val parsedSingle = root.optJSONObject("media")?.toSource()
            val parsedVideo = root.optJSONObject("video")?.toSource()
            val parsedAudio = root.optJSONObject("audio")?.toSource()
            val browserVariants = root.optJSONArray("browser_variants").toSources()

            /*
             * Browser discovery may score a 1080p candidate highest for generic
             * technical reasons even when the same page-config family also
             * exposes a complete 720p sibling. Builds #225/#227 demonstrated the
             * resulting contradiction on-device: playback started at 1080p while
             * the quality UI correctly knew that Auto should prefer 720p.
             *
             * Normalize that contract here, before PlayerActivity builds its
             * first MediaSource. This is deliberately limited to automatic
             * browser requests. When the user explicitly selected 480/720/1080,
             * switchBrowserVariant stores that numeric requestedQuality and this
             * block leaves the exact manual source untouched.
             *
             * Doing this at the shared parser also repairs already-persisted
             * browser tabs produced by older builds when they are opened again.
             */
            val normalizedSingle = if (
                resolverMode == "browser" &&
                mode == "single" &&
                isAutomaticBrowserRequest(requestedQuality)
            ) {
                preferredAutomaticBrowserVariant(browserVariants) ?: parsedSingle
            } else {
                parsedSingle
            }

            return ResolvedMedia(
                mode = mode,
                title = root.optString("title", "Video"),
                webpageUrl = root.optString("webpage_url", ""),
                requestedQuality = requestedQuality,
                resolverMode = resolverMode,
                single = normalizedSingle,
                video = parsedVideo,
                audio = parsedAudio,
                browserVariants = browserVariants
            )
        }

        /** Automatic browser requests use the project-wide quality policy. */
        private fun isAutomaticBrowserRequest(requestedQuality: String): Boolean =
            requestedQuality.isBlank() ||
                requestedQuality.equals("browser", ignoreCase = true) ||
                requestedQuality.equals("auto", ignoreCase = true)

        /**
         * Choose one complete browser sibling for initial automatic playback.
         *
         * Policy is intentionally strict and shared with the player UI:
         * 1. exact 720p;
         * 2. otherwise exact 1080p;
         * 3. otherwise the highest rendition below 1080p;
         * 4. rare fallback: the smallest declared rendition above 1080p.
         *
         * If several technical URLs claim the same height, prefer the widest one
         * rather than depending on discovery order. No media imagery is inspected.
         */
        private fun preferredAutomaticBrowserVariant(
            sources: List<StreamSource>
        ): StreamSource? {
            val declared = sources.filter { source ->
                val height = source.height
                height != null && height > 0 && source.url.isNotBlank()
            }

            if (declared.isEmpty()) {
                return null
            }

            val heights = declared.mapNotNull { it.height }.distinct()
            val targetHeight = when {
                720 in heights -> 720
                1080 in heights -> 1080
                heights.any { it < 1080 } -> heights.filter { it < 1080 }.maxOrNull()
                else -> heights.minOrNull()
            } ?: return null

            return declared
                .filter { source -> source.height == targetHeight }
                .maxByOrNull { source -> source.width ?: 0 }
        }

        /** Convert one JSON source object into a Kotlin StreamSource. */
        private fun JSONObject.toSource(): StreamSource {
            val headerObject = optJSONObject("headers") ?: JSONObject()

            val headers = buildMap {
                val keys = headerObject.keys()

                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, headerObject.optString(key))
                }
            }

            return StreamSource(
                url = getString("url"),
                mimeType = nullableString("mime_type"),
                headers = headers,
                height = nullableInt("height"),
                width = nullableInt("width"),
                protocol = nullableString("protocol"),
                extension = nullableString("ext"),
                container = nullableString("container"),
                formatId = nullableString("format_id"),
                videoCodec = nullableString("vcodec"),
                audioCodec = nullableString("acodec")
            )
        }

        /** Parse an optional array of browser quality siblings. */
        private fun JSONArray?.toSources(): List<StreamSource> {
            if (this == null) {
                return emptyList()
            }

            return buildList {
                for (index in 0 until length()) {
                    optJSONObject(index)?.let { add(it.toSource()) }
                }
            }
        }

        /** JSON uses both missing values and explicit nulls, so normalize both. */
        private fun JSONObject.nullableString(key: String): String? =
            optString(key)
                .takeIf { value ->
                    value.isNotBlank() && value != "null"
                }

        private fun JSONObject.nullableInt(key: String): Int? =
            if (isNull(key) || !has(key)) {
                null
            } else {
                optInt(key)
            }
    }
}

/** Convert one source back into the shared resolver JSON contract. */
private fun StreamSource.toJsonObject(): JSONObject {
    val headerJson = JSONObject()
    headers.forEach { (key, value) -> headerJson.put(key, value) }

    return JSONObject()
        .put("url", url)
        .put("mime_type", mimeType ?: JSONObject.NULL)
        .put("headers", headerJson)
        .put("height", height ?: JSONObject.NULL)
        .put("width", width ?: JSONObject.NULL)
        .put("protocol", protocol ?: JSONObject.NULL)
        .put("ext", extension ?: JSONObject.NULL)
        .put("container", container ?: JSONObject.NULL)
        .put("format_id", formatId ?: JSONObject.NULL)
        .put("vcodec", videoCodec ?: JSONObject.NULL)
        .put("acodec", audioCodec ?: JSONObject.NULL)
}
