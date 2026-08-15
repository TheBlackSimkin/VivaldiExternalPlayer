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
Private virtual display was created, but Android denied launching the first normal app Activity onto it (`VIRTUAL_PREP_LAUNCH_FAILED`). Do not retry an Activity launch there and do not request privileged `ACTIVITY_EMBEDDING` or system permissions. A future non-Activity private-display window would be a distinct architecture and is only a fallback if the current focus experiment fails.

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

## Build #225 — authoritative device QA result
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

### Post-CI code path
Committed #225 path correctly started preparation at BG share time:
- `BackgroundShareActivityV2.onCreate()` created pending tab, marked preparation requested, started foreground lease and directly launched preparation Activity;
- dashboard/card open was not involved;
- no `moveTaskToBack()` or `launchDisplayId` in normal path;
- preparation Activity `onCreate()` created/configured WebView, started direct resolver and scheduled 12s browser fallback;
- alpha was exactly `0.0f` with `FLAG_NOT_TOUCHABLE`;
- ordinary BG prep created no PlayerActivity/ExoPlayer.

### #225 user QA — PARTIAL / FAIL on two blockers
Authoritative observations:
- Vivaldi remained unresponsive for about **7 seconds** after BG share: **FAIL**. Alpha=0 did not solve input ownership.
- no visible preparation/video-frame flash: **PASS**. Do not request/describe PH imagery.
- refreshed icon: **PASS**; user liked it.
- video loaded and played: **PASS** for ordinary playback.
- automatic/default playback was **1080p while 720p was available**; quality UI exposed `Auto - 720p`: **strict 720-first FAIL confirmed**.
- UI language change worked: **PASS** for selector/change. Persistence-after-reopen was not separately stated.
- Recently closed was not reported and remains pending.
- the text pasted under `Log:` was the old project/test brief, not an exported operations log. No #225 operations-log telemetry was actually received.
- exact READY-before-open timing was not stated in this report, so the successor build rechecks it while testing the focus change.

Interpretation: **alpha=0 visibility fix PASS; focusable transparent Activity still blocks the browser.**

## Current post-#225 BG change
The next focused architecture keeps the successful lifecycle but removes input focus from the invisible window:
- alpha stays `0.0f`;
- keep `FLAG_NOT_TOUCHABLE`;
- add `FLAG_NOT_FOCUSABLE`;
- explicitly add `FLAG_NOT_TOUCH_MODAL` (NOT_FOCUSABLE already implies it);
- preparation Activity stays on default display and RESUMED;
- direct + automatic browser fallback still start from BG-share time;
- no `moveTaskToBack()`, virtual-display Activity launch, normal Worker fallback, PlayerActivity, or ExoPlayer;
- `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED` now logs alpha + notTouchable + notFocusable + notTouchModal.

Reason: #205 says STOPPED is unstable; #225 says fully transparent but focusable still blocks Vivaldi. This tests whether Activity lifecycle and user-input focus can be separated on the real phone.

Important risk: WebView/browser discovery might behave differently without window focus. CI cannot prove this. The focused device test must prove both immediate Vivaldi input and automatic READY-before-open.

Fallback only if this fails: investigate foreground-service-owned WebView/window on the app-private virtual display, without launching an Activity there. That is distinct from #212.

## Quality status
- #215 automatic PH result reported 720p once.
- #225 definitively reconfirmed automatic 1080p despite available 720p.
- Manual 240p works.
- Manual 480p still needs repair/verification.
- Do not mix quality repair into the focused BG input build. Once BG touch + READY-before-open are clean, fix strict 720-first and then manual 480.
- No HH testing yet.

## Other backlog
- Recently closed is implemented but explicit device QA is still pending.
- Secure `Report log on GitHub` shortcut is approved but comes after current QA. Keep full Android Share log. Never embed GitHub PAT/token/client secret in the APK.

## Current priority
1. Commit the non-focusable transparent-window change with both state files.
2. CI must pass before designating the next APK.
3. Post-CI inspect committed `main`: tab creation + preparation request + foreground lease + preparation Activity + direct resolver/browser fallback must still begin from BG share, not dashboard/tab open.
4. Confirm committed window flags are alpha=0.0 + NOT_TOUCHABLE + NOT_FOCUSABLE + NOT_TOUCH_MODAL and no BG PlayerActivity/ExoPlayer exists.
5. Next QA is ONE PH link: immediate Vivaldi touch/scroll, no flash, READY before ExternalPlayer/tab open, export operations log.
6. If clean, test 2–3 PH links for browser-slot serialization.
7. Then fix/verify strict 720 preference + manual 480 switching.
8. No HH until PH BG/quality blockers are cleared.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
