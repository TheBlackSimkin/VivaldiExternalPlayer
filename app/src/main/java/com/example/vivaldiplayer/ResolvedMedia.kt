package com.example.vivaldiplayer

import org.json.JSONObject

/**
 * One concrete media source which Media3 can request.
 *
 * The resolver intentionally passes technical metadata such as protocol,
 * container and codecs along with the URL. Playback does not require every
 * field, but these values are extremely useful when a phone reports "blank
 * player" and we need to understand what yt-dlp or the WebView actually chose.
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
 */
data class ResolvedMedia(
    val mode: String,
    val title: String,
    val webpageUrl: String,
    val requestedQuality: String,
    val resolverMode: String,
    val single: StreamSource?,
    val video: StreamSource?,
    val audio: StreamSource?
) {

    /** Height shown by the existing yt-dlp quality button when it is known. */
    val displayedHeight: Int?
        get() = video?.height ?: single?.height

    /** Primary source used for video playback and diagnostics. */
    val primarySource: StreamSource?
        get() = video ?: single

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
                audio = root.optJSONObject("audio")?.toSource()
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
