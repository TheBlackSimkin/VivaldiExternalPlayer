# Temporary Chat Bootstrap — Vivaldi External Player

I am continuing an existing Android project called **Vivaldi External Player**.

Public repository:

`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Before every response, verify whether you can still read this repository
directly and briefly state the result. If direct access fails, use the newest ZIP
I provide and do not claim to have read files you could not access.

Before changing code, read `PROJECT_STATE.md` in full. Treat it as the
operational memory/source of truth. Update `PROJECT_STATE.md` and this file when
requirements, architecture, tests, failures, decisions, or next steps materially
change.

## Communication preferences

- Keep the conversation in **English**.
- Windows and Vivaldi UI are normally **Spanish**, so give Spanish UI labels when
  instructions depend on what I see on screen.
- The Android app itself must remain **bilingual English/Spanish**.
- I am not an advanced developer: explain behavior in plain language.
- Candidate-selection UI should tell me what I am looking at in plain language;
  host/protocol/discovery-source jargon is not useful as the main description.
- Source code should contain abundant English comments.
- If you ask me to replace a source file manually, give the FULL replacement
  file in one code block.

## QA response format

Whenever you give me a QA test, always provide two code blocks:

1. A detailed block with test steps, **EXPECTED**, and **RESULT**.
2. A separate compact block containing only the short answer format I should
   fill in and send back.

Keep the answer block simple and compact. Never ask me to send PH/HH page/video
titles to ChatGPT; title strings remain local to the Android app.

## Project goal

This is a personal Android external video player intended primarily for Vivaldi
Mobile Browser. Phase 1 targets Pornhub (PH) and HentaiHaven (HH). The app
receives a browser-shared page URL, resolves an accessible non-DRM media stream,
and plays it with Media3 / ExoPlayer.

## Boundaries

- Do not bypass DRM or obtain DRM keys.
- Do not bypass subscriptions/paywalls, authentication, or regional controls.
- Do not deliberately automate anti-bot challenges.
- Intended target usage is logged out.
- ChatGPT must never inspect, analyze, classify, summarize, or describe PH/HH
  video content itself.
- I perform PH/HH playback tests on my own device and report only technical
  results.
- ChatGPT may analyze technical URLs, manifests, containers, codecs,
  resolutions, request metadata, candidate ranking, and playback errors/status.
- Video/page titles may be captured locally by the app for tab labels, but do not
  ask me to send PH/HH title text to ChatGPT and do not inspect/analyze those
  titles as content.
- Use safe non-adult proxy pages when direct inspection is needed.

## Required current resolver workflow

The app should NOT normally make me search manually through many detected
videos.

Expected flow:

1. Share page from Vivaldi.
2. Direct yt-dlp attempt runs first.
3. If it fails, browser-assisted resolution opens automatically.
4. Browser-assisted resolution detects candidates and automatically tries the
   best match after discovery stabilizes.
5. Manual "Choose another video" list is only a fallback if the first attempt
   is wrong.
6. Quality policy: 720p first, otherwise 1080p, otherwise best below 1080p.

Other existing requirements:

- double-tap left/right = -10/+10 seconds;
- timeline preview where supported;
- quality controls for adaptive manifests and separate per-quality URLs;
- playback speed pending;
- volume/mute pending;
- portrait/landscape rotation;
- final Return to existing Vivaldi task/tab pending.

## Current multi-video tab implementation

The first multi-video tab/session architecture is now implemented on GitHub
`main`; device QA is still pending.

Architecture:

- `VideoTabStore` keeps process-local video sessions.
- Every newly resolved video entering `PlayerActivity` without an existing tab ID
  becomes a new independent tab automatically.
- Each tab stores resolved-media JSON, playback position and play/pause state.
- `ResolvedMedia.toJson()` lets the app remember a quality-switched source.
- `TabbedPlayerApplication` sits above the validated Batch 4 player/resolver flow
  and adds the tab UI without changing resolver ranking logic.
- The player shows a bilingual `Tabs: N` / `Pestañas: N` button.
- The tab switcher can select any tab and close tabs individually.
- Switching tabs recreates one ExoPlayer for the selected session and restores
  its saved position/play state; several ExoPlayers are not kept alive at once.
- Closing the active tab switches to a neighboring remaining tab; closing the
  final tab closes the player.
- The first compatibility layer reads the private current `ResolvedMedia` model
  reflectively when saving a tab, solely so selected quality can be preserved
  without rewriting the validated Batch 4 `PlayerActivity`. Replace this with an
  explicit session API later if/when the player is refactored.

Full app/process restart persistence is **not implemented in this first tab
batch**. The product decision is still open; do not assume tabs must or must not
survive a complete restart.

### Tab titles

Desired title-source order remains:

1. resolver/yt-dlp title;
2. browser-assisted page metadata/title;
3. fallback `Video`.

Current status:

- yt-dlp/direct tabs already inherit the resolver title;
- browser-assisted tabs still inherit the old generic browser title;
- local WebView page-title capture is the next feature after first tab device QA.

Do not expose PH/HH title text to ChatGPT during QA.

## Transparent loading/buffering UX

Still pending after first tab QA. Normal use should hide technical resolver
steps and show only simple user-facing states such as:

- `Opening video…` with a spinner while resolving/loading;
- `Buffering…` while Media3 is genuinely buffering;
- no normal WebView/candidate/manifest/debug details flashing before playback.

Technical diagnostics should stay behind an explicit diagnostics/error path.
The brief browser-resolver screen/flicker remains an explicit UX issue, but it
must be removed without changing the working Batch 4 selection logic.

## App icon

Still pending after loading UX. Replace the prototype launcher icon with a
polished, recognizable original Android adaptive icon. Do not copy Vivaldi or
another product's trademarked icon.

## Current verified baseline

### Batch 4 playback baseline

GitHub Actions clean build **#48** passed from the final cleaned Batch 4 `main`.

Bitmovin safe proxy: **PASS**

- automatic playback: YES;
- video: YES;
- audio: YES;
- no manual candidate selection required;
- brief browser-resolver transition remains a UX issue.

Pornhub (PH): **PASS**

- automatic playback: YES;
- video: YES;
- audio: YES;
- multiple quality options: YES;
- quality switching: YES.

HentaiHaven (HH): **PASS**

- automatic playback: YES;
- video: YES;
- audio: YES;
- quality options: YES;
- quality switching: YES;
- no additional issues reported.

Cloudinary is explicitly skipped and is **not** a required QA gate.

### First multi-tab code build

GitHub Actions build **#59** for commit
`57dd543c7058f79f5357c789344db7556e1747fb` passed:

- Build debug APK: PASS.
- Upload APK: PASS.

Device QA is still required before the tab feature is accepted.

## Batch 4 resolver implementation summary — protect from regression

- automatic best-candidate first attempt;
- manual candidate chooser only as fallback;
- plain-language candidate descriptions;
- preserve up to 80 candidates instead of deleting the oldest at 20;
- show only the strongest 20 in the manual fallback list;
- meaningful first-seen HLS/DASH ranking;
- removed the generic `playlist` bonus;
- soft-demote obvious audio-only/video-only child renditions;
- group page-config sibling quality URLs;
- let PlayerActivity switch between sibling quality URLs;
- successful diagnostics report video/audio/quality state.

Do not change this selection logic merely to implement tabs/titles/loading UI.

## Current prioritized backlog

1. Device-QA and harden the first multi-video tab/session implementation.
2. Browser-assisted per-tab original page/video title using local WebView title
   metadata; direct/yt-dlp titles already work.
3. Transparent `Opening video…` / `Buffering…` UX and removal of resolver
   flicker without destabilizing Batch 4.
4. Polished custom Android launcher icon.
5. Playback-speed control.
6. App-level volume/mute.
7. Return to existing Vivaldi task/tab.
8. Persistent APK signing for GitHub Actions.
9. Decide full process-restart tab persistence separately.
10. Brave evaluation after Vivaldi behavior is mature.
