# Temporary Chat Bootstrap — Vivaldi External Player

Public repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Treat GitHub `main` as the source of truth. Before changing code, read `PROJECT_STATE.md` completely. Keep both state files current whenever requirements, architecture, tests, failures, decisions, or priorities materially change.

## Communication
- Conversation: English.
- Windows/Vivaldi UI: normally Spanish; use Spanish labels when relevant.
- Android app UI: bilingual English/Spanish.
- Explain plainly; user is not an advanced developer.
- Do GitHub work directly whenever possible.
- Source code should contain abundant English comments.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, **EXPECTED**, and **RESULT**;
2. one separate short code block containing only the compact answer format.

Never ask for PH/HH page/video title text. Titles may be used locally only.

## Safety/content boundary
PH and HH are real technical playback targets; the user performs content testing.

Allowed: technical URLs/manifests/codecs/quality/request metadata/candidate ranking/browser state/playback state/errors/local titles.

Do not inspect/describe media content; bypass DRM, subscriptions/paywalls, authentication, regional restrictions or anti-bot/CAPTCHA; or import private Vivaldi credentials.

Automatic consent is conservative: only clearly identified 18+/age-confirmation or cookie-consent controls. Never ambiguous/login/payment/subscription/regional/DRM/challenge controls.

## Protected Batch 4 baseline
Keep yt-dlp first then browser fallback; automatic best candidate first; manual chooser fallback only; 720p -> 1080p -> best below 1080p; video/audio; quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate ordering/limits/ranking protections. Cloudinary is not a gate.

Bitmovin, PH and HH playback baseline was device-verified. Build #62 final-tab/title follow-up QA was already completed; do not repeat unless investigating regression. Build #74 clean-loading baseline PASS.

## Required selected feature bundle
User selected together:
1 background Add/pre-resolution;
2 foreground-only playback/background+lock stop;
3 conservative auto age/cookie handling;
4 clean browser-assisted loading;
5 tab readiness states;
6 persistent tabs;
20 polished tab switcher;
21 playback recovery;
23 adaptive icon;
24 persistent signing infrastructure, activation deliberately deferred;
25 build/version info;
28 temporary-network retry;
29 next-tab pre-resolution;
30 settings.

Treat as one required iteration. Signing activation is not a current QA blocker.

## Implemented architecture on `main`

### Persistent tabs / preparation
`VideoTabStore` uses local SharedPreferences and states `QUEUED`, `RESOLVING`, `READY`, `NEEDS_ATTENTION`, `ERROR`. It persists technical tab/session state, position, intended foreground play state and resolved JSON; no browser credentials/cookies. READY tabs reuse stored JSON.

### Background Add
Separate `Add to External Player` / `Añadir a External Player` share target uses transparent `BackgroundAddActivity`, creates a tab immediately and schedules WorkManager direct pre-resolution. Success -> READY; foreground-WebView need -> NEEDS_ATTENTION; explicit protected-access/challenge signals -> ERROR. Temporary retry is bounded. No background ExoPlayer.

### Foreground-only playback
`ForegroundPlaybackGuardProvider` pauses Media3 after PlayerActivity pause callbacks and on stop. Tab state saves the user's foreground play intent first. Background preparation may continue; playback may not.

### Browser clean UX / consent
Validated BrowserResolverActivity ranking is unchanged. `BrowserAssistEnhancerProvider` covers normal automatic resolution with clean Opening UI, reveals WebView when interaction is needed, never solves anti-bot, and locally clicks only narrowly matched clear English/Spanish age/cookie consent. `PendingBrowserTabBridgeProvider` completes an existing NEEDS_ATTENTION tab rather than duplicating it.

Pre-QA static review found one stale browser-title cache edge case. Commit `e59e35ac40696996b69fa91497fd5df6b60b0568` resets that cache whenever a new BrowserResolverActivity starts. Candidate ranking/source selection is unchanged.

### Tabs / recovery / preload
The tab switcher shows ready/total count, title, state, position, quality and independent close. NEEDS_ATTENTION routes to browser assistance; ERROR offers retry/browser recovery.

`PlayerRecoveryProvider` permits at most two automatic retries only for ordinary connection/timeout and HTTP 429/5xx failures, plus retry-same-source / browser recovery. No access bypass.

`TabPreparationManager` can pre-resolve the next queued tab when enabled; metadata/source preparation only, never concurrent playback.

### Settings / About / icon
`SettingsActivity`: local age/cookie automation toggles, network retry, next-tab pre-resolution, clear saved tabs.
`AboutActivity`: version `0.2.0` / code 2, Git commit, Actions run number.
Original adaptive launcher icon is included and is not copied from Vivaldi.

### Signing — deliberately deferred
Actions supports future permanent release signing, but on 2026-08-13 the user chose not to provision release secrets during the current public/development phase. Continue with debug-signed APKs for development and QA.

The future release key must remain outside Git history even if the repo becomes private. Plan for one uninstall/reinstall when transitioning from debug signing to the permanent release identity. A long-term signing kit exists outside GitHub and has not been committed. Feature 24 is infrastructure-prepared, activation-deferred.

## CI / designated QA build
- #74 PASS clean-loading baseline.
- #98 debug build/upload PASS through feature wiring.
- #99 workflow syntax failure before jobs; not Android compile failure.
- #100 PASS after workflow repair; only debug artifact because signing secrets were absent.
- #104 PASS including foreground privacy guard.
- #108 PASS after recording signing deferral.
- **#109 PASS on app-code commit `e59e35ac40696996b69fa91497fd5df6b60b0568`. Debug artifact SHA-256: `6298a92bf98c460db0f850d8ecdc241cb32d013793cd51d068f3536246bb7464`.**

Use **build #109** for consolidated device QA. Later state/documentation-only commits do not require a newer APK because they do not change packaged Android app code.

## Current priority
1. Consolidated device QA using build #109 while protecting Batch 4.
2. Fix runtime/device regressions before unrelated additions.
3. Repeat focused QA until stable.
4. Activate permanent release signing only later in a stable/private-repo phase; never commit the key.
5. Brave later.
