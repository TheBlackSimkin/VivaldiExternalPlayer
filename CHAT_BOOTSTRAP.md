I am continuing an existing Android project called **Vivaldi External Player**.

Public repository:

`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

If you cannot access the public repository, ask me for the latest ZIP. Until direct GitHub access succeeds, check the repository again after each of my messages. Do not claim you reviewed files which you could not actually read.

Before changing code, read `PROJECT_STATE.md` in full. Treat it as the operational memory/source of truth for the project. Update `PROJECT_STATE.md` and this `CHAT_BOOTSTRAP.md` whenever requirements, architecture, tests, failures, decisions, or next steps materially change.

## Project goal

This is a personal Android external video player intended primarily for Vivaldi Mobile Browser on Android. Phase 1 targets Pornhub and HentaiHaven. Brave compatibility may be considered later.

The app should receive a browser-shared page URL, resolve an accessible **non-DRM** media stream, and play it with Media3 / ExoPlayer using better controls than the site's built-in player.

## Boundaries

- Do not bypass DRM or obtain DRM keys.
- Do not bypass subscriptions/paywalls, authentication, or regional restrictions.
- Do not deliberately automate/defeat anti-bot access controls.
- Intended target usage is logged out.
- Do not analyze, classify, summarize, or inspect the adult video content itself.
- For target-site testing, I will run the video on my own device and report technical success/failure.
- You may directly inspect non-adult test material on safe test sites.
- Prefer Internet Archive for ordinary direct-resolution/player tests.
- Prefer Bitmovin public demos, and Cloudflare Stream public examples when useful, for JavaScript/HLS/DASH browser-assisted tests.

## Required player behavior

- Quality preference: exact 720p first; if unavailable, 1080p; otherwise best available below 1080p.
- Double tap left = seek backward 10 seconds.
- Double tap right = seek forward 10 seconds.
- Efficient buffering.
- Timeline thumbnail preview where technically supported.
- Normal play/pause/seeking.
- Quality control.
- Playback speed control.
- App-level volume/mute control.
- Normal portrait/landscape rotation.
- Final Return to browser behavior should unwind this app and reveal the existing Vivaldi task/tab, not open a fresh copy of the URL.

## Development style

I am not an advanced developer. Explain decisions in plain English.

Source code should contain abundant **English comments** explaining what important parts do and why.

When you ask me to modify a source file, give me the **FULL CONTENT of the replacement file in one code block**. Do not give me only a patch, a download, or isolated changed lines.

My conversation language is English. My Vivaldi, Windows, and phone UI are usually Spanish. GitHub is in English. The app should support English and Spanish for relevant user-facing UI.

This project is intentionally worked on in temporary chats, so do not rely on account memory.

## Known target failures from the original prototype

Pornhub test URL produced a yt-dlp PornHub extractor HTTP 410 while downloading the webpage.

HentaiHaven test URL produced a yt-dlp generic extractor HTTP 403 caused by a Cloudflare anti-bot challenge and suggested yt-dlp impersonation support.

`curl_cffi` is NOT currently enabled. Adding the dependency itself is not DRM bypass, but any future browser-impersonation use must be reviewed for Android/Chaquopy compatibility and the site's current terms/access-control boundary.

## Current architectural direction

Keep the direct yt-dlp resolver as the first attempt.

After direct failure, automatically open the app's browser-assisted WebView fallback. The fallback may observe its own normal network/service-worker requests, page `<video>/<source>` URLs, and Performance API resource URLs. It must not import Vivaldi credentials, replace protected responses, automate challenges, or configure DRM license acquisition.

Read `PROJECT_STATE.md` for the exact current implementation status and next test before proposing another rewrite.

---
