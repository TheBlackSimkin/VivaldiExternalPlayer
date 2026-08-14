# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` completely before substantive work. Keep both state files current.

## Communication / workflow
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer.
- Use connected GitHub tools directly whenever possible.
- Source code should contain abundant English comments.
- Never restart from scratch or ask user to repeat already completed QA without a regression reason.

## Safety boundary
PH and HH are real technical playback targets tested by the user. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed. Do not inspect/describe media content or user tab-thumbnail imagery. Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Never ask user to send PH/HH title text or thumbnails.

Conservative automation: only clearly identified 18+/age and cookie prompts may be auto-handled. Ambiguous/login/payment/region/DRM/challenge controls remain user-driven.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise best below 1080p.

Protect Vivaldi share; yt-dlp first/browser fallback; automatic best candidate/manual fallback; video+audio; adaptive and sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate limits/order/ranking protections; no media imagery inspection for resolver decisions; one ExoPlayer playback session.

Bitmovin/PH/HH core playback baseline was previously device-verified. Cloudinary is not a gate. Build #62 follow-up was already validated. Build #74 clean-loading baseline PASS.

## Selected bundle / signing
Features 1,2,3,4,5,6,20,21,23,24,25,28,29,30 remain the coordinated iteration. Feature 24 signing activation is deliberately deferred; debug APK QA continues. Never commit the permanent release key.

## #109 QA already known
PASS/correct: install, icon, About/build info, Settings, no background playback, clean browser UX, local browser title. Age/cookie prompts not shown. Do not repeat those without regression reason.

Found regressions: background Add stole foreground; share action unclear; browser-assisted tabs did not truly pre-resolve; HH errored although visible browser method worked; adaptive quality list was correct but manual selection did not actually switch.

## #124 fixes awaiting device validation
- `BackgroundShareActivity` + `BackgroundPreparationActivity`: real background yt-dlp -> invisible WebView prep, separate task, no playback, success READY, genuine interaction/timeout NEEDS_ATTENTION, no protected-access bypass.
- `AdaptiveQualityRuntime`: exact Media3 track override + exact size constraints + tiny reseek + one TrackGroup-refresh reapply; Auto restores 720 -> 1080 -> best below 1080.

User requested more features before testing #124, so those fixes remain unverified on-device.

## Additional features implemented before next test
User requested random-frame thumbnails per tab, share entries `ExternalPlayer` and `BG - External Player`, icon V -> E, and a more attractive UI.

Implemented:
- app-private JPEG thumbnail cache keyed by tab ID;
- stable pseudo-random frame chosen from tab ID;
- active tabs reuse PlayerActivity FrameExtractor;
- READY tabs can extract a frame from already-resolved media without creating ExoPlayer/playback;
- warm-up fills missing thumbnails on normal app foreground; failures remain best-effort;
- close/clear/orphan cleanup for cached thumbnails;
- normal share label `ExternalPlayer`, background `BG - External Player` in English and Spanish;
- dark palette/material theme;
- redesigned home screen;
- thumbnail-card tab switcher with active accent, title/state/position/quality and close button;
- refreshed Settings and About screens;
- Spanish refresh strings.

### Latest icon change
Build #143 used a white E + red play triangle. User then requested a non-triangle purple geometric accent. App-code commit `3b0f173d310772278a26cb17d1a11ec7309d9e79` now uses a bold white E + hollow purple diamond on the dark launcher background. No triangle remains.

## Current architecture still active
Persistent `VideoTabStore` states QUEUED/RESOLVING/READY/NEEDS_ATTENTION/ERROR; foreground-only playback guard; clean browser enhancer; same-tab browser bridge; bounded network recovery; local Settings/About; optional release signing infrastructure deferred.

## CI / next QA build
- #124 PASS with background/quality regression fixes.
- #136 PASS for main UI/tab cards + active-tab thumbnail capture.
- #142 PASS for finalized READY-tab thumbnail engine.
- #143 PASS on app-code commit `2af936c6f58e919c303597c19c8513185277b72e`.
- #143 artifact ID `9203587518`.
- Extracted #143 APK SHA-256 `c376301716ecc68b28412c4197a3e7e69c356514a1df0a974fc32b90b8fe13ea`.
- New icon-only app-code commit after #143: `3b0f173d310772278a26cb17d1a11ec7309d9e79`. Use the next successful CI APK rather than #143 for icon QA once available.

## Recommended next development direction
Recommendation only, not yet user-approved:
1. make tabs a first-class dashboard/library with thumbnail grid/list, reordering, swipe/quick close, clearer preparation states and direct retry/continue actions;
2. unify BG Add and feature-29 next-tab preparation behind the same browser-capable background engine;
3. show the actual Media3-selected quality after manual switches and persist manual quality per tab;
4. add subtitle/audio-track selection where technically available;
5. then Brave/other-browser support.

## Current priority
Finish CI for the purple-diamond icon commit. Next focused QA should cover unresolved #124 share/background-preload/quality-switch gates + #143 thumbnail/UI behavior + the new E/purple-diamond icon. Then continue remaining persistence/background-stop/recovery/tab-close gates not yet reported.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
