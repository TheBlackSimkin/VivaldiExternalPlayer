# Vivaldi External Player — Project State

> Operational memory/source of truth for temporary chats.
> Update this file whenever requirements, tests, architecture, failures, or next
> steps materially change.

## 1. Purpose and environment

Vivaldi External Player is a personal Android application which receives a
normal webpage URL, discovers an accessible non-DRM media stream when possible,
and plays it through AndroidX Media3 / ExoPlayer.

Primary environment:

- Android phone for playback testing.
- Vivaldi Mobile Browser is the Phase 1 browser.
- Windows and Vivaldi UI are normally Spanish.
- Conversation with ChatGPT should remain English.
- The app must remain bilingual (English + Spanish user-facing UI).

Primary real-world targets:

- Pornhub (PH)
- HentaiHaven (HH)

The user performs all real-target media-content testing. ChatGPT may analyze
only technical playback information such as URLs, manifests, containers,
codecs, resolutions, request metadata, candidate ranking, and playback status.

## 2. Boundaries

Do not:

- inspect, classify, or describe adult video imagery;
- bypass DRM or obtain DRM keys;
- bypass subscriptions/paywalls, authentication, or regional restrictions;
- deliberately automate anti-bot challenges;
- import Vivaldi passwords/private credentials.

The browser-assisted resolver may observe normal technical state and requests
made by its own WebView.

## 3. Required playback behavior

Quality policy:

1. Exact 720p when available.
2. Otherwise 1080p.
3. Otherwise highest available quality below 1080p.

Required/desired behavior:

- Share from Vivaldi directly into the app.
- Automatic first playback attempt; do not make manual candidate selection the
  normal workflow.
- Manual candidate list remains only as fallback/debugging when the automatic
  choice is wrong.
- Candidate descriptions shown to the user should be plain language, not host,
  protocol, or discovery-source jargon.
- Double tap left/right = -10/+10 seconds.
- Efficient buffering.
- Timeline thumbnail preview where technically supported.
- Quality control for both adaptive manifests and pages exposing separate URLs
  per quality.
- Playback speed control — pending.
- App-level volume/mute — pending.
- Normal portrait/landscape rotation.
- Return to the existing Vivaldi task/tab — pending.

## 4. Preferred workflow

1. User opens a page in Vivaldi.
2. Android Share -> External Video Player.
3. yt-dlp direct resolution is attempted first.
4. If direct resolution fails, browser-assisted mode opens automatically.
5. Browser-assisted mode detects candidate streams and automatically tries the
   best match after discovery stabilizes.
6. If that choice is wrong, the user may go back and choose another detected
   video from a simplified fallback list.
7. Player opens with 720p preferred and quality controls when alternatives are
   available.

Manual URL paste remains useful for debugging.

## 5. Safe proxy sites

### Cloudinary video-player demo

`https://cloudinary.github.io/video-player-demo/player.html`

Use as a safe proxy for noisy pages with multiple players, playlists,
advertising examples, and many candidate resources.

Older result before Batch 4:

- 20 possible videos were displayed, exposing the old hard 20-candidate limit.
- At least one candidate played.
- Audio was not a useful validation signal in that test.

Batch 4 Cloudinary stress test is still pending.

### Bitmovin HLS/fMP4 demo

`https://bitmovin.com/demos/hls-fmp4/`

Batch 4 result: **PASS**.

User reported:

- automatic playback: YES;
- video: YES;
- audio: YES;
- no manual interaction was required;
- a brief browser-resolver screen was visible before the automatic redirect.

Interpretation:

- Batch 4 fixed the previous top-level-vs-child HLS ranking failure;
- the transient browser-resolver screen is a UX polish issue, not a selection
  failure;
- do not risk the working selection logic merely to remove that flicker until
  resolver reliability testing is complete.

## 6. Current architecture

### MainActivity

- Accepts ACTION_SEND / text/plain.
- Extracts HTTP(S) URL from browser share.
- Supports manual URL paste.
- Attempts yt-dlp first.
- Automatically opens browser-assisted fallback after direct failure.

### resolver.py

- Runs yt-dlp through Chaquopy.
- Does not download media files.
- Rejects media marked DRM by yt-dlp.
- Prefers Media3-friendly MP4/M4A/WebM combinations.
- Uses project quality policy.

### BrowserResolverActivity

Observes:

- WebView requests;
- Service Worker requests;
- page `<video>` / `<source>` elements;
- Performance API resource URLs;
- `mediaDefinitions`-style technical player configuration when exposed.

Batch 4 behavior:

- automatic best-candidate first attempt after candidate discovery becomes quiet;
- manual list is fallback only and is labeled "Choose another video";
- plain-language candidate descriptions;
- stores up to 80 candidates instead of deleting the oldest item at 20;
- manual chooser shows only the strongest 20 on extremely noisy pages;
- removed the generic `playlist` ranking bonus;
- first-seen HLS/DASH ordering has meaningful ranking weight;
- obvious audio-only/video-only child paths are softly demoted, not removed;
- page-config family IDs group sibling quality URLs.

No media imagery is inspected.

### PlayerActivity

- Media3 ExoPlayer.
- Progressive/HLS/DASH playback.
- Merged separate video/audio support for yt-dlp.
- Adaptive quality track selection.
- Frame-extractor seek previews.
- Playback diagnostics.

Batch 4 behavior:

- browser sibling quality URLs can be switched while preserving approximate
  playback position;
- existing adaptive HLS/DASH quality switching remains unchanged;
- Diagnostics update after STATE_READY with explicit video/audio/quality results.

## 7. Verified Batch 4 results

### Build gate

GitHub Actions clean build **#48** passed from the final cleaned repository state.

- Build debug APK: PASS.
- Upload APK: PASS.
- Artifact: `VivaldiExternalPlayer-debug-apk`.

### Vivaldi Share

PASS:

- app appears in Share;
- correct URL is received automatically.

### Bitmovin safe proxy

PASS:

- automatic selection: YES;
- video: YES;
- audio: YES;
- correct complete stream chosen automatically;
- brief browser-resolver transition remains a UX polish item.

### Pornhub (PH)

Batch 4 result: **PASS**.

User reported:

- automatic playback: YES;
- video: YES;
- audio: YES;
- multiple quality options available: YES;
- quality change: YES.

Interpretation:

- automatic primary-stream selection still works;
- the Batch 4 sibling-quality handoff fixed the earlier inability to switch
  quality on the tested PH page.

### HentaiHaven (HH)

Batch 4 result: **PASS**.

User reported:

- automatic playback: YES;
- video: YES;
- audio: YES;
- quality options: YES;
- quality change: YES;
- no additional problems reported.

Interpretation:

- Batch 4 preserved the existing HH adaptive behavior;
- HH remains a strong regression baseline.

## 8. Batch 4 QA status and next test

Completed:

1. Build gate — PASS.
2. Bitmovin — PASS.
3. PH — PASS, including quality switching.
4. HH — PASS, including quality switching.

Remaining resolver stress test:

5. Cloudinary — verify noisy-page handling, automatic first attempt, and that the
   app does not require the user to browse a long manual candidate list.

For every QA request, ChatGPT must provide:

- one detailed code block containing what to test, **EXPECTED**, and **RESULT**;
- a second short code block which the user can copy, fill in, and send back.

Keep the reply block compact.

## 9. Development workflow and communication

- Conversation language: English.
- Windows/Vivaldi UI: Spanish; give Spanish UI labels when relevant.
- App UI: bilingual English/Spanish.
- Explain decisions in plain English; the user is not an advanced developer.
- Source code should contain abundant English comments.
- When asking the user to replace a source file manually, provide the FULL file,
  not an isolated patch.
- Keep this file and `CHAT_BOOTSTRAP.md` current.
- Before every assistant response, verify whether the public GitHub repository
  can still be read directly and state the result briefly.

## 10. GitHub

Public repository:

`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Direct GitHub access is currently working through the connected GitHub tool.
The `main` branch is the project source of truth.

## 11. Backlog after resolver reliability

1. Polish the brief browser-resolver transition/flicker before automatic playback.
2. Playback-speed control.
3. App-level volume/mute.
4. Return to existing Vivaldi task/tab.
5. Persistent APK signing for GitHub Actions.
6. Brave evaluation only after Vivaldi is reliable.
