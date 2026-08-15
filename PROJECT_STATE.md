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

A future foreground-service-owned, non-Activity private-display window/WebView would be a distinct architecture and remains only a contingency if the current default-display focus experiment fails.

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

## Build #225 — authoritative device QA
Post-#215 app-code bundle was introduced in `2525520b3b6c140db3818337456569f59725d584`; corrected build head `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890`.

Included:
- preparation window alpha `0.01f -> 0.0f`, retaining `FLAG_NOT_TOUCHABLE`, focus, RESUMED lifecycle, no `moveTaskToBack()`, no virtual-display Activity launch, no ordinary Worker fallback, no PlayerActivity/ExoPlayer during preparation;
- persisted Recently closed history (max 12) + restore/clear UI;
- clearer persistent-tab wording and `Clear all tabs`;
- explicit app language selector System default / English / Español with AndroidX app locales;
- refreshed white-E/purple launcher/dashboard icon.

CI:
- #224 FAIL, run `31850417827`: duplicate Spanish resources; no QA APK.
- correction `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890` removed only duplicates.
- **#225 PASS**, run `31850648050`.
- artifact `9237424502`; ZIP 26,005,544 bytes; digest `sha256:3f87ba2a4cdffbc248534ce0057708a29c580e5cd0ec1894d7e741e44764af34`.
- extracted APK 35,512,362 bytes; SHA-256 `ff84bbc469efc1ea62bb6b5c5abefb03cec3c45e33e7f4ff8ed56085c51e60f8`.

### #225 post-CI code-path inspection — PASS
- share `onCreate()` created pending tab, marked preparation requested, started foreground lease and directly started preparation Activity at BG-share time;
- dashboard/card opening was not involved;
- no `moveTaskToBack()`/`launchDisplayId` in normal path;
- preparation Activity `onCreate()` created/configured WebView, called `attemptDirectFirst()` and scheduled 12s browser fallback;
- alpha exactly `0.0f` + `FLAG_NOT_TOUCHABLE`;
- no BG PlayerActivity/ExoPlayer.

### #225 one-PH user QA — PARTIAL / two blockers
- Vivaldi remained unresponsive ~7s after BG share: **FAIL**. Alpha 0 alone did not solve input ownership.
- no visible preparation/video-frame flash: **PASS**.
- icon: **PASS**; user liked it.
- video loaded and played: **PASS**.
- automatic/default playback was **1080p although 720p was available**, and menu exposed `Auto - 720p`: **strict 720-first FAIL reconfirmed**.
- UI language change: **PASS**; persistence-after-reopen not separately reported.
- Recently closed not reported; remains pending.
- text pasted under `Log:` was an older project brief, not exported operations telemetry. No #225 operations-log anchors were actually received.
- exact READY-before-open timing was not stated in this report, so successor QA must re-verify it.

Interpretation: **visibility PASS, input-focus FAIL**. Do not return to alpha 0.01.

## Build #227 — current focused QA target
App-code commit: `f53cfcdce45e6e1d982bfee97b042195969134cb` (`fix: release BG input focus after share`).

### Architecture change
This is deliberately a narrow lifecycle/input experiment, not a quality rewrite:
- preparation Activity still launches on the default display and remains top/RESUMED;
- alpha remains exactly `0.0f`;
- keep `FLAG_NOT_TOUCHABLE`;
- **add `FLAG_NOT_FOCUSABLE`** so the invisible preparation window does not own input focus;
- explicitly add `FLAG_NOT_TOUCH_MODAL` for maintenance clarity (`NOT_FOCUSABLE` already implies it);
- keep direct resolver + automatic browser fallback beginning at BG-share time;
- keep no `moveTaskToBack()`, no virtual-display Activity launch, no ordinary Worker fallback, and no PlayerActivity/ExoPlayer during BG preparation;
- `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED` now logs `alpha`, `notTouchable`, `notFocusable`, and `notTouchModal` so device QA can prove the exact window flags.

Why: #205 proved STOPPED is unstable; #225 proved fully transparent but focusable still blocks Vivaldi. #227 tests whether the WebView host can stay RESUMED while user input remains with Vivaldi.

Risk: some browser pages may behave differently when the preparation WebView window lacks focus. Therefore CI success alone is not a device PASS. The one-PH test must prove **both immediate Vivaldi input and automatic READY-before-open**.

### #227 CI — PASS
- GitHub Actions build **#227** PASS.
- Run ID: `31852454518`.
- App-code/head SHA built: `f53cfcdce45e6e1d982bfee97b042195969134cb`.
- Debug artifact ID: `9237954029` (`VivaldiExternalPlayer-debug-apk`).
- Artifact ZIP size: `26,005,116` bytes.
- Artifact ZIP digest / locally verified ZIP SHA-256: `68956445798771e9f98ea45be21e5471577f208f7074ce13f749bd8bbc0a76aa`.
- Extracted debug APK size: `35,511,734` bytes.
- Extracted APK SHA-256: `853cc2da965de5c606e2f1cd083f120787d8d55534809c84bdf77a3ccc560170`.

### #227 post-CI committed-code inspection — PASS
Committed app-code path was re-inspected after CI:
- `BackgroundShareActivityV2.onCreate()` validates the URL, creates the persistent pending tab immediately, calls `markPreparationRequested()`, records the share handoff, acquires the foreground lease, and directly starts `BackgroundVirtualPreparationActivity`; no dashboard/card/tab click is involved.
- `BackgroundVirtualPreparationActivity.onCreate()` marks the preparation host and `RESOLVING`, creates/configures the full-size WebView, calls `attemptDirectFirst()` immediately, then schedules the 12s direct/browser fallback.
- `TabbedPlayerApplication.configureTransparentPreparationWindow()` applies alpha `0.0f` plus `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`.
- the BG share path still has no `moveTaskToBack()` or `launchDisplayId` and does not instantiate PlayerActivity/ExoPlayer.

Therefore #227 satisfies the pre-device-test gates: architecture changed, CI passed, BG-share-time scheduling was inspected, and both state files are updated. It is designated for **ONE PH** focused device QA only.

Contingency if #227 makes Vivaldi responsive but breaks automatic browser preparation: investigate a foreground-service-owned non-Activity WebView/window on the app-private virtual display. That would be distinct from the forbidden #212 Activity launch and would not require privileged `ACTIVITY_EMBEDDING`.

## Secure GitHub log reporting
Approved but deferred until after current lifecycle/quality blockers. Keep ordinary Share operations log. Never embed PAT/repository token/OAuth client secret in APK. Preferred future first version: open a pre-filled GitHub new-issue page with bounded sanitized log text for user review/submission.

## Quality status
Protected policy remains exact 720 -> otherwise 1080 -> otherwise best below 1080.

Current findings:
- #215 one-PH automatic result reported 720p once;
- #225 reconfirmed automatic/default 1080p while 720p was available and UI exposed `Auto - 720p`;
- strict 720-first is therefore definitively still broken;
- manual 240p works;
- manual 480p still needs repair/verification.

Do **not** mix the 720/480 repair into #227. Once BG touch + READY-before-open are clean, fix browser initial-source/track selection so actual initial playback follows 720-first, then repair/verify manual 480 before HH regression testing.

## Current UI / backlog
- long-press tab move/reorder WORKS;
- closing tabs WORKS;
- resume position WORKS;
- tested Back flow after manual Browser Step WORKS;
- BG absence from Android Recents PASS/preferred;
- exportable operations log PASS/useful;
- refreshed icon PASS on #225;
- language selector/change PASS on #225; persistence after reopen not separately reported;
- Recently closed is implemented but awaits explicit device result;
- secure GitHub log-report shortcut later.

## Current development priority
1. Device-test build #227 with **ONE PH link only**.
2. Immediately after BG share, verify Vivaldi touch/scroll is responsive instead of blocked ~7s.
3. Do not open ExternalPlayer/the individual tab during preparation; later verify the tab already reached READY before opening it.
4. Verify there is still no visible black/preparation/frame flash.
5. Export the operations log even on PASS. Expected anchors include `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED` with `alpha=0.0 notTouchable=true notFocusable=true notTouchModal=true`, Activity RESUMED, direct/browser stages, and `BG_PREPARATION_READY` when successful.
6. If #227 is clean, test 2–3 PH shares for browser-slot serialization.
7. Then repair/verify strict 720 preference + manual 480 switching.
8. Later run the pending Recently closed/persistence UI regression check.
9. No HH until PH BG/quality blockers are cleared.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
