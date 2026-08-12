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
- Use safe non-adult proxy pages such as Cloudinary and Bitmovin for direct
  inspection whenever practical.

## Required workflow

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

Other requirements:

- double-tap left/right = -10/+10 seconds;
- timeline preview where supported;
- quality controls for adaptive manifests and separate per-quality URLs;
- playback speed pending;
- volume/mute pending;
- portrait/landscape rotation;
- final Return to existing Vivaldi task/tab pending.

## Current Batch 4 status

GitHub Actions clean build **#48** passed from the final cleaned `main` branch.
Batch 4 is now the source of truth in GitHub.

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

### Cloudinary safe proxy

`https://cloudinary.github.io/video-player-demo/player.html`

Older pre-Batch-4 result exposed the old 20-candidate limit. The Batch 4 noisy-
page stress test is still pending and should be the next resolver QA.

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

Do not change the working resolver-selection logic solely to remove the brief
browser-resolver flicker until the remaining Cloudinary stress test is complete.
