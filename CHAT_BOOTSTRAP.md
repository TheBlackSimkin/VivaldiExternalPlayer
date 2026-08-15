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
Private virtual display was created, but Android denied launching the first normal app Activity onto it (`VIRTUAL_PREP_LAUNCH_FAILED`). Do not retry an Activity launch there and do not request privileged `ACTIVITY_EMBEDDING` or system permissions. A future non-Activity private-display window is a distinct fallback architecture only if the current focus experiment fails.

### #215 — first core automatic PH BG success
App-code commit `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`; CI #215 PASS.

One-PH QA:
- automatic preparation completed **before ExternalPlayer/card open**;
- dashboard showed `READY +9s`;
- one automatic result reported 720p;
- preparation Activity RESUMED and automatic browser fallback worked after direct miss;
- Vivaldi touch/scroll blocked ~3–5s;
- brief ~0.5s visible preparation/frame flash. Never inspect/describe its content.

Interpretation: **core automatic BG preparation PASS; overlay UX FAIL**.

## Build #225 — authoritative device result
App-code bundle `2525520b3b6c140db3818337456569f59725d584`; corrected head `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890`; CI #225 PASS, run `31850648050`.

#225 included alpha `0.01 -> 0.0`, persisted Recently closed, explicit app language selector, and refreshed icon.

User QA:
- Vivaldi remained unresponsive about **7 seconds** after BG share: **FAIL**.
- no visible flash: **PASS**.
- refreshed icon: **PASS**, user liked it.
- video loaded/played: **PASS**.
- automatic/default playback was **1080p while 720p was available** and UI showed `Auto - 720p`: **strict 720-first FAIL confirmed**.
- UI language change worked: **PASS**; reopen persistence not separately reported.
- Recently closed not reported yet.
- pasted `Log:` text was an older project brief, not exported operations telemetry; no #225 operations log was received.
- exact READY-before-open timing was not stated, so successor QA must verify it again.

Interpretation: alpha=0 solved visibility, but the transparent **focusable** Activity still blocked Vivaldi.

## Build #227 — CURRENT focused QA target
App-code commit `f53cfcdce45e6e1d982bfee97b042195969134cb` (`fix: release BG input focus after share`).

### Architecture
Keep the known-successful RESUMED lifecycle but remove user-input focus from the invisible preparation window:
- alpha `0.0f`;
- `FLAG_NOT_TOUCHABLE`;
- **`FLAG_NOT_FOCUSABLE`**;
- explicit `FLAG_NOT_TOUCH_MODAL`;
- preparation Activity remains on default display and RESUMED;
- direct resolver + automatic browser fallback still begin at BG-share time;
- no `moveTaskToBack()`, no virtual-display Activity launch, no ordinary Worker fallback, no PlayerActivity/ExoPlayer in BG preparation;
- `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED` logs alpha + notTouchable + notFocusable + notTouchModal.

Reason: #205 says STOPPED is unstable; #225 says transparent-but-focusable still steals/blocks input. #227 tests separation of Activity lifecycle from input focus.

Risk: WebView/browser discovery may behave differently without window focus. Real device must prove **both** immediate Vivaldi interaction and READY-before-open.

Fallback only if #227 fails automatic preparation: investigate a foreground-service-owned non-Activity WebView/window on the app-private virtual display. This is distinct from #212 Activity launch and does not require privileged `ACTIVITY_EMBEDDING`.

### CI #227 — PASS
- run ID `31852454518`;
- built head `f53cfcdce45e6e1d982bfee97b042195969134cb`;
- debug artifact `9237954029`, name `VivaldiExternalPlayer-debug-apk`;
- ZIP size `26,005,116` bytes;
- ZIP SHA-256 `68956445798771e9f98ea45be21e5471577f208f7074ce13f749bd8bbc0a76aa`;
- extracted APK size `35,511,734` bytes;
- APK SHA-256 `853cc2da965de5c606e2f1cd083f120787d8d55534809c84bdf77a3ccc560170`.

### Post-CI code-path inspection — PASS
- `BackgroundShareActivityV2.onCreate()` creates pending tab, calls `markPreparationRequested()`, acquires foreground lease and directly starts preparation Activity at BG-share time; dashboard/card/tab opening is not involved.
- `BackgroundVirtualPreparationActivity.onCreate()` marks host/RESOLVING, creates/configures full-size WebView, calls `attemptDirectFirst()` immediately and schedules 12s browser fallback.
- `TabbedPlayerApplication` applies alpha 0.0 + NOT_TOUCHABLE + NOT_FOCUSABLE + NOT_TOUCH_MODAL.
- normal BG path has no `moveTaskToBack()`/`launchDisplayId` and does not create PlayerActivity/ExoPlayer.

#227 has therefore passed all pre-device-test gates and is designated for **ONE PH focused QA only**.

## Quality status
- #215 reported 720p once.
- #225 definitively reconfirmed automatic 1080p despite available 720p.
- Manual 240p works.
- Manual 480p still needs repair/verification.
- Do **not** mix quality repair into #227. Once BG touch + READY-before-open are clean, fix strict 720-first then manual 480.
- No HH yet.

## Other backlog
- Recently closed is implemented but explicit device QA remains pending.
- Secure `Report log on GitHub` shortcut comes later; never embed a GitHub PAT/token/client secret.

## Current priority
1. Test #227 with ONE PH link.
2. Immediately after BG share, Vivaldi should remain touch/scroll responsive.
3. Do not open ExternalPlayer/tab during preparation; later verify it already reached READY before opening.
4. Verify no visible flash.
5. Export operations log even on PASS; expected flag line: `alpha=0.0 notTouchable=true notFocusable=true notTouchModal=true`, plus RESUMED/direct/browser/READY anchors.
6. If clean, test 2–3 PH shares for browser-slot serialization.
7. Then fix strict 720 preference + manual 480.
8. Later verify Recently closed and language persistence.
9. No HH until PH BG/quality blockers clear.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
