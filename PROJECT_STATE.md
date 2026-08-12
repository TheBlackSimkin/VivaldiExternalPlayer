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

- inspect, classify, summarize, or describe adult video content;
- bypass DRM or obtain DRM keys;
- bypass subscriptions/paywalls, authentication, or regional restrictions;
- deliberately automate anti-bot challenges;
- import Vivaldi passwords/private credentials.

The browser-assisted resolver may observe normal technical state and requests
made by its own WebView.

Video/page titles may be captured locally by the Android app for tab labels, but
ChatGPT should not request, inspect, or analyze PH/HH title text as content.

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

## 4. Future multi-tab player requirements

The external player should eventually behave like a small tabbed browser for
videos moved from Vivaldi.

Required tab behavior:

1. Every video shared/moved from Vivaldi into Vivaldi External Player should
   create an independent app video tab.
2. Multiple video tabs may exist at the same time.
3. The user must be able to open the tab switcher, select any video tab, and
   close individual tabs, with interaction conceptually similar to Vivaldi's tab
   UI.
4. Each tab should preserve its own playback state while it remains open,
   including at minimum the selected media item and playback position. Preserve
   selected quality when practical.
5. Closing one tab must not close unrelated tabs.
6. When the active tab is closed, switch cleanly to another remaining tab or an
   empty/home state if none remain.
7. Persistence of tabs across a full app/process restart is not yet specified;
   decide this separately during implementation rather than assuming it.

### Tab titles

Each tab must display the title of the original video/page rather than a generic
label such as "Browser video".

Title-source preference for implementation:

1. resolver/yt-dlp video title when available;
2. page metadata/title already available to the app during browser-assisted
   resolution;
3. a neutral fallback such as "Video" if no usable title is available.

PH/HH title strings should remain local to the app and should not be sent to or
inspected by ChatGPT during testing.

## 5. Loading and buffering UX requirement

Resolution/loading internals should be transparent to the user.

The app should NOT normally expose technical stages such as yt-dlp attempts,
WebView candidate discovery, manifest ranking, or individual network steps.

Instead, show a small, polished user-facing state such as:

- "Opening video…" while resolving/loading the source;
- a visible progress/spinner indicator so the user knows the app is working;
- "Buffering…" when Media3 is waiting for enough media to continue playback.

Once playback is ready, the loading UI should disappear automatically.
Technical diagnostics remain available only when explicitly opened or when an
error requires useful troubleshooting information.

The existing brief browser-resolver screen/flicker before automatic playback is
therefore a UX problem to remove during this phase without changing the already
working candidate-selection logic.

## 6. Preferred workflow

Current single-video workflow:

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

Future tabbed workflow should keep steps 3-7 internal and attach the resulting
media session to the newly created video tab.

Manual URL paste remains useful for debugging.

## 7. Safe proxy sites and QA policy

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
  failure.

### Cloudinary video-player demo

Historical safe proxy:

`https://cloudinary.github.io/video-player-demo/player.html`

Older testing exposed the old hard 20-candidate limit. The user explicitly chose
on 2026-08-12 to **skip further Cloudinary testing**. It is no longer a required
QA gate. Keep it only as an optional diagnostic proxy if a future resolver bug
specifically needs a noisy multi-player page.

## 8. Current architecture

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

Future architecture will need a tab/session layer above the current single
PlayerActivity flow so several independent media sessions can be represented and
switched without discarding their state.

## 9. Verified Batch 4 results

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

## 10. QA status

Completed and accepted for Batch 4:

1. Build gate — PASS.
2. Bitmovin — PASS.
3. PH — PASS, including quality switching.
4. HH — PASS, including quality switching.

Cloudinary is explicitly skipped and is not required before moving on.

For every QA request, ChatGPT must provide:

- one detailed code block containing what to test, **EXPECTED**, and **RESULT**;
- a second short code block which the user can copy, fill in, and send back.

Keep the reply block compact.

## 11. App identity / launcher icon

The prototype launcher icon should be replaced with a polished, recognizable
custom icon for Vivaldi External Player.

Requirements:

- should read clearly at Android launcher size;
- should look intentional and modern rather than like a development placeholder;
- should fit the app's external-video-player identity without copying another
  product's trademarked icon;
- provide proper Android adaptive-icon assets and foreground/background variants
  when implemented.

## 12. Development workflow and communication

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

## 13. GitHub

Public repository:

`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Direct GitHub access is currently working through the connected GitHub tool.
The `main` branch is the project source of truth.

## 14. Current prioritized backlog

1. Multi-video tab/session system: create, select, and close independent video
   tabs moved from Vivaldi.
2. Per-tab original video/page title.
3. Transparent loading/buffering UX and removal of the resolver-screen flicker.
4. Polished custom Android launcher icon.
5. Playback-speed control.
6. App-level volume/mute.
7. Return to existing Vivaldi task/tab.
8. Persistent APK signing for GitHub Actions.
9. Brave evaluation only after Vivaldi behavior is mature.
