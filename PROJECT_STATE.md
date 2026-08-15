# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA results, failures, decisions, or priorities change.

## Working rules
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Use connected GitHub tools directly. Source should contain abundant English comments.
- PH and HH are real technical playback targets. URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed.
- Do **not** inspect, describe, classify, summarize, or request PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected playback baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p; >1080 only rare fallback.

Preserve Vivaldi share targets; yt-dlp first/browser fallback; automatic best/manual fallback; video+audio; adaptive/sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; 80 stored/20 manual candidate limits; first-seen HLS/DASH ordering; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no imagery-based resolver/ranking; exactly one actual ExoPlayer playback session.

Previously verified baseline includes Bitmovin/PH/HH playback, build #62 follow-up, #74 clean loading, and #109 install/About/Settings/no-background-playback/local browser title. Do not repeat old PASS items without a regression reason.

Permanent release signing remains deferred. Debug GitHub Actions APKs are the QA path; never commit a permanent signing key.

## BG preparation history
### #192 / #202
#192 proved preparation could begin before tab/card clicks, but three PH tabs took ~244–270s and ended at Browser Step; manual Browser Step worked ~5–10s.

#202 added a 12s direct budget, bounded yt-dlp, normal-size hidden WebView, Service Worker + DOM/Performance/page-config discovery and serialized browser ownership. PH still took minutes and ended `NEEDS_ATTENTION`; manual Browser Step worked ~5–7s. Quality findings: manual 240p works, manual 480p does not, Auto could choose 1080 despite 720 existing.

### #205 — stopped-Activity failure
Foreground keep-alive protected the process but not a stopped Activity. Device log showed the preparation Activity STOPPED and then was destroyed almost immediately. Decision: do not intentionally put a WebView Activity behind Vivaldi and depend on Android preserving a STOPPED host.

### #212 — private virtual-display Activity launch, authoritative FAIL
App head `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`; CI #212 PASS. The app-private virtual display itself was created, but Android denied launching the first normal app Activity onto it (`VIRTUAL_PREP_LAUNCH_FAILED`). Do not request privileged `ACTIVITY_EMBEDDING` and do not retry **Activity launch** onto a private display.

Important distinction: the private-display primitive itself worked. A foreground-service-owned **non-Activity** window on that display remained a valid separate architecture.

### #215 — first automatic BG completion
App code `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`; CI #215 PASS. One-PH QA completed automatically before ExternalPlayer/card open (`READY +9s`), but Vivaldi touch/scroll blocked ~3–5s and a brief visible preparation/frame flash occurred.

### #225 — alpha 0 fixed flash, input still failed
Corrected app head `4a6a2225eae86e0dbae0e5e02ac6c1f2bc434890`; CI #225 PASS. Alpha 0 removed the visible flash, icon PASS, language change PASS, playback PASS, but Vivaldi remained unresponsive ~7s. Automatic/default actual playback was 1080p while 720p was available and the menu showed `Auto - 720p`: strict 720-first FAIL reconfirmed.

### #227 — single-share looked clean, repeated/multi-share authoritative FAIL
App code `f53cfcdce45e6e1d982bfee97b042195969134cb`; Build #227 PASS; APK SHA-256 `853cc2da965de5c606e2f1cd083f120787d8d55534809c84bdf77a3ccc560170`.

Architecture was default-display preparation Activity with alpha `0.0`, `NOT_TOUCHABLE`, `NOT_FOCUSABLE`, `NOT_TOUCH_MODAL`, direct resolver + automatic browser fallback at share time, no PlayerActivity/ExoPlayer during BG preparation.

Initial one-PH user test looked clean. The exported 21:30 log proved the intended flags and showed the Activity later PAUSED/STOPPED while direct resolution continued briefly. That log excerpt was truncated before the final READY/ERROR anchor.

**Later repeated/multi-share QA supersedes the provisional single-share pass.** User findings:
- when ExternalPlayer happened to be open before sharing, Vivaldi froze again;
- after repeating the requested sequence, a first share could also freeze;
- after opening ExternalPlayer, clearing tabs, returning to the clean test sequence: first share did not freeze, **second share did**.

The log pasted with this multi-share report was the same old 21:30 single-tab excerpt and did not contain the later second-tab freeze; do not treat it as multi-tab telemetry.

Conclusion: the transparent/default-display preparation Activity is nondeterministic and **not reliable**, even with alpha 0 + NOT_TOUCHABLE + NOT_FOCUSABLE. Do not keep tuning display-0 Activity flags. That architecture is exhausted.

## Build #234 — current focused QA target: service-owned private Presentation/WebView
The replacement architecture is implemented across commits `efa2ff6622247dd6616eadcb522979083e72b9a1` -> `0c44d0cd3ee3585eece6adecf6f9faa3243915f3` -> `7a38252f4daea973bbb3aef8d7e73e6058f2a81a` -> `5c5c93bca225ef53fa365f98dee09ff4c3a56e9b` -> final app-code head `6cd8995ba615b8b70f83806bad9abca49a024034`.

### Architecture
Normal `BG - External Player` path is now:
`short exported share Activity -> create persistent pending tab -> mark preparation requested -> start foreground service with token/tab/url -> finishAndRemoveTask() -> service creates private virtual display -> service-owned Presentation/WebView on that private display -> direct yt-dlp -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.

Key properties:
- **no preparation Activity is launched on display 0** in the normal V2 BG path;
- `BackgroundShareActivityV2` no longer calls `startActivity()` for `BackgroundVirtualPreparationActivity`;
- the foreground service owns one `BackgroundPrivateDisplayPreparationSession` per share token;
- each session reuses `BackgroundVirtualDisplayRegistry`, which creates `OWN_CONTENT_ONLY | PRESENTATION` virtual displays;
- the session creates a `Presentation` and WebView using the presentation/display context, with window type `TYPE_PRIVATE_PRESENTATION`;
- the private WebView can be normally laid out/focusable on its isolated display without owning physical-display input;
- direct resolver keeps the existing 12-second browser-fallback budget;
- browser discovery remains serialized because `ServiceWorkerController` is process-wide; waiting sessions may log `BROWSER_WAITING_FOR_SLOT` and must later acquire the slot automatically;
- conservative cookie/18+ handling only; protected/challenge cases still stop or need foreground interaction as before;
- no PlayerActivity, Media3 playback or ExoPlayer is created by BG preparation;
- historical `BackgroundVirtualPreparationActivity` remains declared/source-visible but the normal V2 share path no longer launches it;
- no privileged activity embedding, overlay permission, DRM/auth bypass or browser-credential import was added.

This is deliberately distinct from #212: #212 attempted to launch an **Activity** on the private display and Android denied it. #234 creates a **non-Activity Presentation/Dialog** owned by the foreground service.

### CI #234 — PASS
- GitHub Actions build **#234** PASS.
- Run ID `31858367113`.
- Built head `6cd8995ba615b8b70f83806bad9abca49a024034`.
- Artifact ID `9239756055`, `VivaldiExternalPlayer-debug-apk`.
- Artifact ZIP size `26,026,322` bytes.
- Artifact ZIP GitHub digest / locally verified SHA-256: `b0ab48cc55d5809fed023e5c9341b32e194f5b1758d9925b1173c5a5df662f82`.
- Extracted APK size `35,528,286` bytes.
- Extracted APK SHA-256: `b6d921b2b1dd5f19c9c4b7b1763aad03476a901dc434ed9a05d84bb8a126c351`.

### #234 post-CI code-path inspection — PASS
- `BackgroundShareActivityV2.onCreate()` creates the tab, marks preparation requested, starts `BackgroundPreparationKeepAliveService.acquire(... token, tabId, sourceUrl ...)`, then calls `finishAndRemoveTask()`.
- There is no preparer `startActivity()` in that normal path.
- `BackgroundPreparationKeepAliveService` stores active private-display sessions, starts one from the share-time service command, and releases the foreground lease when that session finishes.
- Session code creates the private display/Presentation/WebView and begins direct resolution automatically; browser fallback is internal and serialized.

CI proves compile/integration only. Device QA must prove Presentation/WebView runtime behavior on the real phone and, most importantly, that first **and second** BG shares leave Vivaldi responsive.

## Quality status / planned repair
Strict policy remains 720 -> 1080 -> best below 1080.

Current facts:
- #215 reported 720 once;
- #225 definitively produced 1080 while 720 existed and UI knew `Auto - 720p`;
- manual 240 works;
- manual 480 still needs real repair/verification.

Diagnosis: browser-assisted payloads can contain a 1080 primary source plus sibling `browserVariants` including 720. Player UI later knows the Auto target should be 720, but initial playback may already have been built from 1080.

Planned narrow fix **only after #234 BG architecture passes device QA**: normalize automatic browser payloads at the shared `ResolvedMedia.fromJson()` boundary. Prefer sibling 720, else 1080, else best below 1080, else smallest >1080 fallback. Preserve explicit manual choices such as 480. This also repairs persisted old browser payloads when reopened.

Do not claim manual 480 fixed from that normalization; repair/test it separately.

## Current UI/backlog
- long-press tab reorder WORKS;
- closing tabs WORKS;
- resume position WORKS;
- tested Back flow after manual Browser Step WORKS;
- BG absence from Recents preferred/previously PASS;
- operations log PASS/useful;
- icon PASS on #225;
- language selector/change PASS on #225; reopen persistence not separately reported;
- Recently closed implemented but explicit device QA pending;
- secure GitHub log-report shortcut later; never embed PAT/token/client secret.

## Current priority
1. Install **Build #234** and run focused PH BG-input/runtime QA.
2. Test the exact #227 failure pattern: first share and second share close together. Vivaldi must remain immediately responsive on both.
3. Prior ExternalPlayer state must not matter. It is fine to open ExternalPlayer once to clear old tabs, then leave it and perform the test from Vivaldi.
4. Do not open individual new tabs during preparation. After roughly 30 seconds, open the dashboard once and inspect whether tabs progressed automatically.
5. Export the **new** operations log after the test.
6. Expected #234 anchors include `BG_SHARE_PRIVATE_SERVICE_HANDOFF_STARTED`, `PRIVATE_PRESENTATION_SERVICE_REQUESTED`, `KEEPALIVE_SERVICE_CREATED`, `PRIVATE_PRESENTATION_SERVICE_SESSION_STARTED`, `VIRTUAL_DISPLAY_CREATED ... private=true presentation=true`, `PRIVATE_PRESENTATION_CREATED ... defaultDisplay=false type=PRIVATE_PRESENTATION`, `PRIVATE_DISPLAY_WEBVIEW_CREATED`, `DIRECT_STARTED`, browser stages, and ideally `BG_PREPARATION_READY`.
7. In the new test there should be **no** `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED` / `PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED` anchors for the normal BG path.
8. If Presentation creation/show fails, inspect `PRIVATE_PRESENTATION_SHOW_FAILED` / related errors. Do not return to the display-0 Activity. A possible next non-Activity fallback is a display-bound window context + WindowManager on the private display.
9. If #234 cleanly passes first + second share and automatic preparation, implement the narrow 720-first browser normalization next; then repair/verify manual 480.
10. Later test Recently closed/language persistence. No HH until PH BG + quality blockers are cleared.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
