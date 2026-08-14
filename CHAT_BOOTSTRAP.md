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
App-code commit `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`.
GitHub Actions #215 PASS; APK SHA-256 `7aea335b8a2f941898ec5737804a89ddb719deb301e156f555408da15d57133e`.

Architecture: BG share creates the tab and foreground lease immediately, then launches the preparation Activity on the default display. That Activity stays RESUMED, owns a full WebView, starts direct resolution and automatic browser fallback without MainActivity/card involvement.

One-PH device QA result:
- automatic preparation **worked before ExternalPlayer/card open**;
- dashboard later showed `READY +9s`;
- automatic actual quality reported 720p in this run;
- log showed `PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED`, direct miss, then automatic browser request;
- however Vivaldi touch/scroll was blocked for ~3–5 seconds after share;
- a brief ~0.5s visible preparation/video-frame flash appeared. Never inspect or describe that frame/content.

Authoritative log anchors:
- 19:16:17.962 `BG_SHARE_OVERLAY_HANDOFF_STARTED`;
- 19:16:17.984 `PRIMARY_OVERLAY_PREP_LAUNCH_REQUESTED`;
- 19:16:18.087 preparation Activity created on display 0 with `alpha=0.01`;
- 19:16:18.434 `DIRECT_STARTED`;
- 19:16:18.449 Activity RESUMED;
- 19:16:20.138 direct finished;
- 19:16:20.140 browser requested;
- later dashboard proved READY around +9s.

Interpretation: **core BG preparation PASS; transparent-overlay UX FAIL**.

## Post-#215 app-code bundle
App-code commit `2525520b3b6c140db3818337456569f59725d584` contains:
- alpha `0.01 -> 0.0` while keeping the successful RESUMED + NOT_TOUCHABLE preparation model;
- persisted Recently closed history (max 12) + restore UI;
- System default / English / Español app-language selector;
- refreshed white-E/purple icon with stronger purple and less-boxy geometry.

Do not reintroduce `moveTaskToBack()`, virtual-display Activity launch, normal BG Worker fallback, or PlayerActivity/ExoPlayer during preparation.

### CI #224 — FAIL, no APK
GitHub Actions #224, run ID `31850417827`, failed at `mergeDebugResources` before Kotlin compilation and before APK upload.

Cause: five Spanish strings were accidentally duplicated between `values-es/next_build_strings.xml` and existing `values-es/ui_refresh_strings.xml`: `home_brand`, `home_tagline`, `home_tabs_section`, `home_manual_section`, `tab_thumbnail_pending`.

This is only a resource-definition error. No #224 APK exists/is a QA target. Correct by deleting only the duplicate definitions from `next_build_strings.xml` and retaining the existing translations in `ui_refresh_strings.xml`, then re-run CI.

## Required next-build UI bundle — user explicitly said do not postpone
1. **Persistent tab clarity + real Recently closed restore**
   - open tabs restore automatically after app/process restart;
   - Settings says this clearly;
   - rename `Clear saved tabs` to `Clear all tabs`;
   - closing/swiping one tab archives a bounded snapshot;
   - Settings exposes `Recently closed (N)` and lets user restore one;
   - preserve source/resolved payload, position and quality state;
   - max 12 entries;
   - bulk Clear all does not fill history.

2. **App language selector**
   - System default;
   - English;
   - Español;
   - AndroidX `AppCompatDelegate.setApplicationLocales`;
   - declare `en`/`es` locale config;
   - persist compatibly on API 24–32 and synchronize with Android 13+ where supported.

3. **Icon refresh**
   - preserve white-E/purple identity;
   - less boxy/more refined;
   - purple more prominent;
   - affect launcher and dashboard header.

Secure GitHub log-report shortcut is approved but deferred until after these three. Never embed a GitHub PAT/token/client secret in the APK.

## Quality status
- #215 one-PH automatic result reported 720p.
- Earlier runs chose 1080 despite 720 existing, so do not globally declare that fixed from one sample.
- Manual 240p works.
- Manual 480p still needs repair/verification.
- No HH testing yet.

## Current priority
1. Commit the #224 duplicate-resource correction with both state files updated.
2. CI must pass before designating the successor APK.
3. Post-CI inspect committed share-time path: tab + lease + preparation Activity + direct resolver + browser fallback must still start at BG share time, not dashboard/card open.
4. Update both state files with successful build/run/artifact/hash.
5. Next QA: ONE PH link. Verify automatic READY before app/card open, Vivaldi touch works immediately, no visible flash, log shows RESUMED with alpha=0.0, language selector works/persists, Recently closed restore works, and new icon is visible.
6. If clean, test 2–3 PH links for browser-slot serialization.
7. Then fix/verify 720 policy + manual 480 switching.
8. No HH until PH blockers are cleared.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
