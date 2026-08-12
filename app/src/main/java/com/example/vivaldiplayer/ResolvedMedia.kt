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

        /** Parse the JSON returned either by resolver.py or BrowserResolverActivity. */
        fun fromJson(json: String): ResolvedMedia {
            val root = JSONObject(json)

            return ResolvedMedia(
                mode = root.getString("mode"),
                title = root.optString("title", "Video"),
                webpageUrl = root.optString("webpage_url", ""),
                requestedQuality = root.optString("requested_quality", "auto"),

                /*
                 * Older yt-dlp payloads did not include resolver_mode, so keep a
                 * safe backwards-compatible default.
                 */
                resolverMode = root.optString("resolver_mode", "ytdlp"),
                single = root.optJSONObject("media")?.toSource(),
                video = root.optJSONObject("video")?.toSource(),
                audio = root.optJSONObject("audio")?.toSource(),
                browserVariants = root.optJSONArray("browser_variants").toSources()
            )
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
