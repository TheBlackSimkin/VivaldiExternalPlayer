# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA results, failures, decisions, or priorities change.

## Working rules
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Source should contain abundant English comments.
- Use connected GitHub tools directly whenever possible.
- PH and HH are real technical playback targets tested by the user. URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed.
- Do **not** inspect, describe, classify, summarize, or request PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected playback baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p; >1080 only rare fallback.

Preserve Vivaldi share targets; yt-dlp first/browser fallback; automatic best/manual fallback; video+audio; adaptive/sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; 80 stored/20 manual candidate limits; first-seen HLS/DASH ordering; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no imagery-based resolver/ranking; exactly one actual ExoPlayer playback session.

Previously verified core baseline includes Bitmovin/PH/HH playback, build #62 follow-up, build #74 clean loading, and #109 install/About/Settings/no-background-playback/local browser title. Do not repeat old PASS items without a regression reason.

Permanent release signing remains deferred. Debug GitHub Actions APKs are the QA path; never commit a permanent signing key.

## BG preparation history
### #192
Self-owned share Activity proved preparation could begin before tab/card clicks, but three PH tabs took ~244–270s and ended at Browser Step. Manual Browser Step worked ~5–10s.

### #202
V2 added a 12s direct budget, bounded yt-dlp, normal-size hidden WebView, Service Worker + DOM/Performance/page-config discovery and serialized browser ownership. PH still took minutes and ended `NEEDS_ATTENTION`; manual Browser Step worked ~5–7s. Recents absence PASS/preferred.

Quality regressions documented from this stage:
- manual 240p works;
- manual 480p does not;
- Auto could choose 1080 despite 720 existing;
- launcher icon was still unchanged.

### #205 — decisive lifecycle log
Foreground keep-alive protected the process but not the stopped Activity. Device log showed Activity STOPPED at 16:42:01.422, destroyed-host recovery ~107ms later, `WORKER_ENQUEUED` at 16:42:01.569, Activity DESTROYED at 16:42:01.599.

Decision: do not put the WebView Activity behind Vivaldi and depend on Android preserving a STOPPED Activity. Exportable operations log is a PASS/useful feature.

### #212 — private virtual-display experiment, authoritative FAIL
App-code head `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`; CI #212 PASS. Device result: immediate `VIRTUAL_PREP_LAUNCH_FAILED +0s`. The private display itself was created, but Android denied launching the first normal app Activity onto it. Do not request privileged/system `ACTIVITY_EMBEDDING` permissions and do not retry this architecture.

## Build #215 — first real automatic PH BG completion
App-code commit `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`; GitHub Actions #215 PASS; APK SHA-256 `7aea335b8a2f941898ec5737804a89ddb719deb301e156f555408da15d57133e`.

Normal path:
`BG share -> create tab immediately -> foreground lease -> launch preparation Activity on DEFAULT display -> keep Activity RESUMED with transparent/NOT_TOUCHABLE window -> direct/browser resolver -> READY/ERROR/NEEDS_ATTENTION`.

### #215 one-PH device QA — core BG PASS, overlay UX FAIL
Authoritative result:
- automatic preparation completed before ExternalPlayer/card open;
- dashboard showed `READY +9s`;
- automatic actual quality reported 720p in this run;
- no card click was needed to start preparation;
- Vivaldi touch/scroll was blocked for ~3–5s after BG share;
- a brief ~0.5s preparation/video-frame flash was visible. Never inspect/describe that content.

Key log anchors:
- 19:16:17.962 `BG_SHARE_OVERLAY_HANDOFF_STARTED`;
- 19:16:17.984 `PRIMARY_OVERLAY_PREP_LAUNCH_REQUESTED`;
- 19:16:18.087 preparation Activity created on display 0 with `alpha=0.01`;
- 19:16:18.434 `DIRECT_STARTED`;
- 19:16:18.449 Activity RESUMED;
- 19:16:20.138 direct finished;
- 19:16:20.140 browser requested;
- dashboard later proved READY around +9s.

Conclusion: **core automatic BG preparation PASS; alpha-0.01 overlay UX FAIL**. One 720p result is encouraging but does not globally close the earlier 1080-vs-720 regression.

## Build #225 — current focused QA target
Post-#215 app-code bundle was introduced in `2525520b3b6c140db3818337456569f59725d584`; corrected build head is `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890`.

### Changes included
1. **BG touch/flash correction**
   - preparation window alpha changed from `0.01f` to exactly `0.0f`;
   - keeps `FLAG_NOT_TOUCHABLE`;
   - keeps focus enabled;
   - keeps preparation Activity RESUMED;
   - no `moveTaskToBack()`;
   - no virtual-display Activity launch;
   - no normal BG Worker fallback;
   - no PlayerActivity/ExoPlayer during preparation.

2. **Persistent tabs clarity + real Recently closed**
   - open tabs still persist automatically across app/process restart;
   - Settings now explains automatic restoration;
   - `Clear saved tabs` becomes `Clear all tabs` / `Borrar todas las pestañas`;
   - closing/swiping one tab stores a persisted snapshot in Recently closed;
   - Settings shows `Recently closed (N)` / `Cerradas recientemente (N)`;
   - user can restore one closed tab or clear the history;
   - history max 12 entries;
   - restore preserves source/resolved payload, position and quality/technical state;
   - bulk Clear all does not flood Recently closed.

3. **Explicit app language selector**
   - System default;
   - English;
   - Español;
   - uses AndroidX `AppCompatDelegate.setApplicationLocales`;
   - declares `en`/`es` locale config;
   - enables AndroidX compat locale storage for older supported Android versions.

4. **Icon refresh**
   - preserves white-E/purple identity;
   - larger/more prominent purple diamond;
   - less-boxy rounded compact white E;
   - adaptive launcher foreground and pre-adaptive fallback updated;
   - dashboard header uses the refreshed foreground drawable too.

### CI history for this bundle
- **#224 FAIL**, run ID `31850417827`: `mergeDebugResources` failed because five Spanish `home_*`/thumbnail strings were accidentally duplicated between `values-es/next_build_strings.xml` and existing `values-es/ui_refresh_strings.xml`. No #224 APK was designated.
- Fix commit `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890` removed only those duplicate definitions, retaining the existing translations.
- **#225 PASS**, run ID `31850648050`.
- Debug artifact ID: `9237424502`.
- Artifact ZIP size: `26,005,544` bytes.
- Artifact ZIP digest: `sha256:3f87ba2a4cdffbc248534ce0057708a29c580e5cd0ec1894d7e741e44764af34`.
- Extracted APK size: `35,512,362` bytes.
- Extracted APK SHA-256: `ff84bbc469efc1ea62bb6b5c5abefb03cec3c45e33e7f4ff8ed56085c51e60f8`.

### #225 post-CI code-path inspection — PASS
Committed `main` was re-inspected after CI:
- `BackgroundShareActivityV2.onCreate()` creates the pending tab at share time, marks preparation requested, starts the foreground lease and directly starts `BackgroundVirtualPreparationActivity`;
- the share handoff does not use dashboard/card opening, `moveTaskToBack()`, or `launchDisplayId`;
- `BackgroundVirtualPreparationActivity.onCreate()` marks host/resolving, creates/configures the full-size WebView, calls `attemptDirectFirst()` and schedules the 12s direct-browser fallback;
- `TabbedPlayerApplication` has committed `BG_PREPARATION_WINDOW_ALPHA = 0.0f` and applies it with `FLAG_NOT_TOUCHABLE`;
- no PlayerActivity/ExoPlayer is created by ordinary BG preparation.

Therefore #225 is the designated focused successor for one-PH device QA. CI proves compile/package integrity; real-device touch/visibility/lifecycle and UI behavior still require QA.

## Secure GitHub log reporting
Approved idea but deferred until after the current required UX/lifecycle QA. Keep ordinary Share operations log. Never embed a PAT/repository token/OAuth client secret in the APK. Preferred future first version: open a pre-filled GitHub new-issue page with bounded sanitized log text for user review/submission.

## Quality status
Protected policy remains exact 720 -> otherwise 1080 -> otherwise best below 1080.

Current findings:
- #215 one-PH automatic result reported actual 720p;
- earlier PH runs could initially choose 1080 despite 720 existing, so more than one result is required before marking fixed;
- manual 240 works;
- manual 480 still needs repair/verification.

Do not mix a speculative 480 rewrite into #225. After BG touch/visibility is clean, verify 720 policy and repair/verify 480 switching before HH regression testing.

## Current UI / backlog
- long-press tab move/reorder WORKS;
- closing tabs WORKS;
- resume position WORKS;
- tested Back flow after manual Browser Step WORKS;
- BG absence from Android Recents PASS/preferred;
- exportable operations log PASS/useful;
- Recently closed / language / icon changes are in #225 and await device QA;
- secure GitHub log-report shortcut comes later.

## Current development priority
1. Device-test build #225 with **ONE PH link first**.
2. Verify automatic READY still happens before ExternalPlayer/card open.
3. Immediately after BG share, verify Vivaldi touch/scroll works without the #215 3–5s block.
4. Verify there is no visible black/preparation/video-frame flash.
5. Export operations log even on PASS; expect preparation Activity RESUMED and `alpha=0.0`.
6. Verify refreshed icon is visibly changed.
7. Verify Settings language selector exists, changes UI language and persists after leaving/reopening the app.
8. Verify Recently closed: close one test tab, see Recently closed count, restore it, and confirm it returns.
9. If one-link #225 is clean, test 2–3 PH shares for browser-slot serialization.
10. Then fix/verify strict 720 preference + manual 480 switching.
11. No HH testing until PH BG/quality blockers are cleared.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
