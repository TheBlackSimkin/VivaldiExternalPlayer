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

Latest result reported by user:

- 20 possible videos were displayed (this exposed the old hard 20-candidate
  behavior).
- At least one candidate played.
- Audio was not a useful validation signal on that page/test.
- Do not use Cloudinary alone to validate complete audio+video selection.

### Bitmovin HLS/fMP4 demo

`https://bitmovin.com/demos/hls-fmp4/`

Use as the strongest safe proxy for master-vs-child HLS selection and complete
video+audio detection.

Latest result reported by user:

- 5 possible videos detected.
- A manually selected candidate played with correct audio+video.
- The recommended candidate was NOT the correct candidate.

This is the key regression Batch 4 must fix.

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

Batch 3 already fixed webpage-as-video false positives and carried declared
quality metadata.

Batch 4 implementation changes:

- adds a real automatic first attempt after candidate discovery becomes quiet;
- makes the manual list a fallback named "Choose another video";
- replaces technical candidate labels with plain-language descriptions;
- stores up to 80 candidates instead of deleting the oldest item at 20;
- manual chooser shows only the strongest 20 if a page is extremely noisy;
- no longer gives a generic bonus to every path containing `playlist`;
- gives first-seen order real ranking weight for HLS/DASH so a top-level master
  can beat later child renditions;
- softly demotes obvious audio-only/video-only child paths;
- preserves page-config family IDs so sibling quality URLs can be passed to the
  player together.

No media imagery is inspected.

### PlayerActivity

- Media3 ExoPlayer.
- Progressive/HLS/DASH playback.
- Merged separate video/audio support for yt-dlp.
- Adaptive quality track selection.
- Frame-extractor seek previews.
- Playback diagnostics.

Batch 4 implementation changes:

- supports browser quality switching between sibling URLs (for example separate
  720p and 1080p page-config streams), preserving playback position;
- keeps existing adaptive HLS/DASH quality switching unchanged when a real
  master manifest exposes multiple Media3 tracks;
- updates Diagnostics after `STATE_READY` with explicit `Video: yes/no`,
  `Audio: yes/no`, and available qualities instead of leaving a successful
  player at `waiting for playback result`.

## 7. Verified real-target results before Batch 4

### Vivaldi Share

PASS:

- app appears in Share;
- correct URL is received automatically.

### Pornhub (PH)

Latest user test:

- URL tested:
  `https://www.pornhub.com/view_video.php?viewkey=68913ce2533cb`
- 6 possible videos detected.
- First/Recommended candidate played correctly.
- Source was HLS at `em-h.phncdn.com` with declared 720p.
- Playback worked, but quality could not be changed.

Interpretation:

- primary stream discovery is now working;
- the remaining PH issue is quality switching when page configuration exposes
  separate quality URLs rather than one multi-quality master manifest.

### HentaiHaven (HH)

Latest user test:

- URL tested:
  `https://hentaihaven.xxx/watch/nuki-nuki-zupposism/episode-1/`
- 6 possible videos detected.
- First/Recommended candidate worked as expected.
- HLS host: `octopusmanifest.org`.
- Audio/video and quality behavior were correct.

HH is the strongest real-target regression baseline and must not be broken.

## 8. Batch 4 QA order

Build gate first, then:

1. Bitmovin safe proxy — automatic first candidate must be complete video+audio.
2. PH — automatic first attempt should play the same working main video, and
   Quality should expose sibling qualities if the page provides them.
3. HH — automatic first attempt must remain correct; existing quality behavior
   must remain intact.
4. Cloudinary — verify noisy-page handling and that the app does not present the
   manual list as the normal workflow.

For every QA request, ChatGPT must provide:

- one detailed code block containing exactly what to test, Expected, and Result
  fields;
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

Direct GitHub reading succeeded on 2026-08-11. The repository showed 47 commits
at that check. Continue verifying direct access on every user turn as requested.

After Batch 4 is merged, the GitHub `main` branch is the project source of truth.
Use an uploaded ZIP only if direct repository access fails or the user explicitly
says the ZIP is newer.

## 11. Backlog after resolver reliability

1. Playback-speed control.
2. App-level volume/mute.
3. Return to existing Vivaldi task/tab.
4. Persistent APK signing for GitHub Actions.
5. Brave evaluation only after Vivaldi is reliable.
