# Vivaldi External Player — Project State

> Operational source of truth. GitHub `main` is authoritative. Update this file whenever requirements, architecture, QA results, failures, decisions, or priorities materially change.

## Environment and communication

- Android phone; Vivaldi Mobile Browser is Phase 1.
- Conversation with ChatGPT: English.
- Windows/Vivaldi UI is normally Spanish; use Spanish UI labels when relevant.
- Android app UI remains bilingual English/Spanish.
- Do GitHub work directly whenever possible; do not require manual file editing/upload/commits when the connected GitHub tools can do it.
- Explain implementation/test steps plainly; user is not an advanced developer.

## Safety/content boundary

PH and HH are real technical playback targets. The user performs all media-content testing.

Allowed technical work: URLs, manifests, codecs, qualities/resolutions, request metadata, candidate ranking, browser/network state, playback state/errors, and local tab-title handling.

Do not inspect/analyze/classify/describe PH/HH video content. Do not bypass DRM or obtain keys. Do not bypass subscriptions/paywalls, authentication, regional controls, anti-bot/CAPTCHA/challenge systems, or import private Vivaldi credentials.

Local page/video titles may be used on-device for tab labels. ChatGPT must not request PH/HH title text.

### Automatic consent policy

The user is over 18 and wants conservative automatic acceptance only for clearly identified:
- 18+ / age-confirmation prompts;
- cookie-consent prompts.

Never auto-click ambiguous controls, login/account controls, paywall/subscription/payment controls, regional-access controls, DRM-related controls, anti-bot/CAPTCHA/challenge controls, or unrelated page actions. If uncertain, leave the prompt for the user.

## Verified Batch 4 playback baseline — protect from regression

Quality policy:
1. exact 720p when available;
2. otherwise 1080p;
3. otherwise highest available quality below 1080p.

Required baseline behavior:
- Share from Vivaldi.
- yt-dlp first, then automatic browser-assisted fallback.
- Automatic best candidate first; manual chooser fallback only.
- Video + audio playback.
- Adaptive and sibling-URL quality switching.
- Double tap left/right = -10/+10 seconds.
- Timeline thumbnail preview where technically supported.
- Portrait/landscape.
- English + Spanish UI.

Verified device baseline:
- Bitmovin: automatic YES, video YES, audio YES.
- PH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
- HH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
- Cloudinary is explicitly skipped and is not a QA gate.

Resolver protections:
- discovery settles before automatic best-candidate attempt;
- manual chooser fallback only;
- up to 80 candidates stored, strongest 20 shown manually;
- meaningful first-seen HLS/DASH ordering;
- no generic `playlist` bonus;
- soft demotion of obvious audio-only/video-only child paths;
- page-config family IDs for sibling quality URLs;
- no media imagery inspection.

## Multi-video tabs

Required and implemented process-local behavior:
- every shared/moved video becomes an independent tab;
- multiple tabs remain open;
- switch/select/close individual tabs;
- closing one does not close the others;
- preserve per-tab position;
- preserve selected quality when practical;
- active-tab close selects another tab or a clean home state;
- one active ExoPlayer playback session at a time.

Architecture:
- `VideoTabStore`: process-local resolved-media JSON, title, position, play/pause state.
- `TabbedPlayerApplication`: coordinator above the validated resolver/player flow.
- tab switching reconstructs playback from stored resolved JSON.
- `ResolvedMedia.toJson()` preserves quality-switched sources when practical.
- full process-restart persistence remains undecided.

### Device QA history

First multi-tab QA on 2026-08-13 verified tabs, switching, position restore, quality preservation, independent close, auto resolver, video/audio, quality options/switching, and double-tap seek. It exposed two issues: closing the final tab revealed the resolver underneath, and browser-assisted tab labels were generic.

Commit `8be38f33c1a1f225ef555133229669f7e9008b1e` fixed both: final-tab cleanup returns to neutral `MainActivity`, and browser-assisted tabs use the local WebView page title. GitHub Actions build #62 passed. The user has already completed/validated this follow-up device QA; do not ask to repeat build #62 unless investigating a regression.

## Tab titles

Preferred sources:
1. yt-dlp/resolver title;
2. browser-assisted local page title;
3. fallback `Video`.

Never ask the user to send PH/HH title text. QA reports should use CORRECT/GENERIC/OTHER only.

## Loading / buffering UX

Implemented on `main` in the 2026-08-13 clean-loading batch:
- normal direct resolution now shows `Opening video…` instead of raw resolver detail;
- automatic yt-dlp failure -> browser-assisted transition no longer intentionally paints the raw Python error before launching the fallback;
- `GesturePlayerView` owns a presentation-only loading overlay;
- `Opening video…` is shown before Media3 is ready;
- `Buffering…` is shown only when Media3 reports `Player.STATE_BUFFERING`;
- loading overlay disappears on READY/ENDED and on playback error so `PlayerActivity` diagnostics remain authoritative;
- English and Spanish strings are present;
- candidate ranking, source selection, quality logic, and resolver ordering are unchanged.

Commits in this batch include `12d0f0e7786651ebb2d9c4aa8651e9bd50d4bb0a`, `1075fe934dfe80ade3874478f5a3b346a42350ba`, `6f3e37e8653d49ad5ac6904206393ec078cac363`, and `400f9640b11e01bd4a0102c46a25de3126191dea`.

Remaining UX refinement: BrowserResolverActivity itself may still be visible when browser-assisted interaction is genuinely needed. A future pass can hide it behind a clean resolver state until user attention is actually required, without changing candidate ranking.

## Foreground-only playback / privacy

Playback must never continue when External Player is not actively foregrounded.

Required behavior:
- pause/stop video and audio when switching to Vivaldi or another app;
- pause/stop on phone lock/screen off;
- preserve current tab position;
- distinguish automatic background/lock pause from deliberate user pause so resume policy can be sensible;
- background tab preparation/resolution may continue when Android allows it;
- no background audio continuation, PiP autoplay, media-session continuation, or foreground playback service unless requirement changes.

This requirement is documented but implementation/device QA is still pending.

## Background add / pre-resolution

Required workflow:
- separate share action: `Add to External Player` / `Añadir a External Player`;
- Vivaldi remains foregrounded;
- create a new tab immediately;
- start resolution/preparation immediately;
- previously added tabs should ideally be READY before the user switches to External Player;
- selecting READY must use stored resolved media without re-resolving.

Preparation states: `QUEUED`, `RESOLVING`, `READY`, `NEEDS_ATTENTION` and/or `ERROR`.

Architecture rules:
- direct/yt-dlp pre-resolution should use Android-supported background work;
- pre-resolution is not playback and must not start multiple ExoPlayers;
- if browser-assisted WebView work genuinely requires foreground interaction, mark the tab as needing attention rather than falsely READY;
- clean `Opening video…` UX should be used for foreground completion;
- consent automation may help only under the strict policy above;
- background preparation never overrides foreground-only playback.

## Current architecture summary

### MainActivity
- ACTION_SEND / text/plain entry;
- extracts HTTP(S) URL from share text;
- manual URL paste remains for debugging;
- yt-dlp first, browser-assisted fallback on direct failure;
- normal automatic transition now uses clean opening status rather than raw error flicker.

### resolver.py
- yt-dlp via Chaquopy;
- no media-file downloads;
- rejects media marked DRM;
- uses project quality policy.

### BrowserResolverActivity
Observes ordinary WebView/service-worker requests, page `<video>`/`<source>` elements, Performance API resources, and exposed technical player configuration. Conservative age/cookie automation belongs here or in a dedicated helper and must remain separate from candidate ranking.

### PlayerActivity / GesturePlayerView
- Media3 ExoPlayer;
- progressive/HLS/DASH;
- merged separate video/audio support;
- adaptive track selection and sibling-URL quality switching;
- seek preview and double-tap seek;
- playback diagnostics;
- clean state-driven opening/buffering overlay;
- foreground-only playback lifecycle still pending.

## QA format

Whenever asking the user to test, provide EXACTLY:
1. one detailed code block with steps, EXPECTED, and RESULT;
2. one separate short code block containing only the compact answer format.

Never ask for PH/HH title text. Cloudinary is not required.

## Current priority

1. Finish/verify the clean `Opening video…` / real `Buffering…` build and any remaining resolver-visibility polish.
2. Conservative automatic clear 18+ age-confirmation and cookie-consent handling.
3. Background `Add to External Player` / `Añadir a External Player` with immediate pre-resolution and per-tab preparation states.
4. Enforce foreground-only playback when app is backgrounded or phone is locked.
5. User-selected next-iteration feature(s) from the feature backlog discussed in chat.
6. Polished original adaptive launcher icon.
7. Playback speed control.
8. App-level volume/mute.
9. Return to existing Vivaldi task/tab.
10. Persistent APK signing for GitHub Actions.
11. Decide full process-restart tab persistence separately.
12. Brave support later.
