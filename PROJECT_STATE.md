# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA, failures, decisions, or priorities change.

## Working rules
- Conversation: English. User's Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Source should contain abundant English comments.
- Use connected GitHub tools directly whenever possible.
- PH and HH are real technical playback targets tested by the user. Assistant may work with technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local tab titles, but must not inspect/describe media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Never ask user for PH/HH title text or thumbnails.
- Conservative consent may auto-handle only clearly identified 18+/age and cookie prompts.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p.

Do not regress: Vivaldi share; yt-dlp first/browser fallback; automatic best candidate/manual fallback; video+audio; adaptive and sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; 80 stored/20 manual candidate limits; first-seen HLS/DASH ordering; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no media imagery inspection for resolver/ranking; one actual ExoPlayer playback session.

Previously verified: Bitmovin/PH/HH core playback baseline; build #62 final-tab/title follow-up; build #74 clean-loading. Do not repeat old PASS items without regression reason.

## Coordinated feature bundle / signing
Features 1,2,3,4,5,6,20,21,23,24,25,28,29,30 remain the coordinated iteration. Permanent release-signing activation is deliberately deferred; debug APK QA continues. Never commit the permanent release key.

## Known #109 device findings
PASS/correct: install, icon, About/build info, Settings, no background playback, clean browser UX, local browser title. Age/cookie prompts not shown.

Regressions found:
1. Background Add stole foreground.
2. Background share action unclear.
3. Browser-assisted background tabs did not truly pre-resolve.
4. HH showed automatic-preparation error although visible browser method worked.
5. Quality menu listed correct adaptive resolutions but manual choice did not actually change rendition.

## #124 regression fixes still awaiting device validation
- `BackgroundShareActivity` + `BackgroundPreparationActivity`: yt-dlp then invisible-WebView browser-capable preparation, separate excluded task, no ExoPlayer/background playback, success READY, genuine interaction/timeout NEEDS_ATTENTION, no protected-access bypass.
- `AdaptiveQualityRuntime`: exact Media3 single-track override + exact size constraints + tiny reseek + TrackGroup refresh reapply; Auto returns to 720 -> 1080 -> best below 1080.

User requested further development before testing, so these runtime fixes remain unverified on-device.

## #143 feature/UI batch
Implemented before next test:
- app-private persistent random-frame thumbnail per tab; active tabs reuse PlayerActivity FrameExtractor; READY tabs can extract without ExoPlayer/playback; local only; close/clear/orphan cleanup;
- share labels exactly `ExternalPlayer` and `BG - External Player`;
- dark Material UI refresh across home/tabs/Settings/About;
- icon changed to bold white E. Post-#143 commit `3b0f173d310772278a26cb17d1a11ec7309d9e79` replaced the red triangle with a hollow purple diamond; no triangle remains.

Build #143 PASS, APK SHA-256 `c376301716ecc68b28412c4197a3e7e69c356514a1df0a974fc32b90b8fe13ea`.

## User-approved reliability/dashboard batch — implemented for build #162
User explicitly approved developing together before the next test:
1. first-class Tab Dashboard;
2. unified BG/preload/retry/restart preparation engine;
3. verified + persistent quality state.

### First-class Tab Dashboard
`MainActivity` is now the canonical tab dashboard instead of a single saved-tab button / separate popup switcher.

Each persistent card can show:
- local thumbnail;
- title;
- QUEUED / PREPARING / READY / BROWSER STEP / ERROR;
- saved playback position;
- persisted Manual quality when selected;
- Media3-observed Actual quality when known;
- Play/Continue;
- Prepare now / Browser step / Retry;
- move up/down with persistent ordering;
- close button;
- sideways swipe-to-close.

The floating Tabs button in PlayerActivity now saves the session and opens this dashboard. This avoids two independent tab UIs drifting apart. Settings/About/manual-URL controls remain on the dashboard screen.

### Unified preparation engine
New `UnifiedPreparationCoordinator` is the front door for user-facing preparation while a foreground Activity is available.

BG Add, next-tab preload, dashboard retry/prepare, and queued restart continuation all converge on the same `BackgroundPreparationActivity` browser-capable engine. WorkManager remains a direct/network/restart fallback only when no usable foreground Activity exists; ordinary direct-resolver misses return to QUEUED and continue through the unified browser stage on foreground resume.

Important protections:
- one hidden preparation WebView at a time;
- no second ExoPlayer;
- candidate ranking unchanged;
- duplicate Worker work cancelled before unified preparation;
- stale cancelled-Worker handoff cannot overwrite an already-running browser stage;
- automatic next-tab preload only starts genuinely QUEUED tabs, not ERROR/NEEDS_ATTENTION tabs.

`ForegroundPlaybackGuardProvider` now delays privacy pause by 200ms so an internal hidden-preparation Activity may move behind the SAME resumed PlayerActivity without unnecessarily pausing playback. Home/lock/Vivaldi/dashboard/settings leave PlayerActivity non-resumed and therefore still pause. Device QA is required for this nuance.

### Verified + persistent quality
`VideoTabStore` now persists separately:
- `manualQualityHeight` — what the user requested;
- `actualQualityHeight` — what playback actually reports.

Adaptive browser HLS/DASH:
- manual preference restores when the tab reopens;
- exact override logic remains;
- actual height is written only from Media3 `onVideoSizeChanged(VideoSize)`, so the UI does not claim success merely because a menu item was tapped;
- player button can show requested vs actual / switching / verified state.

Concrete yt-dlp and sibling-URL switches reuse the existing resolved payload: numeric `requested_quality` persists Manual mode and the concrete source height persists Actual quality. PlayerActivity's validated switch logic itself was not rewritten.

### Relevant commits
- `696da2850d3ef87fd16d8a8151349ce10804287e` persistent quality/order fields.
- `b2153e1beb12e2e0f6109a8d787a161180b2055c` Media3 actual-quality verification.
- `76f91cec97315d497244392f94e9b3d4591fb920` + `c99e05eaab0ce1077447ab1458fdaaab6b71c661` unified preparation coordinator.
- `5e056e9b30cee104bbf26a2e9e17ef87fe249aa5` BG share unified routing.
- `b12acf346480707fe09c5a4d483720f545a80157` dashboard layout.
- `9b485935e0159ec4bf8f7926fbeb43d0bb422b86` dashboard behavior + fallback Worker consolidation.
- `aeae98d2a44ce8f99930d7e14b1354981d2f83f6` foreground-privacy preload handoff.
- `31395a9648ddf861f0ea5c1632654322ca81b7af` concrete-source manual-quality persistence.
- `5bd11518e44a8e146fcb9456481b562979152e0c` Spanish duplicate-resource fix; designated app-code QA head.

## CI / designated QA build
- Build #160 failed at Android resource merge only because five Spanish home strings existed in both `values-es/next_build_strings.xml` and `values-es/ui_refresh_strings.xml`. No Kotlin/app architecture compiled in that run.
- Duplicate strings were removed from `next_build_strings.xml`.
- **Build #162 PASS** on app-code head `5bd11518e44a8e146fcb9456481b562979152e0c`.
- Run ID `31764372852`.
- Debug artifact ID `9205761745`.
- GitHub artifact ZIP digest `sha256:76e7e4551b9f86aa2870d1c3c8b43aef71599c46ad074645107a4f409cbb789f`.
- Extracted APK SHA-256 `fd46df0019782db4bff34bde5959f4bf7ba4049e78be69f9808b4bd93dac13e9`.

State/documentation-only commits after #162 do not require another APK.

## Current priority
1. Device QA build #162 as the single combined test build.
2. Verify unresolved #124 regression fixes plus new dashboard/unified-preparation/quality-proof behavior.
3. Especially verify: BG share stays in Vivaldi; HH browser-capable pre-resolution; next-tab preload does not interrupt active playback; no background audio; manual quality actually changes and dashboard shows Manual/Actual truthfully; manual/Auto choice persists; Home and lock still pause; dashboard card actions/reorder/swipe/thumbnail behavior.
4. Fix device regressions before adding subtitle/audio-track selection or Brave support.
5. Signing activation later.

## QA format
Whenever asking user to test, provide EXACTLY:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
