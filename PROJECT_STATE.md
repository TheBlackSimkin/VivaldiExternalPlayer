# Vivaldi External Player — Project State

> Operational source of truth. Keep this file current whenever requirements, tests, architecture, failures, or priorities change.

## Environment and communication

- Android phone; Vivaldi Mobile Browser is the Phase 1 browser.
- Conversation with ChatGPT: English.
- Windows/Vivaldi UI: normally Spanish; use Spanish UI labels when relevant.
- Android app UI must remain bilingual English/Spanish.
- GitHub `main` is the source of truth.
- Do GitHub work directly when possible; do not make the user manually edit/code/upload files if the connected GitHub tool can do it.

## Safety/content boundary

PH and HH are real-world technical playback targets. The user performs all media-content testing.

Allowed technical work: URLs, manifests, codecs, quality, request metadata, candidate ranking, browser/network state, playback errors/status, and local tab-title handling.

Do not:
- inspect/analyze/classify/describe PH/HH video content;
- bypass DRM or obtain keys;
- bypass subscriptions/paywalls, authentication, or regional controls;
- deliberately automate anti-bot challenges;
- import Vivaldi passwords/private credentials.

Video/page titles may be captured locally for tab labels, but ChatGPT must not request or inspect PH/HH title text.

### Automatic consent policy

The user is over 18 and explicitly wants the app to automatically accept **clearly identified**:
- 18+ / age-confirmation prompts;
- cookie-consent prompts.

This automation must be conservative. It must **not** auto-click:
- ambiguous buttons;
- sign-in/account prompts;
- paywall/subscription/payment prompts;
- regional-access controls;
- DRM-related controls;
- anti-bot/challenge/CAPTCHA controls;
- unrelated page actions.

If classification is uncertain, leave the prompt for the user. No media imagery/content analysis is needed for this feature.

## Verified Batch 4 playback baseline

Quality policy:
1. Exact 720p when available.
2. Otherwise 1080p.
3. Otherwise highest available quality below 1080p.

Must not regress:
- Share from Vivaldi.
- yt-dlp first, then automatic browser-assisted fallback.
- Automatic best candidate first; manual chooser fallback only.
- Video + audio playback.
- Adaptive and sibling-URL quality switching.
- Double tap left/right = -10/+10 seconds.
- Timeline thumbnail preview where technically supported.
- Portrait/landscape.
- English + Spanish UI.

Verified:
- GitHub Actions clean build #48: PASS.
- Bitmovin: automatic YES, video YES, audio YES.
- PH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
- HH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
- Cloudinary is explicitly skipped and is not a QA gate.

Protect Batch 4 resolver behavior:
- automatic best-candidate first attempt after discovery settles;
- manual chooser fallback only;
- up to 80 stored candidates, strongest 20 shown manually;
- meaningful first-seen HLS/DASH ordering;
- no generic `playlist` bonus;
- soft demotion of obvious audio-only/video-only child paths;
- page-config family IDs for sibling quality URLs;
- no media imagery inspection.

## Playback lifecycle / privacy behavior

Playback is foreground-only.

Required behavior:
- Video and audio must stop immediately when External Player is no longer the foreground app, including when the user switches back to Vivaldi or another app.
- Video and audio must stop when the phone is locked / screen is turned off.
- Background tab preparation/resolution may continue when Android allows it, but **playback itself must not continue in background**.
- Do not use background audio playback, Picture-in-Picture autoplay, media-session continuation, or a foreground playback service unless this requirement is explicitly changed later.
- Preserve the tab's playback position when playback is stopped because the app is backgrounded/locked.
- Automatic background/lock pause should be distinguishable from a deliberate user pause so the app can preserve sensible resume behavior when returning to the same tab; exact auto-resume policy can be decided during implementation, but no media may play while backgrounded or locked.

## Multi-video tabs

Required behavior:
- Every shared/moved video becomes an independent app tab.
- Multiple tabs may remain open.
- User can select/switch and close individual tabs.
- Closing one must not close the others.
- Preserve each tab's playback position.
- Preserve selected quality when practical.
- When active tab closes, select another remaining tab or a clean empty/home state.
- Full process-restart persistence remains undecided.

Current architecture:
- `VideoTabStore`: process-local session store for resolved-media JSON, title, position, and play/pause state.
- `TabbedPlayerApplication`: tab coordinator above the validated resolver/player flow.
- One active ExoPlayer at a time.
- Tab switching reconstructs playback from stored resolved JSON and restores position/play state.
- `ResolvedMedia.toJson()` preserves quality-switched sources when practical.

### First device QA — 2026-08-13

Reported:
- Tabs 1→2: YES.
- Switch tabs: YES.
- Position restored: YES.
- Quality preserved: YES.
- Close one only: YES/OK.
- Close active: OK.
- Close final cleanly: PARTIAL FAILURE — old resolver screen underneath became visible.
- Auto resolver: YES.
- Video: YES.
- Audio: YES.
- Quality options: YES.
- Quality switching: YES.
- Double-tap seek: YES.
- Browser-assisted tab labels: GENERIC.
- Resolver/loading UI still visible instead of clean animated loading UI.

Interpretation: core tab architecture works and Batch 4 playback did not regress.

### Post-QA fix

Commit `8be38f33c1a1f225ef555133229669f7e9008b1e`:
- final tab now clears back to `MainActivity` instead of revealing/retriggering the resolver beneath it;
- browser-assisted tab title now uses local WebView page title captured on-device;
- Batch 4 candidate ranking/selection unchanged.

GitHub Actions build #62: PASS for debug APK build and artifact upload.

## Tab titles

Preferred sources:
1. yt-dlp/resolver title;
2. browser-assisted page metadata/title;
3. fallback `Video`.

Direct titles work. Browser-assisted local title capture is implemented in the tab coordinator. Never ask the user to report PH/HH title text; QA should report only CORRECT/GENERIC/OTHER.

## Loading / buffering UX

Next major UX feature.

Normal UI should show:
- `Opening video…` + spinner while resolving/opening;
- `Buffering…` only while Media3 is genuinely buffering;
- indicator disappears when playback is ready;
- no WebView/candidate/manifest/debug details flashing in normal use;
- technical diagnostics only through explicit diagnostics/error paths.

Remove resolver flicker without changing the working Batch 4 candidate-selection logic.

## Background add / “segundo plano”

Required workflow:
- Add separate share action such as `Add to External Player` / `Añadir a External Player`.
- Vivaldi remains in front.
- The shared URL immediately creates a new video tab.
- The new tab starts resolving/preparing immediately; it must not sit dormant until selected.
- By the time the user switches to External Player, previously sent tabs should ideally already be resolved and ready.

Proposed tab preparation states:
- `QUEUED`;
- `RESOLVING`;
- `READY`;
- `NEEDS_ATTENTION` or `ERROR`.

Architecture rules:
- Direct/yt-dlp resolution should run immediately using Android-supported background work.
- If direct resolution succeeds, store completed `ResolvedMedia` JSON; selecting a READY tab must not resolve again.
- Background preparation is pre-resolution, not concurrent playback: keep one active ExoPlayer.
- If browser-assisted WebView resolution is genuinely required and cannot be completed robustly in background, mark the tab as needing browser-assisted completion; do not claim it is READY.
- When foreground completion is necessary, use the clean `Opening video…` UX.
- Automatic clear age/cookie consent handling should also help browser-assisted completion, subject to the strict consent policy above.
- Background preparation never implies background playback; foreground-only playback rules above always win.

## Current architecture summary

### MainActivity
- ACTION_SEND / text/plain entry.
- Extracts HTTP(S) URL from share text.
- Manual URL paste for debugging.
- yt-dlp first; browser-assisted fallback after direct failure.

### resolver.py
- yt-dlp via Chaquopy.
- Does not download media files.
- Rejects media marked DRM.
- Uses the project quality policy.

### BrowserResolverActivity
Observes normal technical state:
- WebView requests;
- Service Worker requests;
- page `<video>` / `<source>` elements;
- Performance API resource URLs;
- technical player configuration when exposed.

Future narrow age/cookie consent automation belongs here (or a dedicated helper called from here) and must remain separate from candidate ranking logic.

### PlayerActivity
- Media3 ExoPlayer.
- Progressive/HLS/DASH.
- Merged separate video/audio support.
- Adaptive track selection and sibling-URL quality switching.
- Seek preview.
- Playback diagnostics.
- Must obey foreground-only playback lifecycle: no video/audio while app is backgrounded or phone is locked.

### VideoTabStore / TabbedPlayerApplication
- Process-local independent tabs.
- One active player at a time.
- Per-tab position/play state/selected resolved source.
- Tab switcher and per-tab close.
- Local browser page-title capture.

## QA format

Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, and RESULT;
2. one separate short code block containing only the compact answer format.

Never ask for PH/HH title text. Cloudinary is not required.

## Current priority

1. Device-QA build #62 final-tab cleanup + browser-assisted titles.
2. Implement transparent `Opening video…` / `Buffering…` UX and hide resolver flicker.
3. Implement conservative automatic clear 18+ age-confirmation and cookie-consent handling.
4. Implement background `Add to External Player` / `Añadir a External Player` with immediate pre-resolution and per-tab preparation states.
5. Enforce foreground-only playback when app is backgrounded or phone is locked.
6. Polished original adaptive launcher icon.
7. Playback speed control.
8. App-level volume/mute.
9. Return to existing Vivaldi task/tab.
10. Persistent APK signing for GitHub Actions.
11. Decide full process-restart tab persistence separately.
12. Brave support later.
