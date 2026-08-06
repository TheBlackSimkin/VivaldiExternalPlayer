package com.example.vivaldiplayer

import org.json.JSONObject

data class StreamSource(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val height: Int?
)

data class ResolvedMedia(
    val mode: String,
    val title: String,
    val single: StreamSource?,
    val video: StreamSource?,
    val audio: StreamSource?
) {
    companion object {
        fun fromJson(json: String): ResolvedMedia {
            val root = JSONObject(json)
            return ResolvedMedia(
                mode = root.getString("mode"),
                title = root.optString("title", "Video"),
                single = root.optJSONObject("media")?.toSource(),
                video = root.optJSONObject("video")?.toSource(),
                audio = root.optJSONObject("audio")?.toSource()
            )
        }

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
                mimeType = optString("mime_type").takeIf { it.isNotBlank() && it != "null" },
                headers = headers,
                height = if (isNull("height")) null else optInt("height")
            )
        }
    }
}
