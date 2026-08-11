# Temporary Chat Bootstrap — Vivaldi External Player

Copy/paste the text below into a new temporary ChatGPT chat when continuing this project.

---

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
- Do not analyze, classify, summarize, or inspect adult video content itself.
- For target-site testing, I will run the video on my own device and report technical success/failure.
- You may directly inspect non-adult test material on safe test sites.
- Prefer Internet Archive for ordinary direct-resolution/player tests.
- Prefer `https://bitmovin.com/demos/hls-fmp4/` for JavaScript/HLS browser-assisted tests.
- Cloudflare Stream public examples may be used when useful for normal CDN testing.

## Required player behavior

- Quality preference: exact 720p first; if unavailable, 1080p; otherwise best available below 1080p.
- Double tap left = seek backward 10 seconds.
- Double tap right = seek forward 10 seconds.
- Efficient buffering.
- Timeline thumbnail preview where technically supported.
- Normal play/pause/seeking.
- Quality control for direct and adaptive browser-resolved streams.
- Playback speed control (still pending after resolver work).
- App-level volume/mute control (still pending after resolver work).
- Normal portrait/landscape rotation.
- Final Return to browser behavior should unwind this app and reveal the existing Vivaldi task/tab, not open a fresh copy of the URL.

## Important UX backlog

Correct-stream selection comes first. After that:

- reduce the current excessive number of taps in browser-assisted resolution;
- handle pages containing multiple videos, advertisements, previews, and separate HLS audio/video renditions;
- automatically select the most likely primary complete stream when confidence is high;
- keep the manual candidate chooser as a fallback;
- avoid making the user browse the entire embedded webpage just to reach the player when a safe technical shortcut can be built.

Do this with technical metadata and page/player structure, not adult-content analysis.

## Development style

I am not an advanced developer. Explain decisions in plain English.

Source code should contain abundant **English comments** explaining what important parts do and why.

When you ask me to modify a source file, give me the **FULL CONTENT of the replacement file in one code block**. Do not give me only a patch, a download, or isolated changed lines.

My conversation language is English. My Vivaldi, Windows, and phone UI are usually Spanish. GitHub is in English. The app should support English and Spanish for relevant user-facing UI.

This project is intentionally worked on in temporary chats, so do not rely on account memory.

## Verified results which must not be forgotten

### Vivaldi Share

PASS: app appears in Share and receives the URL.

### Internet Archive

`https://archive.org/details/BigBuckBunny` opened PlayerActivity but rendered no video in Batch 1. This is now treated as a playback/source-compatibility failure, not simply "resolver failed". Batch 2 adds Media3-friendly yt-dlp container preference and diagnostics.

### Bitmovin

Safe test page:

`https://bitmovin.com/demos/hls-fmp4/`

Batch 1 candidate results:

1. MP4 -> empty.
2. MP4 -> empty.
3. HLS -> audio/music only.
4. HLS -> video without audio.
5. HLS -> full video+audio, correct.

The old resolver reordered candidates by recency, so #5 was likely the oldest/top-level HLS request. Batch 2 preserves first-seen order and ranks likely master/adaptive streams higher.

### Pornhub

The current problem is no longer just the historical yt-dlp 410.

Browser-assisted Batch 1 detected:

- `MP4 • es.pornhub.com • page` -> blank external player;
- `MP4 • ht-cdn2.adtng.com • network` -> plays, but it is an advertisement.

Batch 2 must reveal the exact Media3 reason for the blank preferred candidate and demote obvious ad infrastructure without deleting manual alternatives.

### HentaiHaven

Major PASS: browser-assisted resolution detected two candidates and the user's second candidate was the desired video; external Media3 playback worked.

This proves the core WebView-observation -> header/cookie handoff -> Media3 architecture works on a real target.

Batch 1 displayed `Quality: stream`; that did not prove only one quality existed. Batch 2 inspects Media3 adaptive tracks and enables quality selection where present.

## Current Batch 2 direction

Read `PROJECT_STATE.md` for exact source status.

Batch 2 changes include:

- yt-dlp MP4/M4A/WebM-compatible container preference;
- richer source metadata;
- copyable `Player.Listener.onPlayerError` diagnostics;
- browser candidate ranking with first-seen HLS order;
- generic ad-host demotion without blocking candidates;
- adaptive browser quality discovery and the 720 -> 1080 -> best-below-1080 policy;
- continued EN/ES strings and English source comments.

Do not skip the GitHub Actions compile gate. If it fails, fix the build before proposing additional functional changes.

## Development APK installation

The user's Xiaomi/MIUI phone allowed the development build to install successfully through ADB after Play Protect intercepted normal sideloading. ADB is an acceptable development installation path.

---
