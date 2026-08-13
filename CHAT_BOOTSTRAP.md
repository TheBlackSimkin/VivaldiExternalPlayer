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
PH and HH are real technical playback targets tested by the user. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed. Do not inspect/describe media content. Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Never ask user to send PH/HH title text.

Conservative automation: only clearly identified 18+/age and cookie prompts may be auto-handled. Ambiguous/login/payment/region/DRM/challenge controls remain user-driven.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise best below 1080p.

Protect Vivaldi share; yt-dlp first/browser fallback; automatic best candidate/manual fallback; video+audio; adaptive and sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate limits/order/ranking protections; no media imagery inspection; one ExoPlayer playback session.

Bitmovin/PH/HH core playback baseline was previously device-verified. Cloudinary is not a gate. Build #62 follow-up was already validated. Build #74 clean-loading baseline PASS.

## Selected bundle
Features 1,2,3,4,5,6,20,21,23,24,25,28,29,30 are the coordinated iteration. Feature 24 signing activation is deliberately deferred; debug APK QA continues.

## Build #109 partial device QA
PASS/correct: install, icon, About/build info, Settings, no background playback, clean browser UX, browser title. Age/cookie prompts were not shown. Tabs/buttons and displayed quality options generally looked good, though UI can be polished later.

Regressions:
- background Add brought External Player forward instead of reliably leaving Vivaldi;
- background action was hard to identify;
- browser-assisted background tabs were not really pre-resolved until user opened External Player and selected the tab;
- HH showed automatic-preparation error even though normal browser-assisted method worked;
- adaptive quality menu listed correct resolutions but manual choice did not actually change rendition.

Do not repeat #109 PASS items unless needed for regression investigation.

## User-requested share labels
Spanish:
- `Añadir en External Player en segundo plano`
- `Añadir en External Player`

Current strings use those names. Background intent-filter order 100 vs normal 10, but Android/Vivaldi controls final visual ranking.

## Build #124 fixes on `main`
### Real background browser preparation
`BackgroundShareActivity` is the background share target. It creates the persistent tab, starts `BackgroundPreparationActivity` in a separate excluded-from-recents task, then removes its hand-off task.

`BackgroundPreparationActivity` tries yt-dlp first, then automatically uses an invisible WebView when direct resolution misses. It moves behind Vivaldi, never creates ExoPlayer, uses only technical browser signals, conservatively handles clear age/cookie prompts, and never bypasses protected access. Success -> READY. Genuine interaction/timeout -> NEEDS_ATTENTION for the normal visible browser resolver.

WorkManager remains for restart recovery/direct retry/next-tab prep. If feature 29 still stops at direct-only preparation in later QA, unify it with this new browser-capable path.

### Actual adaptive quality switching
`AdaptiveQualityRuntime` installs through the existing foreground guard. For adaptive browser HLS/DASH it applies an exact Media3 single-track override plus exact video-size constraints, tiny reseek, and one re-apply on TrackGroup refresh. Auto restores 720 -> 1080 -> best-below-1080. Resolver candidate ranking is unchanged.

## Other implemented architecture
- Persistent `VideoTabStore`: QUEUED/RESOLVING/READY/NEEDS_ATTENTION/ERROR; saved position/play intent/resolved JSON.
- `ForegroundPlaybackGuardProvider`: playback stops when app is not foreground; background preparation may continue.
- `BrowserAssistEnhancerProvider`: clean Opening UI and conservative consent.
- `PendingBrowserTabBridgeProvider`: browser completion updates same tab.
- `PlayerRecoveryProvider`: bounded ordinary network retry only.
- Settings/About/adaptive icon implemented.
- Release signing infrastructure prepared but activation deferred; never commit permanent key.

## CI / QA target
- #109 compiled but device QA found regressions above.
- **#124 PASS** on app-code commit `ab251902cf15cb9615abe242ca954bee64ddec2c`.
- #124 APK SHA-256: `04d3cb806bfca5b10db19bfab85e51cb0053e73f5c7268bcb4746454b401624a`.

Focused QA #124 first: share labels/order, staying in Vivaldi, real background prep especially HH, no background playback, actual manual adaptive quality switching. Then resume remaining unreported persistence/background-stop/recovery/tab-close gates.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
