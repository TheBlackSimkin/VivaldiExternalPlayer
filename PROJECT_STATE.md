# Vivaldi External Player — Project State

> Operational memory/source of truth for temporary chats. Update this file whenever requirements, tests, architecture, failures, or next steps materially change.

## 1. Purpose and environment

Vivaldi External Player is a personal Android application which receives a normal webpage URL, discovers an accessible non-DRM media stream when possible, and plays it through AndroidX Media3 / ExoPlayer.

Primary environment:
- Android phone for playback testing.
- Vivaldi Mobile Browser is the Phase 1 browser.
- Windows/Vivaldi UI is normally Spanish.
- Conversation with ChatGPT stays in English.
- The app UI must remain bilingual English/Spanish.

Primary real-world playback targets are PH and HH. The user performs all media-content testing. ChatGPT may analyze only technical playback information such as URLs, manifests, containers, codecs, resolutions, request metadata, candidate ranking, and playback status.

## 2. Boundaries

Do not:
- inspect, classify, summarize, or describe adult video content;
- bypass DRM or obtain DRM keys;
- bypass subscriptions/paywalls, authentication, or regional restrictions;
- deliberately automate anti-bot challenges;
- import Vivaldi passwords/private credentials.

The browser-assisted resolver may observe normal technical state and requests made by its own WebView. Video/page titles may be captured locally by the Android app for tab labels, but ChatGPT must not request, inspect, or analyze PH/HH title text.

## 3. Playback baseline — Batch 4

Quality policy:
1. Exact 720p when available.
2. Otherwise 1080p.
3. Otherwise highest available quality below 1080p.

Existing features which must not regress:
- Share from Vivaldi.
- yt-dlp first, then automatic browser-assisted fallback.
- Automatic best candidate first; manual chooser fallback only.
- Video + audio playback.
- Adaptive and sibling-URL quality switching.
- Double tap left/right = -10/+10 seconds.
- Timeline thumbnail preview where technically supported.
- Portrait/landscape.
- English + Spanish UI.

Verified Batch 4 QA:
- GitHub Actions clean build #48: PASS.
- Bitmovin: automatic YES, video YES, audio YES.
- PH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
- HH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
- Cloudinary testing is explicitly skipped and is not a required QA gate.

Batch 4 resolver logic to protect from regression:
- automatic best-candidate first attempt after discovery settles;
- manual chooser fallback only;
- up to 80 stored candidates, strongest 20 shown manually;
- first-seen HLS/DASH ordering weight;
- no generic `playlist` bonus;
- soft demotion of obvious audio-only/video-only child paths;
- page-config family IDs for sibling quality URLs;
- no media imagery inspection.

## 4. Multi-video tabs

Required behavior:
- Every shared/moved video becomes an independent app video tab.
- Multiple tabs may remain open.
- User can select/switch and close individual tabs.
- Closing one tab must not close others.
- Per-tab playback position must be preserved.
- Preserve selected quality when practical.
- When active tab closes, select another remaining tab or clean empty/home state.
- Full process-restart persistence remains undecided; do not assume either behavior.

### Current architecture

`VideoTabStore`:
- process-local session store;
- stores resolved-media JSON, title, position, and play/pause state.

`TabbedPlayerApplication`:
- session layer above the validated resolver/player flow;
- one active ExoPlayer at a time;
- tab switch reconstructs playback from stored resolved JSON and restores position/play state;
- tab UI is attached above `PlayerActivity` to minimize Batch 4 regression risk;
- current compatibility layer reads `PlayerActivity.currentResolved` reflectively when saving a tab so selected quality can be preserved.

`ResolvedMedia.toJson()`:
- serializes the current resolved media back into the tab session, including quality-switched browser sibling sources.

### First device QA — 2026-08-13

User reported:
- Tabs 1→2: YES.
- Switch tabs: YES.
- Position restored: YES.
- Quality preserved: YES.
- Close one only: YES / OK.
- Close active: OK.
- Close final cleanly: PARTIAL FAILURE — final tab closed, but old resolver screen underneath became visible and looked like it was trying to open the tab again.
- Automatic resolver: YES.
- Video: YES.
- Audio: YES.
- Quality options: YES.
- Quality switching: YES.
- Double-tap seek: YES.
- Tab labels: GENERIC in browser-assisted flow.
- Loading UX still exposes resolver instead of showing simple animated loading UI.

Interpretation:
- Core multi-tab/session architecture is successful.
- Batch 4 playback behavior did not regress in this QA.
- Two confirmed tab-hardening issues remain: final-tab back-stack cleanup and browser-assisted titles.

### Fix committed after QA

Commit `8be38f33c1a1f225ef555133229669f7e9008b1e`:
- final tab now clears back to `MainActivity` with a neutral non-share Intent instead of merely revealing the resolver Activity underneath;
- `TabbedPlayerApplication` captures the already-loaded browser-resolver WebView page title locally and uses it for the new browser-assisted tab;
- title remains on-device only and is not transmitted;
- Batch 4 resolver candidate ranking was not changed.

GitHub Actions build #62 was triggered for this fix and must pass before device QA is requested.

## 5. Tab titles

Preferred title sources:
1. yt-dlp/resolver title.
2. Browser-assisted page metadata/title.
3. Fallback `Video`.

Current implementation:
- direct/yt-dlp titles already work;
- browser-assisted title capture is now implemented in the tab coordinator using local WebView title metadata;
- do not ask the user to report PH/HH title strings to ChatGPT; QA should report only whether labels are correct/generic.

## 6. Loading / buffering UX

Still pending and now the next major UX feature.

Required normal UI:
- `Opening video…` + spinner while resolving/opening;
- `Buffering…` only while Media3 is actually buffering;
- indicator disappears when playback is ready;
- no WebView/candidate/manifest/debug details flashing in normal use;
- technical diagnostics only via explicit diagnostics/error path.

Current issue confirmed again in first tab QA: automatic resolver works, but the browser resolver/loading screen remains visible instead of the clean loading animation.

The resolver functionality must not be destabilized while removing this flicker.

## 7. Background-add / “segundo plano” requirement

New requested workflow from user on 2026-08-13:
- From Vivaldi, the user wants an option to send/add a link to External Player **in the background**, so Vivaldi stays in front and the newly sent video becomes another app tab without immediately taking over the screen.

Preferred architecture decision:
- Add a separate share target/action such as `Add to External Player` / `Añadir a External Player`.
- This action should queue the shared webpage URL as a pending video tab and immediately return/leave the user in Vivaldi.
- The pending tab should resolve when the user later opens/selects it.
- Do **not** rely on running the browser-assisted WebView resolver invisibly in the Android background; that is not a robust design and may require user interaction.
- Normal existing share-to-open behavior should remain available separately.

Implementation is pending. This should be integrated with the tab/session architecture after the current tab hardening and loading/title UX are stable.

## 8. Current architecture summary

### MainActivity
- ACTION_SEND / text/plain entry.
- Extracts HTTP(S) URL from share text.
- Manual URL paste for debugging.
- yt-dlp first.
- Automatically launches browser-assisted fallback after direct failure.

### resolver.py
- yt-dlp via Chaquopy.
- Does not download media files.
- Rejects media marked DRM by yt-dlp.
- Uses project quality policy.

### BrowserResolverActivity
Observes normal technical state:
- WebView requests;
- Service Worker requests;
- page `<video>` / `<source>` elements;
- Performance API resource URLs;
- player configuration such as `mediaDefinitions` when exposed.

### PlayerActivity
- Media3 ExoPlayer.
- Progressive/HLS/DASH.
- Merged separate video/audio support.
- Adaptive track selection and browser sibling-URL quality switching.
- Frame-extractor seek preview.
- Playback diagnostics.

### VideoTabStore / TabbedPlayerApplication
- process-local independent video tabs;
- one active player at a time;
- per-tab position/play state/selected resolved source;
- tab switcher and per-tab close;
- browser page-title capture now added locally.

## 9. QA format

Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, and RESULT;
2. one separate short code block containing only the compact answer format.

Never ask the user to send PH/HH title text. Cloudinary is not a required gate.

## 10. Communication / workflow

- English conversation.
- Spanish UI labels when relevant to Windows/Vivaldi.
- App remains bilingual English/Spanish.
- Explain plainly; user is not an advanced developer.
- Source code should contain abundant English comments.
- Do GitHub work directly whenever possible; do not make the user manually edit/code/upload files when the connected GitHub tool can do it.
- Keep this file and `CHAT_BOOTSTRAP.md` current.
- Before each response, verify direct GitHub repository access and state it briefly.
- GitHub `main` is always source of truth.

## 11. Current prioritized backlog

1. Verify build #62 and device-QA final-tab cleanup + browser-assisted tab titles.
2. Transparent `Opening video…` / `Buffering…` UX and removal of resolver flicker.
3. Background `Add to External Player` / `Añadir a External Player` share target that queues a pending tab while leaving Vivaldi in front.
4. Polished original Android adaptive launcher icon.
5. Playback speed control.
6. App-level volume/mute.
7. Return to existing Vivaldi task/tab.
8. Persistent APK signing for GitHub Actions.
9. Decide full process-restart tab persistence separately.
10. Brave support later.
