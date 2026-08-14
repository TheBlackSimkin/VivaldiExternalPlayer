# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` completely before substantive work. Keep both state files current.

## Communication / workflow
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer.
- Use connected GitHub tools directly whenever possible.
- Source should contain abundant English comments.
- Never restart from scratch or ask user to repeat already completed QA without a regression reason.

## Safety boundary
PH and HH are real technical playback targets tested by the user. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed. Do not inspect/describe media content or user thumbnail imagery. Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Never ask for PH/HH title text or thumbnails.

Conservative automation: only clearly identified age/18+ and cookie prompts may be auto-handled.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise best below 1080p. Protect Vivaldi share; yt-dlp first/browser fallback; automatic best/manual fallback; video+audio; adaptive/sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate limits/order/ranking protections; no imagery-based resolver decisions; one ExoPlayer playback session.

Previously verified: Bitmovin/PH/HH core baseline, build #62 follow-up, build #74 clean loading. Do not repeat old PASS items without regression reason.

## Signing
Original coordinated bundle features 1,2,3,4,5,6,20,21,23,24,25,28,29,30 remain active. Permanent release signing is deliberately deferred; debug APK QA continues. Never commit the permanent key.

## #109 findings / #124 fixes still awaiting device validation
#109 PASS/correct: install, icon, About/build info, Settings, no background playback, clean browser UX, local browser title. Age/cookie prompts not shown.

#109 regressions: BG Add stole foreground; share action unclear; browser-assisted tabs were not truly pre-resolved; HH errored although visible browser method worked; adaptive quality menu changed but rendition did not.

#124 compiled fixes: real background yt-dlp -> invisible-WebView preparation with no playback, and exact adaptive Media3 quality override/reapply. User postponed device testing while requesting more features.

## #143 / icon features
Implemented local random-frame persistent thumbnails, short share labels `ExternalPlayer` / `BG - External Player`, dark Material UI, redesigned home/tabs/Settings/About. Post-#143 icon commit `3b0f173d310772278a26cb17d1a11ec7309d9e79` uses white E + hollow purple diamond; no triangle.

## User-approved dashboard/reliability batch — build #162
User explicitly approved before next test:
1. real Tab Dashboard;
2. unified BG/preload/retry/restart preparation;
3. verified + persistent quality state.

### Dashboard
MainActivity is now the canonical tab dashboard. Persistent thumbnail cards show title, preparation state, position, Manual quality, Actual quality and direct actions. Actions include Play/Continue, Prepare/Browser/Retry, move up/down, close and sideways swipe-to-close. Player floating Tabs button saves state and returns to this dashboard; the old independent popup switcher is removed.

### Unified preparation
`UnifiedPreparationCoordinator` routes BG Add, next-tab preload, dashboard retry/prepare and queued restart continuation through the same browser-capable `BackgroundPreparationActivity` whenever a foreground Activity is available. WorkManager is only direct/network/restart fallback when foreground Activity is unavailable. One hidden prep WebView at a time, no extra ExoPlayer, ranking unchanged, stale Worker handoff guarded. Automatic preload only takes QUEUED tabs.

`ForegroundPlaybackGuardProvider` uses a 200ms delayed check so the internal hidden-preparer task may move behind an already-resumed PlayerActivity without unnecessary pause. Home/lock/Vivaldi/dashboard/settings still leave PlayerActivity non-resumed and should pause; device QA required.

### Quality proof/persistence
`VideoTabStore` persists separate `manualQualityHeight` and `actualQualityHeight` plus tab ordering.

Adaptive HLS/DASH manual preference restores per tab; Actual height is written only from Media3 `onVideoSizeChanged`, not from the menu tap. UI can show requested vs actual/switching/verified. Concrete yt-dlp/sibling switches infer Manual from numeric `requested_quality` and Actual from the selected concrete source. Existing PlayerActivity source-switch logic was not rewritten.

### CI
Build #160 failed only at resource merge because five Spanish home strings were duplicated. Duplicates were removed.

**Build #162 PASS** on app-code head `5bd11518e44a8e146fcb9456481b562979152e0c`.
- Run `31764372852`
- Artifact `9205761745`
- APK SHA-256 `fd46df0019782db4bff34bde5959f4bf7ba4049e78be69f9808b4bd93dac13e9`

Use build #162 for the next device QA. State-only commits afterward do not require a newer APK.

## Next QA priority
Focused build #162 pass should verify:
- `ExternalPlayer` / `BG - External Player` share flow and Vivaldi staying foreground;
- HH background browser-capable pre-resolution and no background playback;
- next-tab preload uses same engine and does not interrupt active playback;
- dashboard cards/actions/reorder/swipe/thumbnails;
- manual quality actually switches, dashboard Manual/Actual matches reality, and manual/Auto persists after reopen/restart;
- Home + lock still stop playback;
- then remaining persistence/recovery/final-tab gates.

Do not ask for PH/HH titles or thumbnails.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
