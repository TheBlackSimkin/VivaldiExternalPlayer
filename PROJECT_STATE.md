# Vivaldi External Player — Project State

> Operational source of truth. GitHub `main` is authoritative. Keep this file current whenever requirements, architecture, QA, failures, decisions, or priorities materially change.

## Environment / communication

- Android phone; Vivaldi Mobile Browser is Phase 1.
- Conversation: English. Windows/Vivaldi UI is normally Spanish; use Spanish UI labels when relevant.
- Android app UI remains bilingual English/Spanish.
- Use connected GitHub tools directly whenever possible; do not make the user manually edit/commit files when the connector can do it.
- Explain plainly; user is not an advanced developer. Source code should contain abundant English comments.

## Safety/content boundary

PH and HH are real technical playback targets. The user performs all media-content testing.

Allowed: technical work on URLs, manifests, codecs, resolutions/qualities, request metadata, candidate ranking, browser/network state, playback errors/status, and local tab titles.

Do not inspect/analyze/classify/describe PH/HH video content. Do not bypass DRM, subscriptions/paywalls, authentication, regional restrictions, anti-bot/CAPTCHA challenges, or import private Vivaldi credentials. Local titles may be used on-device; never ask the user to send PH/HH title text.

### Conservative automatic consent

Automatically accept only clearly identified 18+/age-confirmation and cookie-consent prompts. Never auto-click ambiguous buttons, login/account, payment/subscription/paywall, regional, DRM, anti-bot/CAPTCHA/challenge, or unrelated controls. If uncertain, leave it for the user.

## Verified Batch 4 baseline — must not regress

Quality policy: exact 720p -> otherwise 1080p -> otherwise highest available below 1080p.

Protect:
- Vivaldi share;
- yt-dlp first then browser-assisted fallback;
- automatic best candidate first, manual chooser fallback only;
- video + audio;
- adaptive and sibling-URL quality switching;
- double-tap ±10 seconds;
- seek thumbnail preview where supported;
- portrait/landscape;
- English + Spanish;
- up to 80 stored candidates / strongest 20 manual;
- meaningful first-seen HLS/DASH ordering;
- no generic playlist bonus;
- soft audio-only/video-only child demotion;
- page-config family IDs;
- no media imagery inspection.

Verified device baseline:
- Bitmovin automatic/video/audio PASS.
- PH automatic/video/audio/quality options/quality switching PASS.
- HH automatic/video/audio/quality options/quality switching PASS.
- Cloudinary is explicitly skipped and is not a QA gate.

Build #62 follow-up final-tab/title QA was already completed/validated. Do not ask to repeat unless investigating a regression.

## Clean loading baseline

Build #74 PASS. Normal flow uses `Opening video…` / `Abriendo video...`; Media3-driven `Buffering…` / `Cargando...` appears only for real buffering; raw direct-resolver error flicker was removed. Candidate ranking/source selection remained unchanged.

## Selected next-build bundle — 2026-08-13

The user selected these features together for the next major build:
1. background Add + pre-resolution;
2. foreground-only playback / stop on app background or phone lock;
3. conservative automatic clear 18+/cookie prompts;
4. completely clean browser-assisted loading until interaction is required;
5. READY/resolving/error tab indicators;
6. process-restart persistent tabs;
20. polished tab switcher;
21. playback error recovery;
23. adaptive launcher icon;
24. persistent signed release APKs;
25. in-app version/build information;
28. bounded automatic retry for temporary network failures;
29. pre-resolve the next queued tab;
30. local settings screen.

Treat these as one coordinated required feature set, not separate optional priorities.

## Persistent tabs / preparation states

`VideoTabStore` is now a local SharedPreferences-backed persistent store. It saves technical session state only: tab ID/title, source URL, resolved-media JSON, position, intended foreground play state, preparation state/error status and timestamps. It does not persist WebView cookies, Vivaldi credentials or private authentication data.

States: `QUEUED`, `RESOLVING`, `READY`, `NEEDS_ATTENTION`, `ERROR`.

READY tabs reuse stored resolved JSON and must not re-resolve merely because selected. Stale RESOLVING state after process death is reset to QUEUED and can resume through WorkManager.

`TabbedPlayerApplication` initializes/restores tabs, updates an existing pending tab when foreground browser completion succeeds, restores position/play intent, pre-resolves the next queued tab, and shows a state-aware tab switcher. One actual ExoPlayer playback session remains the rule.

## Background Add / pre-resolution

Implemented:
- second Android share target: `Add to External Player` / `Añadir a External Player`;
- transparent `BackgroundAddActivity` creates the tab immediately, schedules preparation and finishes so the browser/task can remain foreground context;
- WorkManager `ResolveTabWorker` performs direct/yt-dlp pre-resolution under connected-network constraint;
- pre-resolution never constructs ExoPlayer;
- direct success -> READY;
- normal browser-required failure -> NEEDS_ATTENTION;
- explicit DRM/challenge/paywall/login/geo signals -> ERROR and are not bypassed;
- temporary network failures get bounded retry;
- queued work resumes after process restart;
- selecting READY uses stored media JSON.

WorkManager dependency: `androidx.work:work-runtime-ktx:2.11.2`.

## Foreground-only playback / privacy

Playback must not continue when External Player leaves foreground or phone is locked.

Implementation:
- tab snapshot stores position and the user's foreground play intention before privacy pause;
- `ForegroundPlaybackGuardProvider` posts a Media3 pause after `PlayerActivity.onPause` callbacks and also pauses on stop;
- returning to the same tab can restore saved user intent;
- background WorkManager preparation may continue, but playback may not.

No background audio, PiP autoplay, media-session continuation or foreground playback service is introduced. Device QA is still required for app switch/Home/lock behavior.

## Clean browser-assisted UX + conservative consent

The validated `BrowserResolverActivity` candidate-ranking logic remains unchanged.

`BrowserAssistEnhancerProvider` sits above it:
- black `Opening video…` cover during automatic discovery;
- keeps cover while existing candidate debounce/automatic launch runs;
- reveals WebView when interaction is actually needed;
- returning from a wrong/failed automatic candidate reveals the existing manual resolver UI;
- challenge/CAPTCHA signals reveal the page and are never auto-solved.

Settings-controlled local DOM helper only clicks narrowly matched clear English/Spanish 18+ or cookie controls. It excludes ambiguous/login/payment/subscription/regional/DRM/challenge scopes and performs no media imagery inspection.

`PendingBrowserTabBridgeProvider` makes foreground browser completion update the same NEEDS_ATTENTION persistent tab rather than duplicate it.

## Tab status / polished switcher

The tab UI now displays:
- local title / active marker;
- preparation state;
- saved position;
- selected/displayed quality when available;
- independent close action;
- ready/total count on the player tab button.

NEEDS_ATTENTION routes to foreground browser assistance. ERROR exposes retry/browser recovery instead of pretending READY.

## Playback recovery / automatic retry

`PlayerRecoveryProvider` observes Media3 errors without changing candidate/source ranking.

Safe recovery:
- maximum two automatic retries only for network connection/timeout or HTTP 429/5xx;
- retry same source preserving current position;
- browser playback can return to already-detected candidates;
- direct playback can explicitly open normal browser assistance using the original webpage URL.

No recovery path bypasses protected access.

## Next-tab pre-resolution

On PlayerActivity resume, `TabbedPlayerApplication` asks `TabPreparationManager` to pre-resolve the next queued tab when enabled. This prepares metadata/source URLs only and never starts a second player. Background-added tabs are also scheduled immediately.

## Settings / About

`SettingsActivity` provides local toggles for:
- clear age prompts;
- clear cookie prompts;
- bounded temporary-network retry;
- next-tab pre-resolution;
plus local Clear saved tabs.

`AboutActivity` shows version `0.2.0` / versionCode 2, Git commit and GitHub Actions run number (with local-development fallbacks).

## Adaptive launcher icon

Original project icon added: dark adaptive background, white V-shaped player mark and red play triangle, with Android 8+ adaptive icon plus legacy vector fallback. It is not a copy of Vivaldi branding.

## Persistent APK signing

Workflow support is implemented securely. CI always builds debug. When these GitHub repository secrets exist, it also builds and verifies a persistently signed release APK:
- `VEP_KEYSTORE_BASE64`;
- `VEP_KEYSTORE_PASSWORD`;
- `VEP_KEY_ALIAS`;
- `VEP_KEY_PASSWORD`.

The keystore is decoded only on the temporary runner and removed after build. Never commit private signing material to this public repo.

A new long-term release keystore/signing kit was generated outside GitHub on 2026-08-13. It has NOT been committed. The connected GitHub tool has no repository-secret write action, so the user must perform one secure one-time GitHub Actions Secrets provisioning step. After that ChatGPT can re-run the workflow directly with the connected Actions tool.

Until secrets are provisioned, only the debug APK artifact exists.

## CI status

- Build #74: PASS clean-loading baseline.
- Build #98: debug APK build + upload PASS through the full feature wiring before final foreground-guard hardening.
- Build #99: failed before jobs due to the first signing-workflow rewrite; this was a workflow/YAML issue, not Android compile failure.
- Build #100: PASS after workflow simplification; exactly one artifact existed (debug APK), confirming signing secrets were not configured yet.
- **Build #104: PASS on commit `5cffc11bc893dc6e4af496a861847eb24c863c0b`, including the explicit foreground playback guard and both state-file updates. Debug APK artifact uploaded successfully.**

Therefore the selected 14-feature Android source bundle is currently compile-clean on `main`. Runtime/device QA remains required, and persistent release signing becomes operational after the one-time secret provisioning.

## Current priority

1. Securely provision the four signing secrets from the generated private signing kit; do not commit the key.
2. Re-run Actions and require both debug and signed-release APK artifacts plus successful `apksigner` verification.
3. Perform one consolidated device QA for the selected 14-feature bundle while protecting Batch 4 playback.
4. Fix any runtime/device regressions before adding unrelated features.
5. Brave support later.

## QA format

Whenever asking the user to test, provide EXACTLY:
1. one detailed code block with steps, EXPECTED, and RESULT;
2. one separate short code block containing only the compact answer format.

Never ask for PH/HH title text. Cloudinary is not required.
