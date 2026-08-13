# Temporary Chat Bootstrap — Vivaldi External Player

I am continuing an existing Android project called **Vivaldi External Player**.

Public repository:
`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Before every response, verify whether you can still read this repository directly and briefly state the result. Treat GitHub `main` as the source of truth. Before changing code, read `PROJECT_STATE.md` in full. Update both `PROJECT_STATE.md` and this file whenever requirements, architecture, tests, failures, decisions, or next steps materially change.

## Communication preferences

- Conversation: English.
- Windows/Vivaldi UI: normally Spanish; use Spanish UI labels when relevant.
- Android app UI: bilingual English/Spanish.
- Explain behavior plainly; I am not an advanced developer.
- Source code should contain abundant English comments.
- Do GitHub work directly whenever possible; do not make me manually edit/code/upload files if the connected GitHub tool can do it.

## QA response format

Whenever you ask me to test something, always provide exactly:
1. one detailed code block with steps, **EXPECTED**, and **RESULT**;
2. one separate short code block containing only the compact answer format for me to fill in.

Never ask me to send PH/HH page/video titles to ChatGPT. Titles may be used locally by the Android app for tab labels only.

## Safety/content boundary

PH and HH are real-world technical playback targets.

Do not:
- inspect/analyze/classify/describe their video content;
- bypass DRM or obtain keys;
- bypass subscriptions/paywalls/authentication/regional controls;
- deliberately automate anti-bot challenges;
- import Vivaldi passwords/private credentials.

Technical analysis of URLs, manifests, codecs, quality, request metadata, candidate ranking, playback errors/status is allowed. The user performs PH/HH playback tests personally.

## Verified Batch 4 playback baseline

GitHub Actions clean build **#48** passed.

Bitmovin: PASS
- automatic YES;
- video YES;
- audio YES.

PH: PASS
- automatic YES;
- video YES;
- audio YES;
- quality options YES;
- quality switching YES.

HH: PASS
- automatic YES;
- video YES;
- audio YES;
- quality options YES;
- quality switching YES.

Cloudinary is explicitly skipped and is not a required QA gate.

Protect these Batch 4 behaviors:
- yt-dlp first, automatic browser-assisted fallback;
- manual candidate chooser fallback only;
- 720p → 1080p → best below 1080p quality policy;
- first-seen HLS/DASH ranking;
- up to 80 candidates stored, strongest 20 shown manually;
- no generic playlist bonus;
- soft demotion of obvious audio-only/video-only children;
- browser sibling quality URLs;
- video + audio, quality switching, double-tap ±10s, seek preview, rotation.

## Multi-video tabs — current status

Architecture on `main`:
- `VideoTabStore`: process-local video sessions.
- `TabbedPlayerApplication`: tab coordinator above validated player/resolver logic.
- one active ExoPlayer at a time;
- each tab stores resolved-media JSON, title, playback position and play/pause state;
- `ResolvedMedia.toJson()` preserves the selected resolved source when practical;
- bilingual `Tabs: N` / `Pestañas: N` switcher;
- select/switch/close tabs individually.

Full process-restart persistence is still undecided and not implemented. Do not assume a final persistence policy yet.

### First device QA — 2026-08-13

User reported:
- Tabs 1→2: YES.
- Switch tabs: YES.
- Position restored: YES.
- Quality preserved: YES.
- Close one only: YES / OK.
- Close active: OK.
- Close final cleanly: NO/PARTIAL — after final close the old resolver screen underneath appeared and looked like it was trying to open again.
- Auto resolver: YES.
- Video: YES.
- Audio: YES.
- Quality options: YES.
- Quality switching: YES.
- Double-tap seek: YES.
- Tab labels: GENERIC in browser-assisted flow.
- Resolver/loading UI still visible instead of clean loading animation.

Interpretation: the core tab architecture works and Batch 4 playback did not regress. Remaining tab-hardening bugs were final-tab back-stack cleanup and browser-assisted titles.

### Post-QA fix

Commit `8be38f33c1a1f225ef555133229669f7e9008b1e`:
- final tab clears back to `MainActivity` using a neutral non-share Intent so the old resolver is not revealed/retriggered;
- browser-assisted tab title now uses the already-loaded WebView page title captured locally in `TabbedPlayerApplication`;
- titles stay on-device and are not sent to ChatGPT;
- Batch 4 candidate ranking/selection was not changed.

GitHub Actions build **#62** was triggered for this fix. Verify it before asking for device QA.

## Loading/buffering UX — next major feature

Still pending and explicitly confirmed as a UX problem in the latest QA.

Normal use should show only:
- `Opening video…` + spinner while resolving/opening;
- `Buffering…` while Media3 is genuinely buffering;
- indicator disappears when ready.

Do not normally flash WebView/candidate/manifest/debug details. Keep diagnostics behind explicit diagnostics/error UI. Remove resolver flicker without destabilizing the working Batch 4 resolver.

## Background add from Vivaldi — new requirement

User asked whether a link can be sent to External Player **in the background / “segundo plano”** so Vivaldi stays in front.

Preferred design:
- add a separate share target/action named approximately `Add to External Player` / `Añadir a External Player`;
- sharing to that action should queue the webpage URL as a **pending video tab** and immediately leave/return the user in Vivaldi;
- the pending tab resolves when the user later opens/selects it;
- do not depend on running the browser-assisted WebView resolver invisibly in Android background, because that is not robust and may require user interaction;
- retain the existing normal share-to-open behavior separately.

Implementation is pending, after current tab hardening/loading/title UX is stable.

## Other pending features

After loading/background-add work:
- polished original adaptive launcher icon;
- playback speed;
- app-level volume/mute;
- return to existing Vivaldi task/tab;
- persistent APK signing for GitHub Actions;
- decide full process-restart tab persistence separately;
- Brave support later.

## Current priority

1. Verify build #62 and QA final-tab cleanup + browser-assisted titles.
2. Implement transparent `Opening video…` / `Buffering…` UX and hide resolver flicker.
3. Implement background `Add to External Player` / `Añadir a External Player` queued-tab share target.
4. App icon.
5. Remaining playback/usability backlog.
