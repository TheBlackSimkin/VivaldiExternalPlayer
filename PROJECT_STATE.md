# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA, failures, decisions, or priorities change.

## Working rules
- Conversation: English. User's Vivaldi/Windows UI is normally Spanish; use Spanish labels when useful. Android UI remains bilingual.
- Explain plainly; user is not an advanced developer. Source code should contain abundant English comments.
- Use connected GitHub tools directly whenever possible.
- PH and HH are real technical playback targets tested by the user. Assistant may work with technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local tab titles, but must not inspect/describe media content.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import private Vivaldi credentials.
- Conservative consent may auto-handle only clearly identified 18+/age and cookie prompts. Ambiguous/login/payment/region/DRM/challenge controls remain user-driven.
- Never ask user to send PH/HH title text.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest available below 1080p.

Do not regress: Vivaldi share; yt-dlp first then browser fallback; automatic best candidate first/manual chooser fallback; video+audio; adaptive and sibling quality switching; double-tap ±10s; seek thumbnails; rotation; bilingual UI; 80 stored/20 manual candidates; first-seen HLS/DASH ordering; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no media imagery inspection; one actual ExoPlayer playback session at a time.

Previously device-verified: Bitmovin, PH and HH core playback/audio/quality baseline. Cloudinary is skipped. Build #62 final-tab/title follow-up was already validated; do not repeat unless investigating a regression. Build #74 clean-loading baseline PASS.

## Selected feature bundle
Required together: 1 background Add/pre-resolution; 2 foreground-only playback; 3 conservative age/cookie automation; 4 clean browser-assisted loading; 5 tab readiness states; 6 persistent tabs; 20 polished switcher; 21 playback recovery; 23 adaptive icon; 24 release-signing infrastructure; 25 build info; 28 temporary-network retry; 29 next-tab preparation; 30 settings.

Feature 24 activation is deliberately deferred until a future stable/private-repo phase. Debug APKs remain the QA path. Signing keys must never be committed even when private.

## Persistent tabs
`VideoTabStore` uses local SharedPreferences with `QUEUED`, `RESOLVING`, `READY`, `NEEDS_ATTENTION`, `ERROR`. READY tabs reuse saved resolved JSON; positions/play intent are saved; stale RESOLVING resets to QUEUED after process death. No imported browser credentials/cookies are stored.

## Build #109 QA findings
Reported PASS/correct:
- install PASS;
- adaptive icon PASS;
- About/build info CORRECT;
- Settings PASS;
- no background playback, as intended;
- clean browser-assisted UX PASS;
- age/cookie prompts were not shown in that test, acceptable;
- local browser title CORRECT;
- tabs/buttons and listed quality options generally worked well; UI can be polished later.

Regressions found:
1. Background Add changed focus to External Player instead of reliably leaving Vivaldi in front.
2. Background share action was hard to identify/find.
3. A background-added browser-assisted tab was added but not actually pre-resolved; preparation finished only after opening External Player and selecting the tab.
4. HH showed the generic automatic-preparation error, but `Método asistido por navegador` worked, proving the background architecture stopped too early.
5. Adaptive quality menu showed correct available resolutions, but choosing another quality did not actually change the playing rendition.

Do not ask user to repeat #109 items already reported PASS unless a fix could plausibly regress them.

## Background share UX
User requested Spanish labels:
- `Añadir en External Player en segundo plano`
- `Añadir en External Player`

Current strings use those labels. Background share filter order is 100 vs normal 10. Android/Vivaldi owns final share-sheet ordering, so exact visual placement cannot be guaranteed.

## Build #124 fixes
### Real background preparation
`BackgroundShareActivity` is now the background ACTION_SEND entry point. It creates the tab, starts `BackgroundPreparationActivity` in an excluded-from-recents task, and removes the tiny share hand-off task.

`BackgroundPreparationActivity`:
- never creates ExoPlayer;
- tries yt-dlp first;
- if direct resolution succeeds -> READY;
- if direct resolution misses normally, continues automatically with an invisible WebView instead of stopping immediately;
- moves its task behind the browser so Vivaldi should stay foreground;
- observes technical WebView requests, VIDEO/SOURCE URLs, Performance API entries, and normal player configuration;
- mirrors the protected quality/candidate policy;
- may conservatively clear clear age/cookie prompts only;
- never imports Vivaldi credentials, inspects imagery, or bypasses protected access;
- if real interaction is needed or the bounded background attempt expires, tab -> NEEDS_ATTENTION for the normal visible browser resolver.

WorkManager remains for queued restart recovery/direct retry/next-tab preparation. If later device QA shows feature 29 still stops at the direct-only boundary, unify it with the new browser-capable preparation path.

### Adaptive quality switching
`AdaptiveQualityRuntime` is installed from the already-registered `ForegroundPlaybackGuardProvider`.

For browser adaptive HLS/DASH with multiple Media3 video tracks it:
- takes over only the adaptive quality-button path;
- applies an exact single-track `TrackSelectionOverride`;
- reinforces manual selection with exact min/max video dimensions;
- performs a tiny re-seek to discard buffered old-rendition chunks promptly;
- re-applies once if a live manifest refresh replaces the TrackGroup;
- restores the project 720 -> 1080 -> below-1080 policy for Auto.

Direct/yt-dlp and sibling-URL quality paths remain otherwise unchanged. Foreground BrowserResolver candidate ranking remains unchanged.

## Other implemented bundle pieces
- `ForegroundPlaybackGuardProvider`: pauses PlayerActivity on app background/stop; background preparation may continue but playback may not.
- `BrowserAssistEnhancerProvider`: clean `Opening video…` cover; reveals WebView when needed; never solves CAPTCHA/challenge.
- `PendingBrowserTabBridgeProvider`: browser completion updates the same pending tab.
- `PlayerRecoveryProvider`: max two automatic retries only for ordinary network timeout/connection and HTTP 429/5xx; no access bypass.
- Settings: age prompt toggle, cookie toggle, network retry, next-tab preparation, Clear saved tabs.
- About: version 0.2.0, versionCode 2, Git commit, Actions run.
- Adaptive launcher icon implemented.
- User said tab UI/buttons work well but may be improved later; cosmetic redesign is below current regression fixes.

## Signing
Optional release signing workflow is prepared for the four VEP secrets, but activation is deliberately deferred. Future permanent key stays outside Git history. Expect one uninstall/reinstall when eventually moving from debug signing to permanent release signing.

## CI / QA baseline
- #109 PASS compile; device QA found the regressions above.
- **#124 PASS** on app-code commit `ab251902cf15cb9615abe242ca954bee64ddec2c` with the background-preparation and adaptive-quality fixes.
- Build #124 debug APK SHA-256: `04d3cb806bfca5b10db19bfab85e51cb0053e73f5c7268bcb4746454b401624a`.

## Current priority
1. Focused device QA on build #124 only for the regressions fixed: share labels/order, staying in Vivaldi, real background preparation especially HH, no background playback, and actual manual adaptive quality switching.
2. Fix any #124 regression before resuming the remaining unreported parts of the original consolidated QA.
3. Then continue persistence/background-stop/recovery/close-tab gates not yet reported.
4. Signing activation later; Brave support later.

## QA format
Whenever asking user to test, provide EXACTLY:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
