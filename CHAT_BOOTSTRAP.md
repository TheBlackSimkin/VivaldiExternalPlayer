# Temporary Chat Bootstrap — Vivaldi External Player

Public repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Treat GitHub `main` as the source of truth. Before changing code, read `PROJECT_STATE.md` completely. Keep both state files updated whenever requirements, architecture, tests, failures, decisions, or priorities materially change.

## Communication

- Conversation: English.
- Windows/Vivaldi UI: normally Spanish; use Spanish UI labels when relevant.
- Android app UI: bilingual English/Spanish.
- Explain plainly; user is not an advanced developer.
- Do GitHub work directly whenever possible.
- Source code should contain abundant English comments.

## QA format

Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, **EXPECTED**, and **RESULT**;
2. one separate short code block containing only the compact answer format.

Never ask for PH/HH page/video title text. Titles may be used locally by the app only.

## Safety/content boundary

PH and HH are real technical playback targets. The user performs playback/content testing.

Allowed technical work: URLs, manifests, codecs, resolutions/qualities, request metadata, candidate ranking, browser/network state, playback errors/status, and local tab-title handling.

Do not inspect/analyze/classify/describe media content. Do not bypass DRM, subscriptions/paywalls, authentication, regional restrictions, anti-bot/CAPTCHA challenges, or import private Vivaldi credentials.

### Automatic age/cookie consent

The user wants conservative automation only for clearly identified 18+ confirmation and cookie-consent prompts. Never auto-click ambiguous buttons, login/account prompts, paywall/subscription/payment controls, regional controls, DRM controls, anti-bot/CAPTCHA/challenge controls, or unrelated actions. If uncertain, leave the prompt for the user.

## Verified Batch 4 baseline

Protect:
- Vivaldi share flow;
- yt-dlp first, then automatic browser-assisted fallback;
- automatic best candidate first, manual chooser only as fallback;
- quality policy 720p -> 1080p -> best below 1080p;
- video + audio;
- adaptive and sibling quality switching;
- double-tap ±10 seconds;
- seek preview;
- rotation;
- bilingual UI;
- up to 80 stored candidates / strongest 20 manual candidates;
- meaningful first-seen HLS/DASH ordering;
- no generic playlist bonus;
- soft demotion of obvious audio-only/video-only child paths;
- page-config family IDs for sibling URLs;
- no media imagery inspection.

Verified targets:
- Bitmovin automatic/video/audio PASS.
- PH automatic/video/audio/quality options/quality switching PASS.
- HH automatic/video/audio/quality options/quality switching PASS.
- Cloudinary is not a QA gate.

## Multi-video tabs

On `main`:
- `VideoTabStore` stores process-local tab sessions;
- `TabbedPlayerApplication` coordinates tabs above validated player/resolver logic;
- one active ExoPlayer playback session at a time;
- per-tab resolved JSON, title, position and play/pause state;
- selected quality source preserved when practical;
- independent select/switch/close;
- final close returns to clean neutral `MainActivity`;
- browser-assisted tabs use local WebView page title;
- full process-restart persistence remains undecided.

The follow-up device QA for build #62 (final-tab cleanup + browser-assisted title fix) has already been completed/validated. Do not ask the user to repeat it unless investigating a regression.

## Clean opening/buffering UX

A 2026-08-13 batch on `main` adds:
- `Opening video…` for normal initial opening/direct resolution;
- no intentional raw Python-resolver error flash before automatic browser fallback;
- a presentation-only overlay in `GesturePlayerView`;
- `Buffering…` only when Media3 reports `Player.STATE_BUFFERING`;
- overlay hidden when ready/ended or when PlayerActivity takes over error diagnostics;
- English + Spanish strings;
- no change to Batch 4 candidate ranking/source selection/quality logic.

Relevant commits include:
- `12d0f0e7786651ebb2d9c4aa8651e9bd50d4bb0a`;
- `1075fe934dfe80ade3874478f5a3b346a42350ba`;
- `6f3e37e8653d49ad5ac6904206393ec078cac363`;
- `400f9640b11e01bd4a0102c46a25de3126191dea`.

Remaining UX polish: BrowserResolverActivity may still be shown when foreground browser interaction is genuinely needed. Any further concealment must not change validated candidate logic.

## Foreground-only playback requirement

Playback must never continue when External Player is not actively foregrounded.

Required:
- pause/stop video and audio when switching to Vivaldi/another app;
- pause/stop when phone locks/screen turns off;
- preserve tab position;
- distinguish automatic lifecycle pause from deliberate user pause;
- background preparation may continue when Android allows it;
- no background audio/PiP autoplay/media-session continuation/foreground playback service unless requirement changes.

Implementation/device QA is still pending.

## Background Add workflow

Required separate share action: `Add to External Player` / `Añadir a External Player`.

Expected:
- Vivaldi stays foregrounded;
- create tab immediately;
- start pre-resolution immediately;
- tabs should ideally be READY before user opens External Player;
- selecting READY must use stored resolved media without resolving again.

Preparation states: `QUEUED`, `RESOLVING`, `READY`, `NEEDS_ATTENTION` and/or `ERROR`.

Pre-resolution is not playback. Browser-assisted work which genuinely requires foreground interaction must not be falsely marked READY.

## Current priority

1. Finish/verify clean `Opening video…` / real `Buffering…` behavior and remaining resolver-visibility polish.
2. Conservative automatic clear age/cookie consent handling.
3. Background Add + immediate pre-resolution/preparation states.
4. Foreground-only playback lifecycle enforcement.
5. User-selected next-iteration feature(s) from the feature backlog discussed in chat.
6. App icon.
7. Playback speed.
8. App-level volume/mute.
9. Return to existing Vivaldi task/tab.
10. Persistent APK signing.
11. Decide process-restart tab persistence.
12. Brave support later.
