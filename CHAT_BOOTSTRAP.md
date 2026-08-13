# Temporary Chat Bootstrap — Vivaldi External Player

Public repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Treat GitHub `main` as the source of truth. Before changing code, read `PROJECT_STATE.md` completely. Keep both state files updated whenever requirements, architecture, tests, failures, decisions, or priorities materially change.

## Communication

- Conversation: English.
- Windows/Vivaldi UI: normally Spanish; use Spanish UI labels when relevant.
- Android app UI: bilingual English/Spanish.
- Explain plainly; user is not an advanced developer.
- Do GitHub work directly whenever possible.
- Source code should contain abundant English comments.

## QA format

Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, **EXPECTED**, and **RESULT**;
2. one separate short code block containing only the compact answer format.

Never ask for PH/HH page/video title text. Titles may be used locally by the app only.

## Safety/content boundary

PH and HH are real technical playback targets. The user performs playback/content testing.

Allowed technical work: URLs, manifests, codecs, resolutions/qualities, request metadata, candidate ranking, browser/network state, playback errors/status, and local tab-title handling.

Do not inspect/analyze/classify/describe media content. Do not bypass DRM, subscriptions/paywalls, authentication, regional restrictions, anti-bot/CAPTCHA challenges, or import private Vivaldi credentials.

### Automatic age/cookie consent

The user wants conservative automation only for clearly identified 18+ confirmation and cookie-consent prompts. Never auto-click ambiguous buttons, login/account prompts, paywall/subscription/payment controls, regional controls, DRM controls, anti-bot/CAPTCHA/challenge controls, or unrelated actions. If uncertain, leave the prompt for the user.

## Verified Batch 4 baseline

Protect:
- Vivaldi share flow;
- yt-dlp first, then automatic browser-assisted fallback;
- automatic best candidate first, manual chooser only as fallback;
- quality policy 720p -> 1080p -> best below 1080p;
- video + audio;
- adaptive and sibling quality switching;
- double-tap ±10 seconds;
- seek preview;
- rotation;
- bilingual UI;
- up to 80 stored candidates / strongest 20 manual candidates;
- meaningful first-seen HLS/DASH ordering;
- no generic playlist bonus;
- soft demotion of obvious audio-only/video-only child paths;
- page-config family IDs for sibling URLs;
- no media imagery inspection.

Verified targets:
- Bitmovin automatic/video/audio PASS.
- PH automatic/video/audio/quality options/quality switching PASS.
- HH automatic/video/audio/quality options/quality switching PASS.
- Cloudinary is not a QA gate.

The follow-up build #62 final-tab cleanup/browser-title QA was already completed/validated. Do not ask to repeat it unless investigating a regression.

## Clean Opening / Buffering baseline

Build #74 passed. `Opening video…` / `Abriendo video...` is used while opening, Media3-driven `Buffering…` / `Cargando...` appears only during real buffering, and raw direct-resolver error flicker was removed from the normal automatic fallback transition. Candidate ranking/source selection did not change.

## Selected next-build bundle — required together

On 2026-08-13 the user selected these feature candidates for the next major build:
1. background Add + pre-resolution;
2. stop playback whenever app is backgrounded/phone locked;
3. conservative automatic clear 18+/cookie prompts;
4. completely clean browser-assisted loading until interaction is needed;
5. READY/resolving/error tab indicators;
6. persistent tabs after app/process restart;
20. more polished tab switcher;
21. playback error recovery;
23. adaptive launcher icon;
24. persistent signed release APKs;
25. in-app version/build information;
28. bounded automatic retry for temporary network failures;
29. pre-resolve the next queued tab;
30. local settings screen.

Treat these as one coordinated next-build scope, not separate optional priorities.

## Persistent tab/preparation architecture on `main`

`VideoTabStore` is now backed by local SharedPreferences and persists:
- tab ID/title/source URL;
- completed ResolvedMedia JSON;
- position;
- intended foreground play state;
- preparation state/error status.

States: `QUEUED`, `RESOLVING`, `READY`, `NEEDS_ATTENTION`, `ERROR`.

READY tabs reuse stored resolved JSON. No private browser credentials/cookies are stored.

`TabbedPlayerApplication` initializes/restores persistent tabs, resumes queued preparation, restores READY tab state, pre-resolves the next queued tab, and displays a richer tab switcher with status/position/quality.

## Background Add / pre-resolution

A second Android share target is registered:
- `Add to External Player` / `Añadir a External Player`;
- transparent `BackgroundAddActivity` creates the tab immediately, schedules work and finishes;
- WorkManager `ResolveTabWorker` performs yt-dlp/direct pre-resolution without creating ExoPlayer;
- successful direct resolution -> READY;
- browser-required ordinary failure -> NEEDS_ATTENTION;
- explicit DRM/challenge/paywall/login/geo signals -> ERROR, never bypassed;
- temporary network errors get bounded retries;
- queued preparation resumes after process restart.

WorkManager dependency currently `androidx.work:work-runtime-ktx:2.11.2`.

## Foreground-only playback

`ForegroundPlaybackGuardProvider` explicitly pauses the PlayerActivity Media3 player after pause callbacks and on stop. Tab snapshotting preserves position and the user's pre-background play intention first, so automatic privacy pause is distinguishable from deliberate pause.

Goal for device QA: no video/audio after switching apps, Home, or phone lock; returning can restore sensible state. Background preparation may continue.

## Browser-assisted clean UX + consent

The validated BrowserResolverActivity candidate-ranking code is intentionally unchanged.

`BrowserAssistEnhancerProvider`:
- covers automatic browser resolution with clean `Opening video…` UI;
- keeps the cover while an existing candidate is automatically launched;
- reveals the WebView when user interaction is actually needed;
- reveals challenge/CAPTCHA pages and never solves them;
- locally accepts only narrowly matched, clearly identified English/Spanish age-18+ or cookie consent controls;
- excludes ambiguous/login/payment/subscription/regional/DRM/challenge scopes;
- uses no media imagery inspection.

`PendingBrowserTabBridgeProvider` makes foreground browser completion update the same persistent NEEDS_ATTENTION tab instead of creating a duplicate.

## Error recovery / retry

`PlayerRecoveryProvider`:
- adds maximum-two automatic retries for normal network connection/timeout and HTTP 429/5xx playback failures;
- adds recovery options to retry same source;
- browser playback can return to already-detected candidate UI;
- direct playback can open normal browser assistance using the original webpage URL;
- no protected-access bypass.

## Settings / build info / icon

`SettingsActivity` has local toggles for age prompt handling, cookie prompt handling, temporary-network retry and next-tab pre-resolution, plus Clear saved tabs.

`AboutActivity` shows version `0.2.0` / versionCode 2, Git commit, and GitHub Actions run number when built by Actions.

Original adaptive launcher icon added: dark background, white V-shaped player mark and red play triangle; not a copy of Vivaldi branding.

## Persistent APK signing

Workflow support exists for a persistent signed release APK, but the public repo does not contain private key material.

Required repository secrets:
- `VEP_KEYSTORE_BASE64`;
- `VEP_KEYSTORE_PASSWORD`;
- `VEP_KEY_ALIAS`;
- `VEP_KEY_PASSWORD`.

When present, Actions decodes the keystore temporarily, builds release, verifies it with apksigner, uploads it, then removes the temporary key. Without secrets the workflow builds/uploads debug only.

Important limitation: the connected GitHub tool currently exposes no repository-secret write action. Initial secret provisioning therefore requires one secure one-time user-side GitHub Secrets action (or another connector with secret-management permission). Never commit/share a private keystore in the public repository.

## CI status for this iteration

- App build #98: Android `Build debug APK` SUCCESS and `Upload APK` SUCCESS through commit `649fc10d31496d8dc1dcc7e177c11c5d8488c430`.
- Signing-workflow run #99 failed before creating a job due to the first workflow rewrite; not an Android compile failure.
- Commit `95b50ff66eb6e929e1689acf5d39d747fe6d5993` simplified/fixed the workflow; build #100 was running during implementation.
- Explicit foreground playback guard was added after #98, so newest CI must pass before consolidated device QA.

## Current priority

1. Obtain a fully clean newest CI run for the complete selected bundle.
2. Securely provision persistent signing secrets (only remaining non-code dependency for feature 24).
3. Produce one consolidated next-build APK set and device-QA the selected 14-feature bundle.
4. Protect Batch 4 playback baseline while fixing any device-only failures.
5. Brave support later.
