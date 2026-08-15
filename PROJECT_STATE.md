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

Previously verified baseline includes Bitmovin/PH/HH playback, build #62 follow-up, #74 clean loading, and #109 install/About/Settings/no-background-playback/local browser title. Do not repeat old PASS items without a regression reason.

Permanent release signing remains deferred. Debug GitHub Actions APKs are the QA path; never commit a permanent signing key.

## BG preparation history
### #192 / #202
#192 proved preparation could begin before tab/card clicks, but three PH tabs took ~244–270s and ended at Browser Step; manual Browser Step worked ~5–10s.

#202 added a 12s direct budget, bounded yt-dlp, normal-size hidden WebView, Service Worker + DOM/Performance/page-config discovery and serialized browser ownership. PH still took minutes and ended `NEEDS_ATTENTION`; manual Browser Step worked ~5–7s. Quality regressions documented here: manual 240p works, manual 480p does not, Auto could choose 1080 despite 720 existing.

### #205 — decisive stopped-Activity failure
Foreground keep-alive protected the process but not a stopped Activity. Device log: Activity STOPPED 16:42:01.422; destroyed-host recovery ~107ms later; `WORKER_ENQUEUED` 16:42:01.569; Activity DESTROYED 16:42:01.599.

Decision: do not intentionally put the WebView Activity behind Vivaldi and depend on Android preserving a STOPPED host. Exportable operations log is useful and should stay.

### #212 — virtual-display Activity launch, authoritative FAIL
App head `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`; CI #212 PASS. Device immediately reported `VIRTUAL_PREP_LAUNCH_FAILED`. Private display creation worked, but Android denied launching the first normal app Activity there. Do not request privileged `ACTIVITY_EMBEDDING` and do not retry Activity launch onto that display.

A foreground-service-owned **non-Activity** private-display window remains only a contingency; it is a different architecture.

### #215 — first automatic BG completion
App code `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`; CI #215 PASS.

One-PH QA:
- automatic preparation completed before ExternalPlayer/card open; dashboard showed `READY +9s`;
- no card click was needed;
- one run reported actual 720p;
- Vivaldi touch/scroll blocked ~3–5s;
- ~0.5s visible preparation/frame flash occurred. Never inspect/describe that content.

Interpretation: core automatic BG preparation PASS; overlay UX FAIL.

## Build #225 — alpha 0 visibility fix, focus still failed
Corrected app head `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890`; CI #225 PASS run `31850648050`; artifact `9237424502`; APK SHA-256 `ff84bbc469efc1ea62bb6b5c5abefb03cec3c45e33e7f4ff8ed56085c51e60f8`.

Included alpha `0.01 -> 0.0`, persisted Recently closed (max 12), language selector System/English/Español, and refreshed white-E/purple icon.

Post-CI path inspection PASS: BG share created the pending tab, marked preparation requested, acquired foreground lease and directly launched preparation Activity; its `onCreate()` created/configured WebView, called `attemptDirectFirst()` and scheduled browser fallback. No dashboard/tab click, no `moveTaskToBack()`, no `launchDisplayId`, no PlayerActivity/ExoPlayer in BG prep.

User QA:
- Vivaldi unresponsive ~7s: FAIL;
- no visible flash: PASS;
- icon PASS and user liked it;
- language change PASS;
- playback PASS;
- automatic/default actual result 1080p while 720p was available and UI showed `Auto - 720p`: strict 720-first FAIL reconfirmed;
- Recently closed not explicitly tested.

Interpretation: alpha=0 solved visibility but a focusable transparent Activity still blocked input.

## Build #227 — first clean BG input result
App-code commit `f53cfcdce45e6e1d982bfee97b042195969134cb` (`fix: release BG input focus after share`). Documentation commit after build: `cb9b9abf93278f296afda599c0723dd7a7c98c35`.

### Architecture
Keep default-display preparation Activity but separate its window from user input:
- alpha `0.0f`;
- `FLAG_NOT_TOUCHABLE`;
- `FLAG_NOT_FOCUSABLE`;
- explicit `FLAG_NOT_TOUCH_MODAL`;
- direct resolver + automatic browser fallback still start at BG-share time;
- no `moveTaskToBack()`, no virtual-display Activity launch, no ordinary Worker fallback, no PlayerActivity/ExoPlayer during preparation.

`PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED` logs alpha + touch/focus/modal flags.

### CI #227 — PASS
- run `31852454518`;
- built head `f53cfcdce45e6e1d982bfee97b042195969134cb`;
- artifact `9237954029`;
- ZIP SHA-256 `68956445798771e9f98ea45be21e5471577f208f7074ce13f749bd8bbc0a76aa`;
- APK SHA-256 `853cc2da965de5c606e2f1cd083f120787d8d55534809c84bdf77a3ccc560170`.

Post-CI inspection PASS: preparation is still requested/started from `BackgroundShareActivityV2.onCreate()`; `BackgroundVirtualPreparationActivity.onCreate()` marks host/RESOLVING, creates WebView, calls direct resolver and schedules browser fallback. Window flags are alpha 0 + NOT_TOUCHABLE + NOT_FOCUSABLE + NOT_TOUCH_MODAL. No tab-open trigger.

### #227 one-PH device QA — focused BG UX PASS
The user performed the clean sequence **without manually opening ExternalPlayer before Vivaldi**, then reported: “all seemed ok to me, i didnt notice anything weird.” This is authoritative for the focused user-visible behavior: no noticed Vivaldi freeze, no noticed flash/weird overlay, and the test flow behaved normally.

Actual exported operations log confirms the #227 binary (`Git: f53cfcdc`, Actions build 227) and the intended share-time path:
- 21:30:44.736 `BG_SHARE_OVERLAY_HANDOFF_STARTED`;
- 21:30:44.763 `PRIMARY_OVERLAY_PREP_LAUNCH_REQUESTED`;
- 21:30:44.870 `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED` with `display=0 alpha=0.0 notTouchable=true notFocusable=true notTouchModal=true`;
- 21:30:44.872/874 host created and state became RESOLVING;
- 21:30:45.287 WebView created;
- 21:30:45.295 `DIRECT_STARTED`;
- 21:30:45.315 Activity RESUMED;
- 21:30:46.232 Activity PAUSED;
- 21:30:46.422 Activity STOPPED;
- 21:30:48.248 direct resolver FINISHED;
- 21:30:48.250 browser stage REQUESTED;
- excerpt then continues into `BROWSER_AUTO_AFTER...` but the pasted message is truncated before final READY/ERROR lines.

Important interpretation: after Vivaldi regained foreground and the preparation Activity became STOPPED, **the same preparation session demonstrably kept running for at least ~1.8s more**, completing direct resolution and requesting browser fallback. This differs from #205 where the STOPPED Activity was destroyed almost immediately. The user-visible focused test is PASS, but the pasted telemetry does not include the final `BG_PREPARATION_READY` line, so do not invent an exact READY timestamp from this excerpt.

#227 is therefore the first clean result for the specific input/flash blocker. Do not change this BG architecture while the next serialization test is pending.

## Quality diagnosis and planned repair
Strict policy remains 720 -> 1080 -> best below 1080.

Current facts:
- #215 reported 720 once;
- #225 definitively produced 1080 while 720 existed and the UI knew `Auto - 720p`;
- manual 240 works;
- manual 480 still needs real repair/verification.

Code diagnosis for the #225 contradiction:
- browser-assisted payloads can contain a 1080 primary source plus sibling `browserVariants` including 720;
- `PlayerActivity` later knows 720 is the Auto target, but initial playback has already been built from the primary 1080 source.

Planned narrow fix after BG serialization QA: normalize **automatic browser payloads** at the shared `ResolvedMedia` parsing boundary. When sibling heights are available, choose 720, else 1080, else best below 1080, else smallest >1080 fallback. Only automatic/browser requests are normalized; explicit manual choices such as 480 must remain exact. This also repairs already-persisted old browser payloads when reopened without changing BG lifecycle code.

Do not claim manual 480 fixed merely from that normalization; test/repair it separately.

## Current UI/backlog
- long-press tab reorder WORKS;
- closing tabs WORKS;
- resume position WORKS;
- tested Back flow after manual Browser Step WORKS;
- BG absence from Recents PASS/preferred;
- operations log PASS/useful;
- icon PASS on #225;
- language selector/change PASS on #225; reopen persistence not separately reported;
- Recently closed implemented but explicit device QA pending;
- secure GitHub log-report shortcut approved for later; never embed PAT/token/client secret.

## Current priority
1. **Keep build #227 installed; no new APK.** Test 2–3 PH BG shares close together to verify browser-slot serialization under the clean input architecture.
2. Vivaldi must remain responsive during all shares; no flash.
3. Do not manually open individual tabs to make preparation start. Later inspect dashboard states.
4. Export operations log. Look for each tab leaving QUEUED automatically; only one browser owner at a time may use the process-wide ServiceWorker client; waiting tabs may show `BROWSER_WAITING_FOR_SLOT` and must later progress.
5. If 2–3-tab serialization is clean, implement the narrow automatic browser 720-first normalization described above, CI it, inspect the path, update both state files, and give one focused quality APK.
6. Then repair/verify manual 480 switching.
7. Later test Recently closed and language persistence.
8. No HH until PH BG + quality blockers are cleared.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
