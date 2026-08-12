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

Keep the answer block simple and compact.

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

## Required current workflow

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

## New future requirements after Batch 4

### Multi-video tabs

The external player should gain its own tab system for videos moved from
Vivaldi.

- Every shared/moved video becomes an independent app video tab.
- Multiple video tabs may remain open simultaneously.
- The user can open a tab switcher, select any tab, and close individual tabs,
  conceptually similar to Vivaldi's tab workflow.
- Each open tab preserves its own playback position and media selection; preserve
  selected quality when practical.
- Closing one tab must not close unrelated tabs.
- Persistence across a full app/process restart is not yet specified and should
  be decided separately during implementation.

### Per-tab title

Every video tab must show the title of the original video/page instead of a
generic "Browser video" label.

Preferred local title sources:

1. resolver/yt-dlp title;
2. page metadata/title from browser-assisted resolution;
3. neutral fallback such as "Video".

Do not expose PH/HH title text to ChatGPT during QA.

### Transparent loading/buffering UX

The internal resolution process should be hidden during normal use. Do not show
the user every yt-dlp/WebView/candidate/manifest step.

Instead provide a polished simple state such as:

- "Opening video…" with a spinner while resolving/loading;
- "Buffering…" while Media3 is waiting for media;
- hide the indicator automatically when playback is ready.

Technical diagnostics should stay behind an explicit diagnostics/error path.
The current brief browser-resolver screen/flicker is now an explicit UX issue to
remove while preserving the working resolver logic.

### App icon

Replace the prototype launcher icon with a polished, recognizable custom Android
adaptive icon for Vivaldi External Player. It should be modern, readable at
launcher size, and original rather than copying another product's trademarked
icon.

## Current Batch 4 status

GitHub Actions clean build **#48** passed from the final cleaned `main` branch.
Batch 4 is the source of truth in GitHub.

### Bitmovin safe proxy

`https://bitmovin.com/demos/hls-fmp4/`

**PASS**:

- automatic playback: YES;
- video: YES;
- audio: YES;
- no manual candidate selection required.

A brief browser-resolver screen can flash before automatic playback. Treat this
as UX polish, not a selection failure.

### Pornhub

**PASS** on Batch 4:

- automatic playback: YES;
- video: YES;
- audio: YES;
- multiple quality options: YES;
- quality switching: YES.

This confirms Batch 4 fixed the earlier PH quality-switching problem.

### HentaiHaven

**PASS** on Batch 4:

- automatic playback: YES;
- video: YES;
- audio: YES;
- quality options: YES;
- quality switching: YES;
- no additional issues reported.

This confirms Batch 4 preserved the previously working HH adaptive behavior.

### Cloudinary

Older testing exposed the old 20-candidate limit. On 2026-08-12 the user chose
to **skip further Cloudinary testing**. Do not treat Cloudinary as a required QA
gate. It may remain only as an optional safe diagnostic proxy if a future noisy-
page resolver bug specifically needs it.

## Batch 4 implementation summary

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

## Current prioritized backlog

1. Multi-video app tabs/sessions: create, select, and close independent videos.
2. Per-tab original video/page titles.
3. Transparent "Opening video…" / "Buffering…" UX and removal of resolver
   flicker.
4. Polished custom Android launcher icon.
5. Playback-speed control.
6. App-level volume/mute.
7. Return to existing Vivaldi task/tab.
8. Persistent APK signing for GitHub Actions.
9. Brave evaluation after Vivaldi behavior is mature.
