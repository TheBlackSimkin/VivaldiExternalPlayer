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

The project must not analyze or classify adult video content. Testing on target sites is limited to user-performed playback success/failure and technical information such as URLs, request types, HTTP errors, codecs, manifests, tracks, and player logs.

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

### UX requirements discovered during real testing

After correct-stream selection is reliable, reduce the current excessive number of taps/clicks. The present browser-assisted flow can require:

1. Share/open URL.
2. Wait for resolver.
3. Enter the embedded WebView.
4. Find the site's player inside the full webpage.
5. Start playback in the webpage.
6. Tap the detected-stream button.
7. Choose among several candidates.
8. Finally enter the external player.

This is acceptable for diagnosis but not for the final UX.

Pages can also contain multiple videos/media sources: the desired main video, advertisements, previews, and separate HLS audio/video renditions. Future selection must rank/associate these technically without analyzing adult imagery.

Possible technical signals include:

- `<video>` / `<source>` association;
- visibility and element dimensions;
- duration where available;
- media host relationship to page host;
- HLS/DASH master-vs-child playlist relationship;
- request ordering;
- separate audio/video rendition structure;
- obvious advertising infrastructure;
- Media3 track structure and playable-video presence.

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
- After a direct yt-dlp failure, automatically opens the browser-assisted resolver.
- A visible browser-assisted retry button remains available when the user returns to MainActivity.

### Python resolver (`resolver.py`)

- Runs through Chaquopy.
- Uses yt-dlp.
- Does not download files; it asks yt-dlp for playable media information.
- Rejects media which yt-dlp marks as DRM.
- Returns JSON describing either one media source or separate video/audio sources.
- Batch 2 changes format selection so desirable resolution alone cannot select an obviously legacy/unsupported container. It explicitly prefers MP4/M4A and WebM alternatives.
- Batch 2 also returns protocol/container/codec metadata for diagnostics.

### BrowserResolverActivity

- Loads the original page in this app's own Android WebView.
- Does not import Vivaldi cookies.
- Observes normal WebView and service-worker requests without replacing them.
- Also reads normal page `<video>` / `<source>` URLs and browser Performance API resource entries.
- Passes relevant WebView request headers and this WebView session's cookies to Media3 for the chosen media URL.
- Batch 2 preserves first-seen request order and ranks candidates instead of presenting reverse-recency order.
- Batch 2 gives adaptive HLS/DASH candidates a higher generic priority, prefers page-associated/same-site media, preserves old HLS requests as likely master playlists, and demotes obvious ad infrastructure without blocking any candidate.
- The top candidate is labeled Recommended, but all alternatives remain available.

### PlayerActivity

- Uses Media3 ExoPlayer.
- Supports progressive media, HLS, DASH, and separate video/audio merge when the resolver returns those sources.
- Uses `FLAG_SECURE` so Android does not expose player frames through screenshots/recent-app thumbnails.
- Provides timeline frame previews using Media3 `FrameExtractor` when the remote source supports seeking/frame extraction.
- No playback-history database or disk media cache is intended.
- Batch 2 adds copyable Media3 diagnostics using `Player.Listener.onPlayerError`.
- Diagnostics intentionally omit media URL query strings/tokens and do not expose cookies/request headers.
- Batch 2 reads browser HLS/DASH video tracks after Media3 prepares them and exposes actual quality choices.
- Browser automatic quality keeps adaptive renditions up to the project target where possible, so Media3 may still step down for buffering.

## 5. Verified QA results before Batch 2

### APK installation

- GitHub Actions Batch 1 eventually built successfully.
- Direct sideloading was intercepted by Google Play Protect on the user's Xiaomi/MIUI device.
- Installation succeeded using ADB from the user's laptop.
- Continue using ADB for development builds when convenient.

### Vivaldi Share

Verified PASS:

- The app appears in Android/Vivaldi Share.
- The URL is received automatically.

This means the browser-to-app Intent handoff is working.

### Internet Archive

Tested page:

`https://archive.org/details/BigBuckBunny`

Observed:

- The custom PlayerActivity opened.
- Controls were visible.
- No actual video rendered.
- Rotation worked.
- Quality UI existed, but could not be meaningfully verified without video.

Interpretation:

- Resolution did return a source; the failure occurred at Media3 playback or source compatibility.
- The Archive item can expose legacy AVI variants, while Media3's documented progressive container support does not include AVI.
- This motivated Batch 2's Media3-compatible yt-dlp container preference and playback diagnostics.
- Re-test after Batch 2 instead of assuming AVI was definitely the cause.

### Bitmovin safe browser-assisted test

Test page:

`https://bitmovin.com/demos/hls-fmp4/`

The old candidate chooser detected:

1. MP4 — empty external player.
2. MP4 — empty external player.
3. HLS — audio/music only.
4. HLS — video without audio.
5. HLS — full video with audio; correct stream.

Important inference:

- In the old implementation candidates were continually moved to the front, so the oldest HLS request appeared last.
- Bitmovin's published demo uses a top-level HLS manifest; the observed result is consistent with the master playlist being requested before child audio/video playlists.
- Batch 2 therefore preserves first-seen order and uses it as a ranking hint among otherwise-equal HLS candidates.

Quality selection did not work in Batch 1 because browser-resolved streams deliberately displayed `Quality: stream`. Batch 2 now queries Media3 tracks and adds browser-stream quality selection.

### Pornhub

A later test no longer stopped at the old yt-dlp HTTP 410; the browser-assisted path loaded the full site and detected two MP4 candidates.

Observed candidates in the old chooser:

1. `MP4 • es.pornhub.com • page` — external PlayerActivity opened but no video rendered.
2. `MP4 • ht-cdn2.adtng.com • network` — played successfully, but it was an advertisement rather than the desired main video.

What this proves:

- Browser observation works on the target page.
- Media3 can play at least some media transferred from that WebView session.
- The main remaining PH problem is correct-candidate selection/playability, not merely the old initial 410.
- Batch 2 keeps the page-associated candidate ranked above obvious advertising infrastructure but, crucially, now reports the exact Media3 failure if the preferred candidate is blank.

Do not analyze PH video imagery. User performs playback verification.

### HentaiHaven

Verified major PASS:

- The browser-assisted page loaded.
- Two candidates were detected.
- The second candidate was the intended video.
- External Media3 playback worked.

This proves the core architecture is viable on one of the two real targets:

`browser page -> WebView observes media -> headers/cookies handed off -> Media3 external playback`

Batch 1 showed `Quality: stream`; this did not prove only one quality existed. Batch 2 now inspects Media3 adaptive tracks and enables quality choice when the selected stream exposes them.

Do not analyze HH video imagery. User performs playback verification.

## 6. curl_cffi / impersonation decision

Adding `curl_cffi` does not inherently break the project's DRM boundary. It is a networking dependency which can make yt-dlp requests resemble supported browsers at the TLS/request layer.

The concern is HOW it would be used. Deliberately forcing impersonation to get around an anti-bot/access-control decision could conflict with the project's terms/access-control boundary. Separately, `curl_cffi` includes native components, so Android/Chaquopy packaging compatibility would have to be verified.

Therefore `curl_cffi` is not being added at this stage. Browser-assisted resolution has already demonstrated a viable path without it.

## 7. Safe development test matrix

Use non-adult test material whenever ChatGPT needs to inspect playback behavior directly.

### Internet Archive (`archive.org`)

Use public-domain/open material for:

- direct yt-dlp resolution;
- normal player behavior;
- quality selection;
- seeking;
- timeline thumbnail extraction;
- verifying that unsupported legacy containers are no longer selected blindly.

### Bitmovin public HLS demo

Primary safe browser-assisted test:

`https://bitmovin.com/demos/hls-fmp4/`

Use for:

- JavaScript-created player behavior;
- HLS master and child playlist request order;
- browser-assisted candidate ranking;
- audio-only/video-only/full-stream distinctions;
- Media3 adaptive quality selection.

### Cloudflare Stream public examples

Use when useful to separate normal Cloudflare video CDN behavior from an unrelated Cloudflare anti-bot challenge. Do not treat it as an anti-bot-bypass test.

## 8. Current dependency baseline

From the uploaded source snapshot:

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Kotlin: 2.0.21
- compileSdk / targetSdk: 36
- minSdk: 24
- JDK: 17 in GitHub Actions
- Chaquopy: 17.0.0
- Python: 3.13
- yt-dlp: 2026.06.09
- Media3: 1.10.1
- ABI currently built: arm64-v8a

## 9. Current implementation status

### Batch 1 — built and phone-tested

Verified:

- Automatic browser-assisted fallback exists.
- Broader media discovery works.
- Vivaldi Share works.
- HentaiHaven external playback works for a manually chosen correct candidate.
- Bitmovin reproduces the multiple-HLS-candidate problem safely.
- Pornhub reveals a desired-looking blank candidate plus a playable ad candidate.

### Batch 2 — prepared, compilation/phone test pending

Files changed:

- `app/src/main/python/resolver.py`
- `app/src/main/java/com/example/vivaldiplayer/ResolvedMedia.kt`
- `app/src/main/java/com/example/vivaldiplayer/BrowserResolverActivity.kt`
- `app/src/main/java/com/example/vivaldiplayer/PlayerActivity.kt`
- `app/src/main/res/layout/activity_player.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-es/strings.xml`
- `PROJECT_STATE.md`
- `CHAT_BOOTSTRAP.md`

Batch 2 goals:

1. Prefer Media3-friendly direct-resolver containers.
2. Turn blank playback failures into copyable Media3 diagnostics.
3. Preserve browser media request order and rank likely complete streams first.
4. Demote obvious advertising candidates without hiding them.
5. Make adaptive browser-stream qualities visible/selectable.
6. Apply the project's 720 -> 1080 -> best-below-1080 policy to browser adaptive tracks while allowing lower adaptive tracks for buffering in Auto mode.
7. Continue EN/ES localization and heavy English source comments.

Local checks completed before handing Batch 2 to the user:

- `resolver.py` passes Python syntax compilation.
- Modified Android XML files parse as XML.
- All referenced string resources exist in both English and Spanish.
- Full Android/Kotlin compilation still requires GitHub Actions/Android SDK and is the next gate.

## 10. Batch 2 QA order after a green build

1. Install new APK by ADB if normal sideloading is intercepted.
2. Re-test Internet Archive direct resolution and record any automatic diagnostics.
3. Re-test Bitmovin.
   - Check whether the full HLS stream is now ranked Recommended / near the top.
   - Confirm external full video+audio playback.
   - Open Quality and record the available heights.
   - Confirm Auto prefers/caps at 720p when 720 exists.
4. Re-test Pornhub.
   - Choose the recommended page-associated candidate first.
   - If blank, copy the new Playback diagnostics text and send it to ChatGPT.
   - Confirm the obvious ad candidate is demoted in the list.
5. Re-test HentaiHaven to ensure the working external-playback path was not regressed.
   - Check candidate ranking.
   - Check available browser quality choices.
6. Do not optimize click count until correct-stream ranking/playability is better understood.

## 11. Planned work after correct-stream selection

Keep these explicitly on the backlog:

1. Reduce the excessive number of clicks/taps in browser-assisted resolution.
2. Handle pages containing multiple real videos plus ads/previews.
3. Add automatic primary-video selection with a manual candidate fallback.
4. Avoid making the user manually browse the entire embedded site just to reach the video when a safe technical shortcut is possible.
5. Add playback-speed controls.
6. Add app-level volume/mute controls.
7. Add a dedicated Return to existing Vivaldi tab/task action.
8. Finish player UI localization and polish.
9. Evaluate Brave only after the Vivaldi flow is reliable.

## 12. GitHub access status

Public repository:

`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

As of 2026-08-11, ChatGPT web access still returns a cache miss for the repository. The latest uploaded source ZIP, `VivaldiExternalPlayer-main_2.zip`, is therefore the current source of truth. Re-check GitHub after every new user message until direct access succeeds, then update this section.

## 13. Change log

### 2026-08-11 — Batch 1 results

- GitHub Actions build succeeded after fixing Android apostrophe escaping in English string resources.
- Development APK installation succeeded through ADB after Play Protect blocked the normal MIUI sideload flow.
- Verified Vivaldi Share handoff.
- Archive direct source opened PlayerActivity but rendered no video.
- Bitmovin produced 2 MP4 and 3 HLS candidates; only the oldest HLS candidate produced full video+audio.
- Pornhub produced a page MP4 which was blank in Media3 and an advertising MP4 which played.
- HentaiHaven produced a correct candidate which played successfully in external Media3.
- Added future requirements to reduce click count and handle multiple media items intelligently.

### 2026-08-11 — Batch 2 prepared

- Added Media3-friendly yt-dlp container preference.
- Added richer resolver source metadata.
- Added generic candidate ranking preserving first-seen HLS order.
- Added generic ad-host demotion without blocking alternatives.
- Added copyable playback diagnostics.
- Added adaptive browser quality discovery and selection.
- Updated English and Spanish player strings.
- Updated this operational memory and `CHAT_BOOTSTRAP.md`.
