# Vivaldi External Player — Project State

> Operational source of truth. GitHub `main` is authoritative. Update this file whenever requirements, architecture, QA results, failures, decisions, or priorities materially change.

## Environment and communication

- Android phone; Vivaldi Mobile Browser is Phase 1.
- Conversation with ChatGPT: English.
- Windows/Vivaldi UI is normally Spanish; use Spanish UI labels when relevant.
- Android app UI remains bilingual English/Spanish.
- Do GitHub work directly whenever possible; do not require manual file editing/upload/commits when the connected GitHub tools can do it.
- Explain implementation/test steps plainly; user is not an advanced developer.
- Source code should contain abundant English comments.

## Safety/content boundary

PH and HH are real technical playback targets. The user performs all media-content testing.

Allowed technical work: URLs, manifests, codecs, qualities/resolutions, request metadata, candidate ranking, browser/network state, playback state/errors, and local tab-title handling.

Do not inspect/analyze/classify/describe PH/HH video content. Do not bypass DRM or obtain keys. Do not bypass subscriptions/paywalls, authentication, regional controls, anti-bot/CAPTCHA/challenge systems, or import private Vivaldi credentials.

Local page/video titles may be used on-device for tab labels. ChatGPT must not request PH/HH title text.

### Automatic consent policy

The user is over 18 and wants conservative automatic acceptance only for clearly identified:
- 18+ / age-confirmation prompts;
- cookie-consent prompts.

Never auto-click ambiguous controls, login/account controls, paywall/subscription/payment controls, regional-access controls, DRM-related controls, anti-bot/CAPTCHA/challenge controls, or unrelated page actions. If uncertain, leave the prompt for the user.

## Verified Batch 4 playback baseline — protect from regression

Quality policy:
1. exact 720p when available;
2. otherwise 1080p;
3. otherwise highest available quality below 1080p.

Required baseline behavior:
- Share from Vivaldi.
- yt-dlp first, then automatic browser-assisted fallback.
- Automatic best candidate first; manual chooser fallback only.
- Video + audio playback.
- Adaptive and sibling-URL quality switching.
- Double tap left/right = -10/+10 seconds.
- Timeline thumbnail preview where technically supported.
- Portrait/landscape.
- English + Spanish UI.

Verified device baseline:
- Bitmovin: automatic YES, video YES, audio YES.
- PH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
- HH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
- Cloudinary is explicitly skipped and is not a QA gate.

Resolver protections:
- discovery settles before automatic best-candidate attempt;
- manual chooser fallback only;
- up to 80 candidates stored, strongest 20 shown manually;
- meaningful first-seen HLS/DASH ordering;
- no generic `playlist` bonus;
- soft demotion of obvious audio-only/video-only child paths;
- page-config family IDs for sibling quality URLs;
- no media imagery inspection.

## Multi-video tabs and persistence

Core multi-tab behavior was device-validated before this iteration. Build #62 follow-up QA for final-tab cleanup/browser titles was already completed; do not ask the user to repeat it unless investigating a regression.

### Current persistent architecture

`VideoTabStore` is now a local persistent store backed by SharedPreferences. Tabs survive normal process death/app restart and contain technical session state only:
- tab ID and local title;
- original/source webpage URL;
- completed `ResolvedMedia` JSON when available;
- playback position;
- intended foreground play/pause state;
- preparation state and limited technical error text;
- timestamps.

No WebView cookies, Vivaldi credentials or private authentication data are persisted by the tab store.

Preparation states:
- `QUEUED`;
- `RESOLVING`;
- `READY`;
- `NEEDS_ATTENTION`;
- `ERROR`.

A READY tab must use its stored resolved-media JSON and must not resolve again merely because the user selects it.

`TabbedPlayerApplication` remains the coordinator above the validated resolver/player logic. It now initializes persistent tabs, resumes queued preparation, updates an existing pending tab when foreground browser assistance completes, restores tab position/play intent, pre-resolves the next queued tab, and exposes richer tab status UI. One actual ExoPlayer playback session remains the architectural rule.

## Selected next-build feature bundle — 2026-08-13

The user selected these features as one coordinated next-build requirement:
1. background Add + immediate pre-resolution;
2. foreground-only playback / stop when backgrounded or locked;
3. conservative clear age/cookie prompt automation;
4. clean browser-assisted loading screen;
5. READY/resolving/error tab indicators;
6. process-restart persistent tabs;
20. more polished tab switcher;
21. better playback error recovery;
23. adaptive launcher icon;
24. proper persistent APK signing;
25. in-app version/build information;
28. limited automatic retry for temporary network failures;
29. pre-resolve/preload the next queued tab;
30. local app settings screen.

Do not split these back into unrelated priorities: they are the required feature set for the next major device-QA build.

## Background Add / pre-resolution

Implemented architecture on `main`:
- separate Android share target labelled `Add to External Player` / `Añadir a External Player`;
- a transparent `BackgroundAddActivity` creates a persistent tab immediately and finishes so the calling browser/task can remain the user's foreground context;
- WorkManager performs direct/yt-dlp pre-resolution under a connected-network constraint;
- pre-resolution stores resolved metadata/URLs only and never constructs ExoPlayer;
- direct success marks the tab READY;
- ordinary direct failure which may need a WebView marks `NEEDS_ATTENTION` rather than falsely READY;
- explicit DRM/challenge/paywall/login/geo signals are not retried around and become ERROR;
- transient network failures use bounded retry only;
- queued work is resumed after process restart;
- selecting a READY tab uses stored JSON directly.

WorkManager dependency: `androidx.work:work-runtime-ktx:2.11.2`.

## Foreground-only playback / privacy

Playback must never continue when External Player is not actively foregrounded.

Implementation on `main` now has two layers:
- tab session snapshot preserves position and the user's foreground play intention;
- `ForegroundPlaybackGuardProvider` applies an explicit Media3 pause after `PlayerActivity.onPause` callbacks and again on stop.

This is designed so switching to Vivaldi/another app or locking the screen stops video/audio, while returning to the same tab can restore the saved user intention. Background WorkManager preparation may continue; playback may not.

No background audio, PiP autoplay, media-session continuation or foreground playback service is introduced.

Device QA is still required for Home/app switch/lock behavior.

## Clean browser-assisted UX + conservative consent

The validated `BrowserResolverActivity` candidate-ranking code remains unchanged.

`BrowserAssistEnhancerProvider` adds a presentation/consent layer above it:
- a clean black `Opening video…` overlay covers the WebView during normal automatic discovery;
- when a candidate exists, the cover stays while the existing resolver debounce/automatic best-candidate path runs;
- if no automatic candidate appears after a short grace period, the real WebView is revealed for user attention;
- returning from a failed/wrong automatic candidate reveals the existing resolver/manual chooser;
- anti-bot/challenge signals reveal the page and are never auto-solved.

Conservative local DOM consent automation is settings-controlled and accepts only narrowly matched clear English/Spanish 18+ confirmations or cookie consent. It explicitly excludes ambiguous controls and scopes containing login, subscription/payment, regional, DRM or challenge/CAPTCHA markers. No media imagery is inspected.

## Tab status and polished switcher

The player tab button now summarizes ready/total count. The tab dialog shows:
- local tab title;
- active marker;
- preparation status;
- saved playback position;
- selected/displayed quality when available;
- independent close action.

NEEDS_ATTENTION tabs route to foreground browser assistance. ERROR tabs expose retry/browser-assisted recovery rather than pretending to be playable.

## Playback error recovery and retry

`PlayerRecoveryProvider` observes Media3 playback errors without changing source-selection/ranking logic.

Safe recovery behavior:
- limited automatic retry (maximum two) only for normal network connection/timeout failures and HTTP 429/5xx responses;
- recovery button after an error;
- retry same source while preserving current position;
- browser-resolved playback can return to the already-existing resolver/candidate list;
- direct-resolved playback can explicitly open browser assistance using the original webpage URL.

No retry path bypasses DRM, authentication, paywall, regional or challenge controls.

## Preload next tab

When a PlayerActivity resumes, `TabbedPlayerApplication` asks `TabPreparationManager` to pre-resolve the next queued tab when the setting is enabled. This is metadata/source preparation only; it never starts a second ExoPlayer.

Background-added tabs are also independently scheduled immediately, so several queued tabs may resolve in parallel as Android allows, while playback remains single-session foreground-only.

## Local settings

`SettingsActivity` provides local on-device toggles for:
- conservative clear 18+ prompt handling;
- conservative clear cookie prompt handling;
- bounded temporary-network retry;
- next-tab pre-resolution.

It also explains persistent tabs and provides a local Clear saved tabs action. Settings are stored only on-device.

## About / build information

`AboutActivity` shows:
- version name/code (`0.2.0`, versionCode 2 for this iteration);
- Git commit identifier from `GITHUB_SHA` when built in Actions;
- GitHub Actions run number from `GITHUB_RUN_NUMBER`;
- local-development fallbacks when not built in Actions.

This is intended to make device QA identify the installed APK precisely.

## Adaptive launcher icon

Original project icon added:
- dark adaptive background;
- original white V-shaped player mark;
- small red play triangle;
- adaptive icon for Android 8+ plus legacy vector fallback.

It is an original External Player mark, not a copy of the Vivaldi browser logo.

## Persistent APK signing

Code/workflow support is implemented but secure key material is not present in the public repository.

The Actions workflow always builds a debug APK. If these repository secrets exist, it also builds and verifies a persistently signed release APK:
- `VEP_KEYSTORE_BASE64`;
- `VEP_KEYSTORE_PASSWORD`;
- `VEP_KEY_ALIAS`;
- `VEP_KEY_PASSWORD`.

The decoded keystore exists only temporarily on the GitHub Actions runner and is deleted after the build. The private keystore must never be committed to this public repository.

Current limitation: the connected GitHub tool available to ChatGPT has no repository-secret write action, so initial signing-secret provisioning requires a one-time secure user-side GitHub Secrets step (or another connector/tool with secret-management permission). Until then, CI produces the normal debug APK and skips the signed release artifact.

## CI / implementation status

- Clean-loading app build #74: PASS.
- During this large next-build batch, workflow run #98 reached `Build debug APK: SUCCESS` and `Upload APK: SUCCESS`, confirming the Android source through commit `649fc10d31496d8dc1dcc7e177c11c5d8488c430` compiled and produced an artifact.
- The first signing-workflow rewrite produced workflow run #99 failure before any job was created; this was a CI/YAML workflow failure, not an Android compile failure.
- Commit `95b50ff66eb6e929e1689acf5d39d747fe6d5993` simplified/fixed the optional-signing workflow; build #100 was running when this state entry was written.
- `ForegroundPlaybackGuardProvider` was added after the #98 compile and requires the newest CI run to complete before the entire selected bundle can be called compile-clean.

## Current architecture summary

### MainActivity
- foreground `ACTION_SEND / text/plain` entry;
- yt-dlp first, browser-assisted fallback;
- clean opening state;
- home entry points for saved tabs, Settings and About.

### BackgroundAddActivity / ResolveTabWorker / TabPreparationManager
- separate background-add share target;
- immediate persistent tab creation;
- WorkManager direct resolution/preparation;
- prep states and bounded retry;
- no ExoPlayer/background playback.

### resolver.py
- yt-dlp via Chaquopy;
- does not download media files;
- rejects media marked DRM;
- Batch 4 quality policy unchanged.

### BrowserResolverActivity
Validated candidate discovery/ranking remains unchanged: ordinary WebView/service-worker requests, page `<video>`/`<source>` elements, Performance API resources, and exposed technical player configuration.

### BrowserAssistEnhancerProvider
- clean resolver cover;
- conservative age/cookie DOM helper;
- reveals interaction when necessary;
- no candidate-ranking changes.

### PlayerActivity / GesturePlayerView
- Media3 ExoPlayer;
- progressive/HLS/DASH;
- merged video/audio;
- quality switching;
- seek preview/double-tap;
- diagnostics;
- clean Opening/Buffering overlay.

### PlayerRecoveryProvider / ForegroundPlaybackGuardProvider
- bounded normal-network recovery;
- safe manual retry routes;
- explicit no-background playback enforcement.

### VideoTabStore / TabbedPlayerApplication
- persistent independent tabs;
- preparation states;
- per-tab resolved JSON/title/position/play intent;
- polished state-aware switcher;
- one active ExoPlayer playback session.

## QA format

Whenever asking the user to test, provide EXACTLY:
1. one detailed code block with steps, EXPECTED, and RESULT;
2. one separate short code block containing only the compact answer format.

Never ask for PH/HH title text. Cloudinary is not required.

## Current priority

1. Get the newest CI build fully clean after the foreground-playback guard and documentation commits.
2. Provision persistent signing secrets securely so the next major QA build can include a signed release APK as well as debug.
3. Perform one consolidated device QA for the selected 14-feature next-build bundle while protecting the verified Batch 4 playback baseline.
4. Fix any device-only failures/regressions found by that consolidated QA before adding unrelated features.
5. Brave support remains later.
