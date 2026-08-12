# Vivaldi External Player — Project State

> Operational memory for this project.
>
> Update this file whenever requirements, architecture, tests, failures,
> decisions, safe test proxies, or next steps materially change.

## 1. Purpose

Vivaldi External Player is a personal Android application which receives a
normal webpage URL from a mobile browser, discovers an accessible non-DRM media
stream when possible, and plays it through AndroidX Media3 / ExoPlayer.

Primary environment:

- Android 13 target device.
- Vivaldi Mobile Browser for Phase 1.
- Brave may be considered later.
- Relevant user-facing UI should support English and Spanish.

Primary real-world targets:

- Pornhub
- HentaiHaven

Only the user performs media-content testing on those real targets.

ChatGPT works only with the technical playback layer: URLs, manifests,
containers, codecs, resolutions, request metadata, errors, candidate ranking
and whether playback technically succeeds.

## 2. Content/testing boundary

ChatGPT must NOT:

- inspect or analyze adult video imagery;
- describe or classify adult media content;
- use real target videos as visual test material.

The user alone performs PH/HH playback verification.

When ChatGPT needs a directly inspectable test environment, use safe/SFW proxy
sites selected for similar technical behavior.

This rule must be preserved across temporary chats.

## 3. Access/control boundaries

The app is not intended to:

- bypass DRM or obtain DRM keys;
- bypass subscriptions/paywalls;
- defeat authentication requirements;
- bypass regional restrictions;
- deliberately automate anti-bot challenges;
- import Vivaldi passwords/private credentials;
- inspect media content semantically.

The intended target use is logged out and limited to media which the user can
already access normally in the browser.

The browser-assisted resolver may observe normal technical state and requests
made by its own WebView.

## 4. Required playback behavior

Quality policy:

1. Exact 720p when available.
2. Otherwise 1080p.
3. Otherwise the highest available quality below 1080p.

Other requirements:

- Double tap left: seek backward 10 seconds.
- Double tap right: seek forward 10 seconds.
- Efficient buffering.
- Seek-bar thumbnail preview where technically possible.
- Normal play/pause/seeking.
- Quality controls.
- Playback-speed control — pending.
- App-level volume/mute — pending.
- Normal portrait/landscape rotation.
- Dedicated Return to existing Vivaldi task/tab — pending.

## 5. Preferred final workflow

1. User is viewing the original page in Vivaldi.
2. User chooses Android Share.
3. User selects External Video Player.
4. App discovers the correct stream.
5. Video opens in the custom player.
6. Return-to-browser should reveal the existing Vivaldi task/tab.

Manual URL paste remains useful for debugging.

## 6. Safe proxy sites

### PH technical proxy — Cloudinary legacy Video Player demo

Primary URL:

`https://cloudinary.github.io/video-player-demo/player.html`

Why this is now the preferred PH proxy:

- safe/SFW material;
- JavaScript-configured players;
- multiple players/videos on one page;
- progressive sources;
- adaptive HLS;
- playlists;
- advertising examples;
- enough candidate noise to test main-video-vs-ad selection safely.

Use it to develop:

- false-candidate filtering;
- multiple-video identification;
- advertisement demotion;
- page/player configuration discovery;
- candidate ranking;
- later click reduction.

Archive.org is no longer the primary PH proxy.

### HH technical proxy — Bitmovin HLS/fMP4 demo

Primary URL:

`https://bitmovin.com/demos/hls-fmp4/`

Why:

- safe/SFW material;
- JavaScript player;
- HLS/fMP4;
- top-level/master playlist plus child rendition requests;
- adaptive quality tracks;
- previously reproduced audio-only, video-only and complete-stream candidates.

Use it to develop:

- master-vs-child HLS ranking;
- full video+audio stream selection;
- adaptive quality discovery;
- 720p preference;
- buffering behavior.

## 7. Current architecture

### MainActivity

- Accepts ACTION_SEND / text/plain.
- Extracts HTTP(S) URL from browser share.
- Supports manual URL paste.
- Attempts yt-dlp first.
- Automatically opens browser-assisted fallback after direct failure.

### resolver.py

- Runs yt-dlp through Chaquopy.
- Does not download files.
- Rejects media marked DRM by yt-dlp.
- Prefers Media3-friendly MP4/M4A and WebM combinations.
- Uses project quality policy.

### BrowserResolverActivity

Observes:

- WebView requests;
- Service Worker requests;
- page `<video>` elements;
- page `<source>` elements;
- Performance API resource URLs.

Batch 2:

- preserved first-seen order;
- ranked adaptive manifests;
- demoted obvious ad hosts;
- transferred WebView headers/cookies.

Batch 3 adds:

- reject the current/original webpage itself as a media candidate;
- reject traditional webpage URLs falsely labelled as video;
- reject known-unhelpful legacy video candidates such as Ogg video/AVI;
- read technical mediaDefinitions-style player configuration already exposed
  in the loaded page;
- carry declared quality metadata into candidate ranking;
- prefer 720p, then 1080p, then lower qualities;
- use current-page Referer/Origin context for page-config/adaptive streams.

No media imagery is inspected.

### PlayerActivity

- Media3 ExoPlayer.
- progressive/HLS/DASH support;
- merged separate video/audio support;
- FLAG_SECURE;
- frame-extractor seek previews;
- Media3 playback diagnostics;
- browser adaptive quality discovery;
- browser track selection.

## 8. Verified results

### Vivaldi Share

PASS:

- app appears in Share;
- correct URL is received automatically.

### HentaiHaven

Current strongest real-target success.

Batch 2 result:

- seven candidates observed;
- Recommended candidate was the correct complete stream;
- external playback worked;
- full video+audio worked;
- quality button became functional;
- selectable quality options were available.

This is the regression baseline.

Do not inspect HH media content. User performs this test.

### Pornhub

Batch 2 diagnostics identified the blank preferred candidate.

The candidate shown as MP4 was actually:

- resolver: browser;
- host: site host;
- path: `/view_video.php`;
- MIME: video/mp4;
- Media3 error:
  `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003)`.

Interpretation:

The webpage document itself was falsely classified as MP4 because a page video
element supplied the page URL together with a video MIME hint.

A separate advertising candidate played correctly.

Therefore PH is no longer an unknown black-screen problem. The immediate bug is
false candidate discovery plus correct technical player-media discovery.

Batch 3 directly addresses this.

Do not inspect PH media content. User performs this test.

### Archive BigBuckBunny

Batch 2 browser candidate:

- `.ogg`;
- Media3 `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003)`.

Archive is retained only as a legacy-container regression test.

It is no longer a primary proxy because it is less representative of the target
web-player architecture than Cloudinary/Bitmovin.

## 9. Batch 3 — prepared, build pending

Files changed:

- `BrowserResolverActivity.kt`
- English `strings.xml`
- Spanish `strings.xml`
- `PROJECT_STATE.md`

Goals:

1. Eliminate webpage-as-video false positives.
2. Eliminate known unsuitable legacy page-video candidates.
3. Discover technical page-player configuration where available.
4. Preserve declared quality metadata.
5. Rank exact 720p before 1080p before lower qualities.
6. Preserve HH/Bitmovin HLS behavior.
7. Keep all visual/content testing on safe proxies or on the user's own device.

## 10. Batch 3 QA after green build

Safe tests first:

1. Cloudinary PH proxy.
2. Bitmovin HH proxy.

Only after those:

3. User performs PH technical playback test.
4. User performs HH regression test.

ChatGPT should receive only:

- candidate list;
- candidate type;
- host;
- declared quality;
- diagnostics;
- playback yes/no;
- audio/video yes/no;
- quality options.

No adult video imagery is needed.

## 11. Immediate backlog after correct PH stream discovery

1. Reduce excessive number of taps.
2. Avoid forcing user to manually search a whole webpage for its main player.
3. Handle multiple actual videos, ads, previews and embedded players.
4. Automatic primary-video selection when confidence is high.
5. Keep manual candidate chooser as fallback.
6. Add playback-speed control.
7. Add app-level volume/mute.
8. Add Return to existing Vivaldi task/tab.
9. Configure persistent APK signing for GitHub Actions.
10. Evaluate Brave only after Vivaldi is reliable.

## 12. Development workflow

- User is not an advanced developer.
- Explain decisions plainly.
- Source files should contain abundant English comments.
- When changing a source file in chat, provide the FULL replacement file.
- Keep PROJECT_STATE.md and CHAT_BOOTSTRAP.md current.
- Project is intentionally developed in temporary chats.

## 13. GitHub access

Repository:

`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

As of 2026-08-11, ChatGPT's web fetch still returns a cache miss.

Latest uploaded ZIP plus subsequent in-chat full-file replacements remain the
working source of truth.

Continue checking GitHub after each user message until direct access succeeds.# Vivaldi External Player — Project State

> Operational memory for this project.
>
> Update this file whenever requirements, architecture, tests, failures,
> decisions, safe test proxies, or next steps materially change.

## 1. Purpose

Vivaldi External Player is a personal Android application which receives a
normal webpage URL from a mobile browser, discovers an accessible non-DRM media
stream when possible, and plays it through AndroidX Media3 / ExoPlayer.

Primary environment:

- Android 13 target device.
- Vivaldi Mobile Browser for Phase 1.
- Brave may be considered later.
- Relevant user-facing UI should support English and Spanish.

Primary real-world targets:

- Pornhub
- HentaiHaven

Only the user performs media-content testing on those real targets.

ChatGPT works only with the technical playback layer: URLs, manifests,
containers, codecs, resolutions, request metadata, errors, candidate ranking
and whether playback technically succeeds.

## 2. Content/testing boundary

ChatGPT must NOT:

- inspect or analyze adult video imagery;
- describe or classify adult media content;
- use real target videos as visual test material.

The user alone performs PH/HH playback verification.

When ChatGPT needs a directly inspectable test environment, use safe/SFW proxy
sites selected for similar technical behavior.

This rule must be preserved across temporary chats.

## 3. Access/control boundaries

The app is not intended to:

- bypass DRM or obtain DRM keys;
- bypass subscriptions/paywalls;
- defeat authentication requirements;
- bypass regional restrictions;
- deliberately automate anti-bot challenges;
- import Vivaldi passwords/private credentials;
- inspect media content semantically.

The intended target use is logged out and limited to media which the user can
already access normally in the browser.

The browser-assisted resolver may observe normal technical state and requests
made by its own WebView.

## 4. Required playback behavior

Quality policy:

1. Exact 720p when available.
2. Otherwise 1080p.
3. Otherwise the highest available quality below 1080p.

Other requirements:

- Double tap left: seek backward 10 seconds.
- Double tap right: seek forward 10 seconds.
- Efficient buffering.
- Seek-bar thumbnail preview where technically possible.
- Normal play/pause/seeking.
- Quality controls.
- Playback-speed control — pending.
- App-level volume/mute — pending.
- Normal portrait/landscape rotation.
- Dedicated Return to existing Vivaldi task/tab — pending.

## 5. Preferred final workflow

1. User is viewing the original page in Vivaldi.
2. User chooses Android Share.
3. User selects External Video Player.
4. App discovers the correct stream.
5. Video opens in the custom player.
6. Return-to-browser should reveal the existing Vivaldi task/tab.

Manual URL paste remains useful for debugging.

## 6. Safe proxy sites

### PH technical proxy — Cloudinary legacy Video Player demo

Primary URL:

`https://cloudinary.github.io/video-player-demo/player.html`

Why this is now the preferred PH proxy:

- safe/SFW material;
- JavaScript-configured players;
- multiple players/videos on one page;
- progressive sources;
- adaptive HLS;
- playlists;
- advertising examples;
- enough candidate noise to test main-video-vs-ad selection safely.

Use it to develop:

- false-candidate filtering;
- multiple-video identification;
- advertisement demotion;
- page/player configuration discovery;
- candidate ranking;
- later click reduction.

Archive.org is no longer the primary PH proxy.

### HH technical proxy — Bitmovin HLS/fMP4 demo

Primary URL:

`https://bitmovin.com/demos/hls-fmp4/`

Why:

- safe/SFW material;
- JavaScript player;
- HLS/fMP4;
- top-level/master playlist plus child rendition requests;
- adaptive quality tracks;
- previously reproduced audio-only, video-only and complete-stream candidates.

Use it to develop:

- master-vs-child HLS ranking;
- full video+audio stream selection;
- adaptive quality discovery;
- 720p preference;
- buffering behavior.

## 7. Current architecture

### MainActivity

- Accepts ACTION_SEND / text/plain.
- Extracts HTTP(S) URL from browser share.
- Supports manual URL paste.
- Attempts yt-dlp first.
- Automatically opens browser-assisted fallback after direct failure.

### resolver.py

- Runs yt-dlp through Chaquopy.
- Does not download files.
- Rejects media marked DRM by yt-dlp.
- Prefers Media3-friendly MP4/M4A and WebM combinations.
- Uses project quality policy.

### BrowserResolverActivity

Observes:

- WebView requests;
- Service Worker requests;
- page `<video>` elements;
- page `<source>` elements;
- Performance API resource URLs.

Batch 2:

- preserved first-seen order;
- ranked adaptive manifests;
- demoted obvious ad hosts;
- transferred WebView headers/cookies.

Batch 3 adds:

- reject the current/original webpage itself as a media candidate;
- reject traditional webpage URLs falsely labelled as video;
- reject known-unhelpful legacy video candidates such as Ogg video/AVI;
- read technical mediaDefinitions-style player configuration already exposed
  in the loaded page;
- carry declared quality metadata into candidate ranking;
- prefer 720p, then 1080p, then lower qualities;
- use current-page Referer/Origin context for page-config/adaptive streams.

No media imagery is inspected.

### PlayerActivity

- Media3 ExoPlayer.
- progressive/HLS/DASH support;
- merged separate video/audio support;
- FLAG_SECURE;
- frame-extractor seek previews;
- Media3 playback diagnostics;
- browser adaptive quality discovery;
- browser track selection.

## 8. Verified results

### Vivaldi Share

PASS:

- app appears in Share;
- correct URL is received automatically.

### HentaiHaven

Current strongest real-target success.

Batch 2 result:

- seven candidates observed;
- Recommended candidate was the correct complete stream;
- external playback worked;
- full video+audio worked;
- quality button became functional;
- selectable quality options were available.

This is the regression baseline.

Do not inspect HH media content. User performs this test.

### Pornhub

Batch 2 diagnostics identified the blank preferred candidate.

The candidate shown as MP4 was actually:

- resolver: browser;
- host: site host;
- path: `/view_video.php`;
- MIME: video/mp4;
- Media3 error:
  `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003)`.

Interpretation:

The webpage document itself was falsely classified as MP4 because a page video
element supplied the page URL together with a video MIME hint.

A separate advertising candidate played correctly.

Therefore PH is no longer an unknown black-screen problem. The immediate bug is
false candidate discovery plus correct technical player-media discovery.

Batch 3 directly addresses this.

Do not inspect PH media content. User performs this test.

### Archive BigBuckBunny

Batch 2 browser candidate:

- `.ogg`;
- Media3 `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003)`.

Archive is retained only as a legacy-container regression test.

It is no longer a primary proxy because it is less representative of the target
web-player architecture than Cloudinary/Bitmovin.

## 9. Batch 3 — prepared, build pending

Files changed:

- `BrowserResolverActivity.kt`
- English `strings.xml`
- Spanish `strings.xml`
- `PROJECT_STATE.md`

Goals:

1. Eliminate webpage-as-video false positives.
2. Eliminate known unsuitable legacy page-video candidates.
3. Discover technical page-player configuration where available.
4. Preserve declared quality metadata.
5. Rank exact 720p before 1080p before lower qualities.
6. Preserve HH/Bitmovin HLS behavior.
7. Keep all visual/content testing on safe proxies or on the user's own device.

## 10. Batch 3 QA after green build

Safe tests first:

1. Cloudinary PH proxy.
2. Bitmovin HH proxy.

Only after those:

3. User performs PH technical playback test.
4. User performs HH regression test.

ChatGPT should receive only:

- candidate list;
- candidate type;
- host;
- declared quality;
- diagnostics;
- playback yes/no;
- audio/video yes/no;
- quality options.

No adult video imagery is needed.

## 11. Immediate backlog after correct PH stream discovery

1. Reduce excessive number of taps.
2. Avoid forcing user to manually search a whole webpage for its main player.
3. Handle multiple actual videos, ads, previews and embedded players.
4. Automatic primary-video selection when confidence is high.
5. Keep manual candidate chooser as fallback.
6. Add playback-speed control.
7. Add app-level volume/mute.
8. Add Return to existing Vivaldi task/tab.
9. Configure persistent APK signing for GitHub Actions.
10. Evaluate Brave only after Vivaldi is reliable.

## 12. Development workflow

- User is not an advanced developer.
- Explain decisions plainly.
- Source files should contain abundant English comments.
- When changing a source file in chat, provide the FULL replacement file.
- Keep PROJECT_STATE.md and CHAT_BOOTSTRAP.md current.
- Project is intentionally developed in temporary chats.

## 13. GitHub access

Repository:

`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

As of 2026-08-11, ChatGPT's web fetch still returns a cache miss.

Latest uploaded ZIP plus subsequent in-chat full-file replacements remain the
working source of truth.

Continue checking GitHub after each user message until direct access succeeds.
