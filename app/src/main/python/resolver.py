import json
from typing import Any, Dict, Optional

import yt_dlp

USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/136.0 Mobile Safari/537.36"
)

SUPPORTED_QUALITIES = {"auto", "1080", "720", "480", "360"}


def _format_selector(quality: str) -> str:
    if quality == "auto":
        # Default policy:
        # 1. Prefer exactly 720p.
        # 2. If 720p is unavailable, try exactly 1080p.
        # 3. Otherwise use the best other quality up to 1080p.
        return (
            "bestvideo[height=720]+bestaudio/"
            "best[height=720][vcodec!=none][acodec!=none]/"
            "bestvideo[height=1080]+bestaudio/"
            "best[height=1080][vcodec!=none][acodec!=none]/"
            "bestvideo[height<=1080]+bestaudio/"
            "best[height<=1080][vcodec!=none][acodec!=none]/"
            "best[height<=1080]/best"
        )

    height = int(quality)

    # For a manually selected quality, use that resolution when possible.
    # If it is unavailable, fall back to a lower resolution rather than
    # unexpectedly selecting something above the user's chosen limit.
    return (
        f"bestvideo[height={height}]+bestaudio/"
        f"best[height={height}][vcodec!=none][acodec!=none]/"
        f"bestvideo[height<={height}]+bestaudio/"
        f"best[height<={height}][vcodec!=none][acodec!=none]/"
        f"best[height<={height}]/best"
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


def resolve(url: str, quality: str = "auto") -> str:
    url = (url or "").strip()

    if not url.startswith(("https://", "http://")):
        raise ValueError("Please share or paste a complete HTTP/HTTPS URL")

    quality = (quality or "auto").strip().lower()

    if quality not in SUPPORTED_QUALITIES:
        raise ValueError(f"Unsupported quality option: {quality}")

    options = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "cachedir": False,
        "format": _format_selector(quality),
        "http_headers": {
            "User-Agent": USER_AGENT,
        },

        # Never spoof X-Forwarded-For in order to bypass
        # geographic restrictions.
        "geo_bypass": False,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        raw = _first_entry(
            ydl.extract_info(
                url,
                download=False,
            )
        )

    if raw.get("has_drm") or raw.get("_has_drm"):
        raise ValueError(
            "This video appears to use DRM, which this player does not bypass"
        )

    base_headers = dict(raw.get("http_headers") or {})
    base_headers.setdefault("User-Agent", USER_AGENT)

    requested = raw.get("requested_formats") or []

    video = next(
        (
            fmt
            for fmt in requested
            if fmt.get("vcodec") not in (None, "none")
        ),
        None,
    )

    audio = next(
        (
            fmt
            for fmt in requested
            if fmt.get("acodec") not in (None, "none")
            and fmt is not video
        ),
        None,
    )

    common = {
        "title": raw.get("title") or "Video",
        "webpage_url": raw.get("webpage_url") or url,
        "duration": raw.get("duration"),
        "requested_quality": quality,
    }

    if video and audio:
        payload = {
            **common,
            "mode": "merged",
            "video": _source(
                video,
                base_headers,
            ),
            "audio": _source(
                audio,
                base_headers,
            ),
        }

    else:
        payload = {
            **common,
            "mode": "single",
            "media": _source(
                raw,
                base_headers,
            ),
        }

    return json.dumps(
        payload,
        ensure_ascii=False,
    )
