# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` before substantive work. Keep both state files current whenever QA, architecture, failures, priorities, or decisions change.

## Communication / safety
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Use connected GitHub tools directly. Source should contain abundant English comments.
- PH/HH are real technical playback targets. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/states/errors/local titles are allowed.
- Never inspect/describe/classify PH/HH media content or thumbnail imagery and never ask the user to provide it.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials.
- Never add background playback or a second ExoPlayer session.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise best below 1080p; >1080 only rare fallback. Preserve yt-dlp first/browser fallback, automatic/manual quality, adaptive/sibling quality, double-tap ±10s, seek preview, rotation, bilingual UI, candidate limits/order, page-config families, no imagery ranking, one actual ExoPlayer playback session.

## BG history that must not be forgotten
### #205
Stopped Activity failure: foreground service protected process importance but the preparation Activity was destroyed almost immediately after becoming STOPPED behind Vivaldi. Do not intentionally depend on a stopped WebView Activity.

### #212
Private virtual display creation itself worked, but Android denied launching the first normal app **Activity** onto it (`VIRTUAL_PREP_LAUNCH_FAILED`). Do not retry Activity launch there and do not request privileged `ACTIVITY_EMBEDDING`. A service-owned non-Activity window on the display is a distinct architecture.

### #215 / #225
#215 first achieved automatic PH BG completion before ExternalPlayer/card open but Vivaldi blocked ~3–5s and a brief flash occurred. #225 alpha 0 fixed the flash, icon/language/playback passed, but Vivaldi still blocked ~7s. #225 also definitively reconfirmed Auto actual 1080 while 720 was available and UI showed `Auto - 720p`.

### #227 — default-display nonfocusable Activity is now authoritative FAIL
App code `f53cfcdce45e6e1d982bfee97b042195969134cb`; CI #227 PASS.

It used a default-display preparation Activity with alpha 0 + NOT_TOUCHABLE + NOT_FOCUSABLE + NOT_TOUCH_MODAL. One initial PH share looked clean, and the 21:30 operations log proved those flags. The pasted log was truncated before final READY/ERROR.

Later repeated/multi-share QA superseded that provisional success:
- user accidentally left ExternalPlayer open before a share and Vivaldi froze;
- corrected repeat could freeze at the first share;
- after clearing tabs and repeating cleanly, first share did not freeze but **second share did**.

The “multi” log pasted with this report was actually the same old 21:30 single-tab excerpt; no new multi-tab telemetry was received.

Conclusion: display-0 preparation Activity is nondeterministic/unreliable even with all transparent/input flags. **Do not keep tuning it.**

## Build #234 — CURRENT focused QA target
Final app-code head `6cd8995ba615b8b70f83806bad9abca49a024034`. GitHub Actions **#234 PASS**, run `31858367113`.

Artifact:
- ID `9239756055`, `VivaldiExternalPlayer-debug-apk`;
- ZIP size `26,026,322` bytes;
- ZIP SHA-256 `b0ab48cc55d5809fed023e5c9341b32e194f5b1758d9925b1173c5a5df662f82`;
- extracted APK size `35,528,286` bytes;
- APK SHA-256 `b6d921b2b1dd5f19c9c4b7b1763aad03476a901dc434ed9a05d84bb8a126c351`.

### New architecture
Normal V2 BG path is now:
`short share Activity -> persistent pending tab -> foreground service(token/tab/url) -> share Activity finishAndRemoveTask -> service-owned private virtual display -> service-owned Presentation/WebView -> direct resolver -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.

Important implementation facts:
- `BackgroundShareActivityV2` no longer starts `BackgroundVirtualPreparationActivity` or any other preparation Activity.
- No normal prep Activity/window is placed on display 0.
- `BackgroundPreparationKeepAliveService` owns `BackgroundPrivateDisplayPreparationSession` objects.
- Session uses existing `BackgroundVirtualDisplayRegistry` with `OWN_CONTENT_ONLY | PRESENTATION` display flags.
- Session creates an Android `Presentation`/WebView on that private display and uses `TYPE_PRIVATE_PRESENTATION`.
- Direct yt-dlp still starts automatically at share time with the 12s browser-fallback budget.
- Browser ownership is still serialized because `ServiceWorkerController` is process-wide; waiting sessions can log `BROWSER_WAITING_FOR_SLOT` and later acquire automatically.
- Conservative cookie/18+ handling only. No auth/DRM/region/challenge bypass.
- No PlayerActivity, Media3 playback or ExoPlayer in preparation.
- Historical `BackgroundVirtualPreparationActivity` remains in source/manifest but normal V2 share no longer invokes it.
- No privileged embedding or overlay permission was added.

This is distinct from #212: #212 tried to launch an Activity onto the private display; #234 uses a **non-Activity Presentation/Dialog owned by the foreground service**.

### #234 post-CI inspection
PASS. The committed normal share path creates/marks tab, calls `BackgroundPreparationKeepAliveService.acquire(... token, tabId, sourceUrl ...)`, then `finishAndRemoveTask()`. No preparer `startActivity()` exists in that path. Service owns the session and private-display resolver automatically.

CI only proves the architecture compiles. Real device must still prove private Presentation/WebView runtime and first+second share input isolation.

## Quality diagnosis — still deferred until BG passes
- Strict Auto remains broken from #225: actual 1080 despite available 720.
- Manual 240 works; manual 480 needs repair/verification.
- Diagnosis: browser payload can carry a 1080 primary plus sibling variants including 720; Player UI later knows Auto should be 720 but initial source may already be 1080.
- Planned narrow fix after #234 passes: at `ResolvedMedia.fromJson()`, normalize automatic browser payload initial source to 720, else 1080, else best below 1080, else smallest >1080. Preserve explicit manual qualities such as 480.
- Do not claim manual 480 fixed by that normalization; test/repair separately.

## Other backlog
- Recently closed implemented but explicit device QA pending.
- Language selector/change PASS; reopen persistence not separately reported.
- Secure GitHub log-report shortcut later; never embed GitHub PAT/token/client secret.

## Current priority
1. Install/test **Build #234**.
2. It is okay to open ExternalPlayer once to clear old tabs, then leave it and go to Vivaldi. The architecture should not depend on prior app state.
3. Share PH test #1 via `BG - External Player`; immediately test Vivaldi touch/scroll.
4. Within ~3–10s share PH test #2; immediately test Vivaldi again. Both must remain responsive.
5. Do not click/open individual new ExternalPlayer tabs during preparation. After ~30s open dashboard once and inspect statuses.
6. Export the NEW operations log.
7. Expected new anchors: `BG_SHARE_PRIVATE_SERVICE_HANDOFF_STARTED`, `PRIVATE_PRESENTATION_SERVICE_REQUESTED`, `KEEPALIVE_SERVICE_CREATED`, `PRIVATE_PRESENTATION_SERVICE_SESSION_STARTED`, `VIRTUAL_DISPLAY_CREATED ... private=true presentation=true`, `PRIVATE_PRESENTATION_CREATED ... defaultDisplay=false type=PRIVATE_PRESENTATION`, `PRIVATE_DISPLAY_WEBVIEW_CREATED`, `DIRECT_STARTED`, browser stages, ideally `BG_PREPARATION_READY`.
8. New normal BG test should **not** contain `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED` / `PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED`.
9. If Presentation show/runtime fails, inspect private-presentation errors and do not return to display-0 Activity; next fallback may be display-bound `WindowManager`/window-context on the same private display.
10. If #234 passes first+second share plus automatic preparation, implement strict 720-first normalization, then manual 480. No HH until PH BG + quality blockers are clear.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
