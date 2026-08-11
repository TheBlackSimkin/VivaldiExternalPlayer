# Vivaldi External Player — Project State

> Operational memory for this project. Update this file whenever requirements, architecture, tests, failures, decisions, or next steps materially change.

## 1. Project purpose

Vivaldi External Player is a personal Android application which receives a normal web page URL from a mobile browser, resolves an accessible non-DRM video stream when possible, and plays that stream with AndroidX Media3 / ExoPlayer instead of the site's built-in video player.

Primary environment:

- Android 13 is the user's current target device environment.
- Vivaldi Mobile Browser is the Phase 1 browser.
- Brave Mobile Browser may be investigated later, after the Phase 1 flow works reliably.
- User-facing app text should support English and Spanish where relevant.

Primary target sites:

- Pornhub
- HentaiHaven

The project must not analyze or classify the adult video content. Testing on those target sites is limited to user-performed playback success/failure and technical information such as URLs, request types, HTTP errors, codecs, manifests, and player logs.

## 2. Hard boundaries

The app is not intended to:

- bypass DRM or obtain DRM keys;
- bypass subscriptions or paywalls;
- defeat authentication requirements;
- bypass regional restrictions;
- import private browser credentials without an explicit future design decision;
- deliberately automate or defeat anti-bot challenges;
- inspect, classify, summarize, or analyze adult video imagery.

The intended target use is logged out and limited to media which the user can already access normally in the browser.

A browser-assisted resolver may observe normal requests made by its own Android WebView after the user loads and interacts with the page. It must not replace protected responses, solve challenges, or configure DRM license acquisition.

## 3. User requirements

### Playback

- Prefer exactly 720p when available.
- If 720p is unavailable, prefer 1080p.
- If neither is available, use the best available quality below 1080p.
- Double tap left: seek backward 10 seconds.
- Double tap right: seek forward 10 seconds.
- Efficient buffering.
- Timeline scrubbing should show a thumbnail preview where technically possible.
- User-facing controls should include normal play/pause/seeking plus quality, playback speed, and app-level volume/mute.
- Rotation between portrait and landscape should work normally.

### Browser workflow

Preferred final flow:

1. User is on the original page in Vivaldi.
2. User uses Vivaldi's Android Share action and selects this app.
3. The app resolves and plays the media.
4. A dedicated Return to browser control should ultimately unwind this app and reveal the existing Vivaldi task/tab instead of opening a fresh copy of the URL.

Manual URL pasting remains useful for testing.

### Development workflow

- The user is not an advanced developer.
- Source files should contain abundant English comments explaining what important sections do and why.
- When a source file must be changed in chat, provide the FULL replacement file, not only a patch or isolated lines.
- Development will normally happen in temporary chats.
- Keep this file and `CHAT_BOOTSTRAP.md` updated when relevant.
- Until direct GitHub access succeeds, re-check the public repository after each new user message.

## 4. Current architecture

### MainActivity

- Accepts `ACTION_SEND` / `text/plain` from a browser.
- Extracts the first HTTP(S) URL from shared text.
- Also supports manual URL pasting.
- First attempts the Python/yt-dlp resolver.
- Batch 1 change: after a direct yt-dlp failure, automatically opens the browser-assisted resolver instead of requiring the user to notice a hidden fallback button.
- A visible browser-assisted retry button remains available when the user returns to MainActivity.

### Python resolver (`resolver.py`)

- Runs through Chaquopy.
- Uses yt-dlp.
- Does not download files; it asks yt-dlp for playable media information.
- Uses the 720p-first format-selection policy.
- Rejects media which yt-dlp marks as DRM.
- Returns JSON describing either one media source or separate video/audio sources.

### BrowserResolverActivity

- Loads the original page in this app's own Android WebView.
- Does not import Vivaldi cookies.
- Observes normal WebView and service-worker requests without replacing them.
- Original version detected obvious HLS, DASH, MP4, and WebM URLs based mainly on URL text.
- Batch 1 change: also reads normal page `<video>` / `<source>` URLs and browser Performance API resource entries so JavaScript-created or extensionless media URLs are less likely to be missed.
- Passes relevant WebView request headers and this WebView session's cookies to Media3 for the chosen media URL.

### PlayerActivity

- Uses Media3 ExoPlayer.
- Supports progressive media, HLS, DASH, and separate video/audio merge when the resolver returns those sources.
- Uses `FLAG_SECURE` so Android does not expose player frames through screenshots/recent-app thumbnails.
- Provides timeline frame previews using Media3 `FrameExtractor` when the remote source supports seeking/frame extraction.
- No playback-history database or disk media cache is intended.

## 5. Current known results

### Known working development target

- xvideos.com previously worked sufficiently to test the custom player controls. It is not a project target and should not drive site-specific implementation.

### Pornhub direct-resolver failure

Test URL used by the user:

`https://www.pornhub.com/view_video.php?viewkey=67f53d82bda2d`

Observed app error:

- yt-dlp PornHub extractor;
- unable to download webpage;
- HTTP 410 Gone.

This is a resolver/network-access failure before external playback begins; it is not evidence of a Media3 playback failure.

### HentaiHaven direct-resolver failure

Test URL used by the user:

`https://hentaihaven.xxx/watch/tiny-evil/episode-1/`

Observed app error:

- yt-dlp generic extractor;
- HTTP 403 caused by a Cloudflare anti-bot challenge;
- yt-dlp suggested browser impersonation support.

Cloudflare 403 is not DRM by itself. The project is not currently enabling yt-dlp browser impersonation. The first preferred compromise is a real embedded WebView for normal user-driven page loading, followed by observation of the resulting non-DRM media request.

## 6. curl_cffi / impersonation decision

Adding `curl_cffi` does not inherently break the project's DRM boundary. It is a networking dependency which can make yt-dlp requests resemble supported browsers at the TLS/request layer.

The concern is HOW it would be used. Deliberately forcing impersonation to get around an anti-bot/access-control decision could conflict with the project's terms/access-control boundary. Separately, `curl_cffi` includes native components, so Android/Chaquopy packaging compatibility would have to be verified.

Therefore `curl_cffi` is not being added in Batch 1. Before a future site-specific impersonation experiment, re-check both technical compatibility and the target site's current terms.

## 7. Safe development test matrix

Use non-adult test material whenever ChatGPT needs to inspect playback behavior directly.

### Internet Archive (`archive.org`)

Use public-domain/open material for:

- direct yt-dlp resolution;
- normal player behavior;
- quality selection;
- seeking;
- timeline thumbnail extraction.

### Bitmovin public player demos (`bitmovin.com/demos`)

Use these for:

- JavaScript-created web players;
- HLS/DASH manifest requests;
- browser-assisted observation;
- behavior closer to a modern embedded streaming page than a simple direct MP4 page.

### Cloudflare Stream public examples

Use these when useful to separate:

- Cloudflare acting normally as a video CDN; from
- a Cloudflare anti-bot challenge on an unrelated target site.

Do not treat the Cloudflare Stream example as an anti-bot-bypass test.

## 8. Current dependency baseline

From the uploaded source snapshot:

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Kotlin: 2.0.21
- compileSdk / targetSdk: 36
- minSdk: 24
- JDK: 17
- Chaquopy: 17.0.0
- Python: 3.13
- yt-dlp: 2026.06.09
- Media3: 1.10.1
- ABI currently built: arm64-v8a

## 9. Current implementation status

### Batch 1 — prepared for the next build

- Automatic browser-assisted fallback after direct resolver failure.
- Manual browser-assisted retry button remains available.
- Broader browser-assisted media discovery from network requests, service workers, page media elements, and Performance API resources.
- English/Spanish localization started for MainActivity and BrowserResolverActivity.
- Extensive English maintenance comments added to every source/layout file touched by this batch.

Local checks completed for Batch 1:

- Android XML files in the batch parse successfully.
- Every new string reference exists in both English and Spanish.
- Full Android/Kotlin compilation still requires the GitHub Actions/Android SDK build.

### Next planned batch

After confirming Batch 1 builds, implement the remaining confirmed player requirements without mixing them into resolver debugging:

- Return to existing browser task/tab;
- explicit app-level volume/mute control;
- explicit playback-speed control;
- complete English/Spanish player localization;
- continue adding explanatory comments to files when touched.

## 10. What the next phone test should answer

Do not repeat an unchanged direct-resolver test merely to confirm the same 410/403. The direct failure is already known.

The useful new test starts AFTER the direct failure automatically opens the WebView fallback. Record:

- whether the target page loads in the embedded WebView;
- whether normal playback can be started inside that WebView;
- whether a "Play detected stream" button appears;
- how many candidates appear and whether they are HLS, DASH, MP4, WebM, or Direct media;
- whether selecting a candidate starts Media3 playback;
- if Media3 fails, the exact playback error.

## 11. GitHub access status

Public repository:

`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

As of 2026-08-11, ChatGPT web access still returns a cache miss for the repository and search has not indexed it. The uploaded ZIP is therefore the current source of truth. Re-check GitHub after every new user message until direct access succeeds, then update this section.

## 12. Change log

### 2026-08-11

- Created this project-state document and `CHAT_BOOTSTRAP.md`.
- Confirmed Phase 1 is Vivaldi + Android + Pornhub/HentaiHaven; Brave is later.
- Confirmed logged-out target usage.
- Confirmed quality preference: 720 -> 1080 -> best lower than 1080.
- Confirmed 10-second double-tap seeking.
- Confirmed normal portrait/landscape rotation is preferred.
- Confirmed audio requirement means app volume/mute, not multi-language track selection.
- Confirmed final Return to browser should reveal the existing browser task/tab.
- Added Internet Archive, Bitmovin demos, and Cloudflare Stream examples as safe development test families.
- Clarified that `curl_cffi` itself does not violate the DRM boundary; the concern is the intended use of impersonation plus Android packaging complexity.
- Prepared Batch 1 resolver improvements and partial EN/ES localization.
