# Temporary Chat Bootstrap — Vivaldi External Player

Public repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Treat GitHub `main` as the source of truth. Before changing code, read `PROJECT_STATE.md` completely. Keep both state files updated whenever requirements, architecture, tests, failures, decisions, or priorities materially change.

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

Bitmovin, PH and HH playback baseline was device-verified. Build #62 follow-up final-tab/title QA was already completed; do not repeat unless investigating regression.

Build #74 clean-loading baseline PASS: `Opening video…` / `Abriendo video...`; real Media3 `Buffering…` / `Cargando...`; raw resolver flicker removed; ranking unchanged.

## Required next-build feature bundle

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
24 persistent signing infrastructure, with activation deliberately deferred;
25 build/version info;
28 temporary-network retry;
29 next-tab pre-resolution;
30 settings.

Treat as one required iteration. Signing activation itself is not a current QA blocker.

## Implemented architecture on `main`

### Persistent tabs
`VideoTabStore` uses local SharedPreferences and states `QUEUED`, `RESOLVING`, `READY`, `NEEDS_ATTENTION`, `ERROR`. It persists technical tab/session state, position, intended foreground play state and resolved JSON; no browser credentials/cookies. READY tabs reuse stored JSON.

### Background Add
Separate `Add to External Player` / `Añadir a External Player` ACTION_SEND target uses transparent `BackgroundAddActivity`, creates a tab immediately and schedules WorkManager direct pre-resolution. Success -> READY; foreground-WebView need -> NEEDS_ATTENTION; explicit DRM/challenge/paywall/login/geo -> ERROR. Temporary network retry is bounded. No background ExoPlayer.

### Foreground-only playback
`ForegroundPlaybackGuardProvider` explicitly pauses Media3 after PlayerActivity pause callbacks and on stop. Tab state saves the user's foreground play intent first. Background preparation may continue; playback may not.

### Browser clean UX / consent
Validated BrowserResolverActivity ranking is unchanged. `BrowserAssistEnhancerProvider` covers normal automatic resolution with clean Opening UI, reveals WebView when interaction is needed, never solves anti-bot, and locally clicks only narrowly matched clear English/Spanish age/cookie consent. `PendingBrowserTabBridgeProvider` completes an existing NEEDS_ATTENTION tab instead of duplicating it.

### Polished tab switcher
Shows ready/total count, tab title, state, position, quality, independent close. NEEDS_ATTENTION routes to browser assistance; ERROR offers retry/browser path.

### Recovery / retry
`PlayerRecoveryProvider` adds max-two retries only for connection/timeout and HTTP 429/5xx, retry-same-source, return-to-detected-browser-candidates, or normal browser fallback. No access bypass.

### Preload next
`TabPreparationManager` pre-resolves next queued tab when enabled; metadata/source prep only, never concurrent playback.

### Settings/About
`SettingsActivity`: local age/cookie automation toggles, network retry, next-tab pre-resolution, clear saved tabs.
`AboutActivity`: version `0.2.0` / code 2, Git commit, Actions run number.

### Icon
Original adaptive icon: dark background, white V-shaped player mark, red play triangle; legacy vector fallback; not copied from Vivaldi.

### Signing — deliberately deferred
Actions workflow already supports the four release-signing secrets and will build/verify a permanent release APK when they are supplied later. On 2026-08-13 the user chose not to provision them during the current public/development phase.

Continue with debug-signed APKs for development and device QA. The future release key must remain outside Git history even if the repository becomes private. Plan for one uninstall/reinstall when transitioning from debug signing to the permanent release identity because Android normally will not update a debug-signed installation with a differently signed release APK.

A long-term signing kit exists outside GitHub and has not been committed. Feature 24 is considered infrastructure-prepared, activation-deferred.

## CI status

- #74 PASS.
- #98 debug build/upload PASS through feature wiring.
- #99 failed before jobs because first signing-workflow version was invalid; not Android compile failure.
- #100 PASS after workflow repair; only debug artifact existed because secrets were absent.
- **#104 PASS on commit `5cffc11bc893dc6e4af496a861847eb24c863c0b`, including foreground privacy guard; debug artifact uploaded.**

The selected Android source bundle is compile-clean. Runtime/device QA still required. Release-signing activation is intentionally postponed and does not block this iteration.

## Current priority

1. Consolidated device QA for the selected feature bundle using the latest compile-clean debug APK while protecting Batch 4.
2. Fix runtime/device regressions before unrelated additions.
3. Repeat focused QA until stable.
4. Activate permanent release signing only later in the stable/private-repo phase; never commit the key.
5. Brave later.
