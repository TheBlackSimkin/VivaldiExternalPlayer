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

### #212 — private virtual-display Activity experiment, authoritative FAIL
App-code head `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`; CI #212 PASS. Device result: immediate `VIRTUAL_PREP_LAUNCH_FAILED +0s`. The private display itself was created, but Android denied launching the first normal app Activity onto it. Do not request privileged/system `ACTIVITY_EMBEDDING` permissions and do not retry **Activity launch** onto that display.

This does not prohibit a future non-Activity private-display window owned by the foreground service if the default-display focus experiment also fails; that is a different Android window architecture and would not launch an Activity on the private display.

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

Conclusion: **core automatic BG preparation PASS; alpha-0.01 overlay UX FAIL**. One 720p result did not globally close the earlier 1080-vs-720 regression.

## Build #225 — device QA now authoritative
Post-#215 app-code bundle was introduced in `2525520b3b6c140db3818337456569f59725d584`; corrected build head is `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890`.

### Changes included
1. **BG touch/flash correction attempt**
   - preparation window alpha changed from `0.01f` to exactly `0.0f`;
   - kept `FLAG_NOT_TOUCHABLE`;
   - intentionally kept focus enabled in #225;
   - kept preparation Activity RESUMED;
   - no `moveTaskToBack()`;
   - no virtual-display Activity launch;
   - no normal BG Worker fallback;
   - no PlayerActivity/ExoPlayer during preparation.

2. **Persistent tabs clarity + real Recently closed**
   - open tabs persist automatically across app/process restart;
   - Settings explains automatic restoration;
   - `Clear saved tabs` became `Clear all tabs` / `Borrar todas las pestañas`;
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

### #225 one-PH device QA — PARTIAL, BG input FAIL + quality FAIL
Authoritative user report:
- **Vivaldi remained unresponsive for about 7 seconds after BG share: FAIL.** This is worse than the ~3–5s block reported on #215 and proves exact alpha 0.0 alone did not solve input ownership.
- **No visible preparation/video-frame flash this time: PASS.** The alpha=0.0 compositor correction worked for visibility.
- refreshed icon: PASS; user explicitly liked the new icon.
- video loaded and played correctly: PASS for ordinary playback of this test result.
- default/automatic playback was **1080p even though 720p was available** in the quality choices, and the UI exposed `Auto - 720p`: **strict 720-first quality policy FAIL, now reconfirmed.**
- app language change worked correctly in the reported test: PASS for selector/change behavior. Persistence-after-reopen was not separately described in this report.
- Recently closed behavior was not reported in this test result and remains pending device QA.
- the text pasted under `Log:` was the older project/test brief, not the exported operations log. Do not treat it as telemetry. No #225 operations-log anchors were received.
- the report did not include an exact READY-before-open timing line, so the successor build must re-verify that automatic preparation still finishes before ExternalPlayer/the tab is opened while testing the new input-focus architecture.

Interpretation: #225 proves **visibility PASS, input-focus FAIL**. Do not return to alpha 0.01. The next BG change must address focus ownership while preserving the successful RESUMED preparation lifecycle.

## Post-#225 BG input-focus architecture change
The next code change removes input focus from the transparent preparation window while keeping the Activity top/RESUMED:
- keep `alpha = 0.0f`;
- keep `FLAG_NOT_TOUCHABLE`;
- add `FLAG_NOT_FOCUSABLE`;
- explicitly add `FLAG_NOT_TOUCH_MODAL` for maintenance clarity (Android already implies it from NOT_FOCUSABLE);
- keep the preparation Activity on the default display and RESUMED;
- keep direct resolver + automatic browser fallback starting at BG-share time;
- keep no `moveTaskToBack()`, no virtual-display Activity launch, no ordinary Worker fallback, and no PlayerActivity/ExoPlayer during preparation;
- log the committed alpha/touch/focus/modal flags in `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED` so device QA can prove which window architecture actually ran.

Why: #205 proved STOPPED is unstable; #225 proved fully transparent but still focusable is not good enough. This is the smallest architecture change which separates Activity lifecycle from user-input focus.

Risk to test: some browser pages may react differently when their WebView window lacks focus. Therefore this build is not successful merely because it compiles. Real-device QA must prove **both** immediate Vivaldi input and automatic READY-before-open.

Contingency if this focus-free Activity cannot prepare: investigate a foreground-service-owned, non-Activity window/WebView on the already-supported app-private virtual display. That would be distinct from the forbidden #212 Activity launch and would not require privileged `ACTIVITY_EMBEDDING`. Do not implement that unless the focused non-focusable-Activity QA fails.

## Secure GitHub log reporting
Approved idea but deferred until after the current required UX/lifecycle QA. Keep ordinary Share operations log. Never embed a PAT/repository token/OAuth client secret in the APK. Preferred future first version: open a pre-filled GitHub new-issue page with bounded sanitized log text for user review/submission.

## Quality status
Protected policy remains exact 720 -> otherwise 1080 -> otherwise best below 1080.

Current findings:
- #215 one-PH automatic result reported actual 720p;
- **#225 reconfirmed the regression:** automatic/default actual result was 1080p while 720p was available, and the quality UI exposed `Auto - 720p`;
- therefore strict 720-first is definitively still broken and needs a code repair after the BG input architecture is clean;
- manual 240p works;
- manual 480p still needs repair/verification.

Do not mix the 720/480 repair into the focused BG input build. Once BG touch + READY-before-open are clean, fix browser initial-source/track selection so the actual initial playback follows 720-first, then repair/verify manual 480 before HH regression testing.

## Current UI / backlog
- long-press tab move/reorder WORKS;
- closing tabs WORKS;
- resume position WORKS;
- tested Back flow after manual Browser Step WORKS;
- BG absence from Android Recents PASS/preferred;
- exportable operations log PASS/useful;
- refreshed icon PASS on #225;
- language selector/change PASS on #225; persistence after reopen not separately reported in the latest result;
- Recently closed feature is implemented but still awaits explicit device result;
- secure GitHub log-report shortcut comes later.

## Current development priority
1. Commit the post-#225 non-focusable transparent-window architecture and both state files together.
2. Ensure GitHub Actions CI passes before designating the successor APK.
3. After CI, inspect committed `main` to verify preparation is still scheduled/started at BG-share time, not dashboard/card/tab-open time.
4. Confirm committed window flags are alpha=0.0 + NOT_TOUCHABLE + NOT_FOCUSABLE + NOT_TOUCH_MODAL, while Activity remains RESUMED and no PlayerActivity/ExoPlayer is started by BG prep.
5. Designate exactly one focused successor APK for **ONE PH link**.
6. Device QA must check: Vivaldi touch/scroll works immediately; no flash; automatic READY happens before ExternalPlayer/tab open; operations log shows RESUMED plus the new non-focusable flags.
7. If that is clean, test 2–3 PH BG shares for browser-slot serialization.
8. Then fix/verify strict 720 preference and manual 480 switching.
9. Get the pending Recently closed device result during a later UI regression pass; do not distract the focused BG test with it.
10. No HH testing until PH BG/quality blockers are cleared.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
