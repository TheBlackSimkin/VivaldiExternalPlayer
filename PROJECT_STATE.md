# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA, failures, decisions, or priorities change.

## Working rules
- Conversation: English. User's Vivaldi/Windows UI is normally Spanish; Android UI remains bilingual.
- Explain plainly; user is not an advanced developer. Source code should contain abundant English comments.
- Use connected GitHub tools directly whenever possible.
- PH and HH are real technical playback targets tested by the user. Assistant may work with technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local tab titles, but must not inspect/describe media content.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import private Vivaldi credentials.
- Conservative consent may auto-handle only clearly identified 18+/age and cookie prompts. Ambiguous/login/payment/region/DRM/challenge controls remain user-driven.
- Never ask user to send PH/HH title text or thumbnail imagery.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest available below 1080p.

Do not regress: Vivaldi share; yt-dlp first then browser fallback; automatic best candidate first/manual chooser fallback; video+audio; adaptive and sibling quality switching; double-tap ±10s; seek thumbnails; rotation; bilingual UI; 80 stored/20 manual candidates; first-seen HLS/DASH ordering; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no media imagery inspection for resolver/ranking; one actual ExoPlayer playback session at a time.

Previously device-verified: Bitmovin, PH and HH core playback/audio/quality baseline. Cloudinary is skipped. Build #62 final-tab/title follow-up was already validated; do not repeat unless investigating a regression. Build #74 clean-loading baseline PASS.

## Coordinated feature bundle
Features 1,2,3,4,5,6,20,21,23,24,25,28,29,30 remain the active coordinated iteration: background Add/pre-resolution; foreground-only playback; conservative consent; clean browser UX; tab readiness; persistent tabs; polished switcher; recovery; icon; signing infrastructure; build info; bounded retry; next-tab preparation; settings.

Feature 24 signing activation is deliberately deferred until a future stable/private-repo phase. Debug APKs remain the QA path. Signing keys must never be committed even when private.

## Build #109 device findings
PASS/correct and do not repeat without regression reason: install, icon, About/build info, Settings, no background playback, clean browser-assisted UX, local browser title. Age/cookie prompts were not shown. Tabs/buttons and displayed quality options were generally usable.

Regressions found:
1. Background Add changed focus to External Player instead of reliably leaving Vivaldi in front.
2. Background share action was hard to identify.
3. Browser-assisted background tabs were added but not actually pre-resolved until the tab was opened.
4. HH showed automatic-preparation error although normal browser-assisted method worked.
5. Adaptive quality menu listed correct resolutions but manual selection did not actually switch rendition.

## Build #124 regression fixes
`BackgroundShareActivity` + `BackgroundPreparationActivity` now provide real background browser-capable preparation: yt-dlp first, then invisible WebView technical discovery when needed, separate excluded task, no ExoPlayer/background playback, conservative clear age/cookie only, protected access never bypassed. Success -> READY; genuine interaction/timeout -> NEEDS_ATTENTION.

`AdaptiveQualityRuntime` fixes browser adaptive HLS/DASH manual switching using exact Media3 single-track override, exact video-size constraints, tiny re-seek and one re-apply after TrackGroup refresh. Auto restores 720 -> 1080 -> best-below-1080. Candidate ranking remains unchanged.

These #124 runtime fixes were compile-clean but had not yet received device QA because the user requested additional features before the next test.

## Additional pre-next-test feature request — implemented in build #143
User requested before the next device test:
1. thumbnail for each tab from a random video frame;
2. rename normal share target to `ExternalPlayer` and background target to `BG - External Player`;
3. change launcher mark from V to E;
4. improve UI to be more attractive.

### Tab thumbnails
Implemented local-only persistent tab thumbnails:
- `TabThumbnailCache`: app-private JPEG cache keyed by tab ID, roughly 480px wide, orphan pruning and close/clear cleanup;
- `TabThumbnailCapture`: stable pseudo-random frame per tab derived from tab ID;
- active tab path reuses PlayerActivity's existing Media3 `FrameExtractor`;
- READY tabs can use a short-lived FrameExtractor against already-resolved media without creating ExoPlayer or starting playback;
- `TabThumbnailWarmup` fills missing READY-tab thumbnails when ExternalPlayer returns to normal foreground screens;
- failed/expired thumbnail extraction is best-effort and never blocks playback;
- no thumbnail pixels leave the device and the assistant must not inspect them.

### Share names
English and Spanish share labels are intentionally short:
- normal: `ExternalPlayer`;
- background: `BG - External Player`.
Background intent-filter order remains 100 vs normal 10, but Android/Vivaldi controls final visual ordering.

### Icon
Adaptive/legacy foreground mark changed from V to a bold white E in build #143. After build #143 the user requested removing the red play triangle too. App-code commit `3b0f173d310772278a26cb17d1a11ec7309d9e79` replaces it with a hollow purple diamond accent over the E. The new icon contains no triangle.

### UI refresh
- new dark ExternalPlayer palette and Material theme surfaces;
- redesigned home screen with branded header, tab card, manual-link card, stronger hierarchy and compact Settings/About actions;
- tab switcher redesigned as rounded thumbnail cards with active accent stroke, title, state/position/quality subtitle and individual close button;
- floating Tabs button restyled;
- Settings and About restyled to match the dark card UI;
- Spanish home refresh strings added.

## Other architecture still active
- `VideoTabStore`: SharedPreferences persistent QUEUED/RESOLVING/READY/NEEDS_ATTENTION/ERROR tabs; saved position/play intent/resolved JSON.
- `ForegroundPlaybackGuardProvider`: playback stops when app is not foreground; background preparation may continue.
- `BrowserAssistEnhancerProvider`: clean Opening UI and conservative consent.
- `PendingBrowserTabBridgeProvider`: browser completion updates same pending tab.
- `PlayerRecoveryProvider`: maximum two ordinary network retries only; no access bypass.
- Settings: age/cookie toggles, retry, next-tab prep, Clear saved tabs.
- About: version 0.2.0, versionCode 2, Git commit, Actions run.

## CI / current QA artifact
- #109 compiled; device QA found regressions above.
- #124 PASS with background-preparation and adaptive-quality fixes.
- #136 PASS for refreshed home/tab UI plus active-tab thumbnail capture.
- #142 PASS for finalized READY-tab thumbnail engine.
- #143 PASS on app-code commit `2af936c6f58e919c303597c19c8513185277b72e`, including Settings/About refresh.
- Build #143 debug artifact ID: `9203587518`.
- Extracted build #143 APK SHA-256: `c376301716ecc68b28412c4197a3e7e69c356514a1df0a974fc32b90b8fe13ea`.
- Post-#143 icon-only app-code commit: `3b0f173d310772278a26cb17d1a11ec7309d9e79` (purple hollow diamond replacing red play triangle). Its next CI APK should supersede #143 for icon testing once compile/upload passes.

## Recommended next development direction
Before another large feature expansion, prioritize the tab/preparation experience:
1. turn the tab switcher into a first-class visual tab dashboard/library with thumbnail grid/list modes, reordering, swipe/quick close, clearer READY/PREPARING/BROWSER STEP states, and direct retry/continue actions;
2. unify every background/preload path behind the browser-capable preparation engine so feature 29 and explicit BG Add behave consistently;
3. make quality state observable: show actual Media3-selected rendition after a manual switch and preserve manual quality per tab;
4. then add player polish such as subtitle/audio-track selection where streams expose them;
5. only after this reliability/UI pass consider Brave integration and other browsers.

These are recommendations, not user-approved requirements yet.

## Current priority
1. Finish CI for the purple-diamond icon commit and use that newer APK for the next device QA.
2. Next device QA should still verify the unresolved #124 regressions: share flow, Vivaldi staying foreground, real background preparation especially HH, no background playback, and actual manual quality switching.
3. In the same focused pass verify the #143 thumbnail/UI features and new purple-diamond E icon.
4. Then resume remaining unreported persistence/background-stop/recovery/final-tab gates.
5. Signing activation later; Brave support later unless the user reprioritizes.

## QA format
Whenever asking user to test, provide EXACTLY:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
