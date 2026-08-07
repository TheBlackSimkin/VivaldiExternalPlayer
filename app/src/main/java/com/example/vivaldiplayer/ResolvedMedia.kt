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
    val webpageUrl: String,
    val requestedQuality: String,
    val resolverMode: String,
    val single: StreamSource?,
    val video: StreamSource?,
    val audio: StreamSource?
) {

    val displayedHeight: Int?
        get() =
            video?.height
                ?: single?.height

    companion object {

        fun fromJson(
            json: String
        ): ResolvedMedia {

            val root =
                JSONObject(json)

            return ResolvedMedia(
                mode =
                    root.getString(
                        "mode"
                    ),
                title =
                    root.optString(
                        "title",
                        "Video"
                    ),
                webpageUrl =
                    root.optString(
                        "webpage_url",
                        ""
                    ),
                requestedQuality =
                    root.optString(
                        "requested_quality",
                        "auto"
                    ),
                /*
                 * Existing yt-dlp JSON doesn't need
                 * modification. If this property is
                 * missing, it is treated as yt-dlp.
                 */
                resolverMode =
                    root.optString(
                        "resolver_mode",
                        "ytdlp"
                    ),
                single =
                    root
                        .optJSONObject(
                            "media"
                        )
                        ?.toSource(),
                video =
                    root
                        .optJSONObject(
                            "video"
                        )
                        ?.toSource(),
                audio =
                    root
                        .optJSONObject(
                            "audio"
                        )
                        ?.toSource()
            )
        }

        private fun JSONObject.toSource():
            StreamSource {

            val headerObject =
                optJSONObject(
                    "headers"
                ) ?: JSONObject()

            val headers =
                buildMap {

                    val keys =
                        headerObject.keys()

                    while (
                        keys.hasNext()
                    ) {
                        val key =
                            keys.next()

                        put(
                            key,
                            headerObject
                                .optString(
                                    key
                                )
                        )
                    }
                }

            return StreamSource(
                url =
                    getString(
                        "url"
                    ),
                mimeType =
                    optString(
                        "mime_type"
                    ).takeIf {
                        it.isNotBlank() &&
                        it != "null"
                    },
                headers =
                    headers,
                height =
                    if (
                        isNull(
                            "height"
                        )
                    ) {
                        null
                    } else {
                        optInt(
                            "height"
                        )
                    }
            )
        }
    }
}
