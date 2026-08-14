# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` before substantive work. Keep both state files current whenever QA, architecture, failures, priorities, or decisions change.

## Communication / safety
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Use connected GitHub tools directly. Source should contain abundant English comments.
- PH and HH are real technical playback targets. URLs/manifests/codecs/resolutions/request metadata/candidate ranking/states/errors/local titles are allowed.
- Never inspect/describe/classify PH/HH media content or thumbnail imagery and never ask the user to provide it.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials.
- Never add background playback or a second ExoPlayer session.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise best below 1080p. Preserve yt-dlp first/browser fallback; automatic best/manual fallback; adaptive/sibling quality handling; double-tap ±10s; seek preview; rotation; bilingual UI; 80 stored/20 manual candidate limits; first-seen HLS/DASH order; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no imagery-based resolver/ranking; one actual ExoPlayer playback session.

## BG history that matters
### #205
Operations log proved this phone destroys a preparation Activity almost immediately after it becomes STOPPED behind Vivaldi. Foreground service protects process lifetime, not a stopped Activity/WebView. Do not return to that architecture.

### #212
Private virtual display was created, but Android denied launching the first normal app Activity onto it (`VIRTUAL_PREP_LAUNCH_FAILED`). Do not retry with privileged `ACTIVITY_EMBEDDING` or system permissions.

### #215 — first core automatic PH BG success
App-code commit `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`; CI #215 PASS.

One-PH device QA:
- automatic preparation completed **before ExternalPlayer/card open**;
- dashboard showed `READY +9s`;
- automatic actual quality reported 720p in this run;
- Activity reached `PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED` and automatic browser fallback started after direct miss;
- Vivaldi touch/scroll was blocked ~3–5s after share;
- brief ~0.5s visible preparation/video-frame flash occurred. Never inspect/describe that content.

Interpretation: **core BG preparation PASS; alpha-0.01 overlay UX FAIL**.

## Build #225 — current focused QA target
App-code bundle commit `2525520b3b6c140db3818337456569f59725d584`; corrected build head `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890`.

Included changes:
- preparation overlay alpha `0.01 -> 0.0`, retaining RESUMED lifecycle, focus and `FLAG_NOT_TOUCHABLE`;
- real persisted Recently closed history (max 12) with Settings restore/clear UI;
- clearer wording that open tabs restore automatically and `Clear all tabs` replaces misleading `Clear saved tabs`;
- app-language selector: System default / English / Español using AndroidX app locales;
- `en`/`es` locale config + compat locale storage;
- refreshed launcher/dashboard icon preserving white-E/purple identity, with more prominent purple and less-boxy E.

Do not reintroduce `moveTaskToBack()`, virtual-display Activity launch, normal BG Worker fallback, or PlayerActivity/ExoPlayer during preparation.

### CI
- #224, run `31850417827`: FAIL at `mergeDebugResources` because five Spanish `home_*`/thumbnail strings were duplicated between two resource files. No QA APK.
- Fix head `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890` removed only those duplicate Spanish definitions.
- **#225 PASS**, run ID `31850648050`.
- Debug artifact ID `9237424502`.
- Artifact ZIP size `26,005,544` bytes; digest `sha256:3f87ba2a4cdffbc248534ce0057708a29c580e5cd0ec1894d7e741e44764af34`.
- Extracted APK size `35,512,362` bytes.
- APK SHA-256 `ff84bbc469efc1ea62bb6b5c5abefb03cec3c45e33e7f4ff8ed56085c51e60f8`.

### Post-CI code-path inspection — PASS
Committed main confirms:
- `BackgroundShareActivityV2.onCreate()` creates pending tab, marks preparation requested, starts foreground lease and directly launches preparation Activity at share time;
- dashboard/card open is not involved;
- no `moveTaskToBack()` or `launchDisplayId` in normal path;
- preparation Activity `onCreate()` creates/configures WebView, starts direct resolver and schedules the 12s browser fallback;
- committed overlay alpha is exactly `0.0f` with `FLAG_NOT_TOUCHABLE`;
- ordinary BG prep does not create PlayerActivity/ExoPlayer.

#225 is the designated one-PH QA build.

## Quality status
- #215 automatic PH result reported 720p, but earlier runs chose 1080 despite 720 existing; do not globally mark fixed yet.
- Manual 240p works.
- Manual 480p still needs repair/verification.
- No HH testing yet.

## Other backlog
Secure `Report log on GitHub` shortcut is approved but comes after current QA. Keep full Android Share log. Never embed GitHub PAT/token/client secret in the APK.

## Current priority
1. Test #225 with ONE PH link.
2. Verify Vivaldi remains visible and touch/scroll works immediately after BG share; no visible preparation/frame flash.
3. Verify tab is READY before ExternalPlayer/card open.
4. Export operations log even on PASS; expect RESUMED + `alpha=0.0`.
5. Verify new icon visibly changed.
6. Verify language selector changes UI and persists after leaving/reopening.
7. Verify Recently closed by closing one test tab, seeing count, restoring it, and confirming it returns.
8. If clean, test 2–3 PH shares for browser-slot serialization.
9. Then fix/verify 720 policy + manual 480 switching.
10. No HH until PH blockers are cleared.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
