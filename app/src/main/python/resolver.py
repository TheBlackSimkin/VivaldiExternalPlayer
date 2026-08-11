"""Resolve a normal webpage URL into one or two Media3-friendly stream URLs.

This module runs inside the Android app through Chaquopy.  It does NOT download
video files.  yt-dlp is only asked to inspect the page and return a playable
stream URL plus the request headers needed by the media server.

The important policy in this file is intentionally conservative:
- never bypass DRM;
- never spoof geographic location;
- prefer containers which Android Media3 commonly supports;
- prefer 720p, then 1080p, then the best available quality below 1080p.
"""

import json
from typing import Any, Dict, Optional

import yt_dlp


# A normal mobile browser User-Agent.  This is used as an HTTP header only; it
# does not enable yt-dlp's optional browser-impersonation subsystem.
USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/136.0 Mobile Safari/537.36"
)

SUPPORTED_QUALITIES = {"auto", "1080", "720", "480", "360"}


# Media3 handles MP4 and WebM much more predictably than legacy containers such
# as AVI.  Internet Archive exposed this weakness during testing: yt-dlp could
# resolve a video but the player then had nothing useful to render.  These
# selector fragments explicitly prefer Media3-friendly containers instead of
# accepting any file only because its resolution is desirable.
def _compatible_height_selector(height_expression: str) -> str:
    """Return yt-dlp selector alternatives for one height expression.

    `height_expression` is either an exact constraint such as `=720` or a
    maximum constraint such as `<=720`.

    The order is deliberate:
    1. MP4 video + M4A audio when separate adaptive streams are available.
    2. A combined MP4 stream containing both video and audio.
    3. WebM video + WebM audio.
    4. A combined WebM stream.

    We do not add an unrestricted `/best` fallback here.  If a page only offers
    an unsupported legacy container, failing resolution and using the browser
    fallback is better than opening a blank player with an unusable source.
    """

    return "/".join(
        [
            f"bestvideo[height{height_expression}][ext=mp4]+bestaudio[ext=m4a]",
            (
                f"best[height{height_expression}][ext=mp4]"
                "[vcodec!=none][acodec!=none]"
            ),
            f"bestvideo[height{height_expression}][ext=webm]+bestaudio[ext=webm]",
            (
                f"best[height{height_expression}][ext=webm]"
                "[vcodec!=none][acodec!=none]"
            ),
        ]
    )


def _format_selector(quality: str) -> str:
    """Build the project quality policy as a yt-dlp format selector."""

    if quality == "auto":
        # Project default policy:
        # 1. Prefer exactly 720p.
        # 2. If 720p is unavailable, prefer exactly 1080p.
        # 3. Otherwise use the best compatible quality below 1080p.
        return "/".join(
            [
                _compatible_height_selector("=720"),
                _compatible_height_selector("=1080"),
                _compatible_height_selector("<1080"),
            ]
        )

    height = int(quality)

    # A manual quality choice is treated as an upper bound after first trying
    # the exact requested resolution.  This avoids unexpectedly jumping above
    # the user's chosen limit.
    return "/".join(
        [
            _compatible_height_selector(f"={height}"),
            _compatible_height_selector(f"<{height}"),
        ]
    )


def _source(fmt: Dict[str, Any], fallback_headers: Dict[str, str]) -> Dict[str, Any]:
    """Convert one yt-dlp format dictionary into the app's JSON contract."""

    url = fmt.get("url")
    if not url:
        raise ValueError("yt-dlp returned a format without a playable URL")

    protocol = (fmt.get("protocol") or "").lower()
    ext = (fmt.get("ext") or "").lower()
    container = (fmt.get("container") or "").lower() or None
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
        "ext": ext or None,
        "container": container,
        "height": fmt.get("height"),
        "width": fmt.get("width"),
        "format_id": fmt.get("format_id"),
        "vcodec": fmt.get("vcodec"),
        "acodec": fmt.get("acodec"),
        "headers": headers,
    }


def _first_entry(info: Dict[str, Any]) -> Dict[str, Any]:
    """Return the first real video when an extractor produced a playlist."""

    entries = info.get("entries")

    if entries:
        for entry in entries:
            if entry:
                return entry

        raise ValueError("The shared page did not contain a playable video")

    return info


def resolve(url: str, quality: str = "auto") -> str:
    """Resolve `url` and return a JSON string understood by PlayerActivity."""

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

        # Never spoof X-Forwarded-For in order to bypass geographic restrictions.
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
        "resolver_mode": "ytdlp",
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
