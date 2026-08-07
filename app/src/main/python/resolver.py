import json
from typing import Any, Dict, Optional

import yt_dlp

USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/136.0 Mobile Safari/537.36"
)


def _source(fmt: Dict[str, Any], fallback_headers: Dict[str, str]) -> Dict[str, Any]:
    url = fmt.get("url")
    if not url:
        raise ValueError("yt-dlp returned a format without a playable URL")

    protocol = (fmt.get("protocol") or "").lower()
    ext = (fmt.get("ext") or "").lower()
    mime: Optional[str] = None
    has_video = fmt.get("vcodec") not in (None, "none")
    if "m3u8" in protocol:
        mime = "application/x-mpegURL"
    elif protocol.startswith("dash") or ext == "mpd":
        mime = "application/dash+xml"
    elif ext in ("m4a", "mp4") and not has_video:
        mime = "audio/mp4"
    elif ext == "webm" and not has_video:
        mime = "audio/webm"
    elif ext == "mp4":
        mime = "video/mp4"
    elif ext == "webm":
        mime = "video/webm"

    headers = dict(fallback_headers)
    headers.update(fmt.get("http_headers") or {})

    return {
        "url": url,
        "mime_type": mime,
        "protocol": protocol,
        "height": fmt.get("height"),
        "width": fmt.get("width"),
        "format_id": fmt.get("format_id"),
        "headers": headers,
    }


def _first_entry(info: Dict[str, Any]) -> Dict[str, Any]:
    entries = info.get("entries")
    if entries:
        for entry in entries:
            if entry:
                return entry
        raise ValueError("The shared page did not contain a playable video")
    return info


def resolve(url: str) -> str:
    url = (url or "").strip()
    if not url.startswith(("https://", "http://")):
        raise ValueError("Please share or paste a complete HTTP/HTTPS URL")

    options = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "cachedir": False,
        # Prefer exactly 720p. If unavailable, try 1080p,
        # then fall back to the best available quality up to 1080p.
        "format": (
            "bestvideo[height=720]+bestaudio/"
            "best[height=720][vcodec!=none][acodec!=none]/"
            "bestvideo[height=1080]+bestaudio/"
            "best[height=1080][vcodec!=none][acodec!=none]/"
            "bestvideo[height<=1080]+bestaudio/"
            "best[height<=1080][vcodec!=none][acodec!=none]/"
            "best[height<=1080]/best"
        ),
        "http_headers": {"User-Agent": USER_AGENT},
        "geo_bypass": "never",
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        raw = _first_entry(ydl.extract_info(url, download=False))

    if raw.get("has_drm") or raw.get("_has_drm"):
        raise ValueError("This video appears to use DRM, which this player does not bypass")

    base_headers = dict(raw.get("http_headers") or {})
    base_headers.setdefault("User-Agent", USER_AGENT)
    requested = raw.get("requested_formats") or []

    video = next(
        (f for f in requested if f.get("vcodec") not in (None, "none")),
        None,
    )
    audio = next(
        (f for f in requested if f.get("acodec") not in (None, "none") and f is not video),
        None,
    )

    if video and audio:
        payload = {
            "mode": "merged",
            "title": raw.get("title") or "Video",
            "webpage_url": raw.get("webpage_url") or url,
            "duration": raw.get("duration"),
            "video": _source(video, base_headers),
            "audio": _source(audio, base_headers),
        }
    else:
        payload = {
            "mode": "single",
            "title": raw.get("title") or "Video",
            "webpage_url": raw.get("webpage_url") or url,
            "duration": raw.get("duration"),
            "media": _source(raw, base_headers),
        }

    return json.dumps(payload, ensure_ascii=False)
