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

Preserve Vivaldi share targets; yt-dlp first/browser fallback; automatic/manual quality; video+audio; adaptive/sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate limits/order; page-config family IDs; no imagery-based resolver/ranking; exactly one actual ExoPlayer playback session.

Permanent release signing remains deferred. Debug GitHub Actions APKs are the QA path; never commit a permanent signing key.

## BG preparation history
### #192 / #202
#192 proved preparation could start before tab/card clicks but multi-PH completion was far too slow. #202 added bounded direct resolution plus hidden WebView/browser discovery and serialized browser ownership, but PH still took minutes and often needed attention. Manual Browser Step worked ~5–10s.

### #205
Foreground service protected process importance but not a stopped preparation Activity. On-device the Activity became STOPPED and was destroyed almost immediately. Do not depend on a stopped WebView Activity behind Vivaldi.

### #212
Private virtual display creation worked, but Android denied launching the first normal app **Activity** onto that display (`VIRTUAL_PREP_LAUNCH_FAILED`). Do not request privileged `ACTIVITY_EMBEDDING` and do not retry Activity launch there.

### #215 / #225
#215 first achieved automatic PH BG completion before ExternalPlayer/card open, but Vivaldi blocked ~3–5s and a brief flash occurred. #225 alpha 0 removed the flash, but Vivaldi still blocked ~7s. #225 also definitively showed Auto actual 1080 while 720 existed and the menu showed `Auto - 720p`.

### #227 — default-display transparent Activity authoritative FAIL
App code `f53cfcdce45e6e1d982bfee97b042195969134cb`; Build #227 PASS; APK SHA-256 `853cc2da965de5c606e2f1cd083f120787d8d55534809c84bdf77a3ccc560170`.

It used alpha 0 + `NOT_TOUCHABLE` + `NOT_FOCUSABLE` + `NOT_TOUCH_MODAL` on a default-display preparation Activity. One initial share looked clean, but repeated QA superseded that result: Vivaldi could freeze on the first share and, after clearing tabs, first share could be clean while the **second share froze**. Do not keep tuning display-0 Activity flags; that architecture is exhausted.

## Build #234 — service-owned private Presentation/WebView: DEVICE PASS
Final app-code head `6cd8995ba615b8b70f83806bad9abca49a024034`; GitHub Actions #234 PASS, run `31858367113`; artifact `9239756055`; APK SHA-256 `b6d921b2b1dd5f19c9c4b7b1763aad03476a901dc434ed9a05d84bb8a126c351`.

### Architecture
Normal `BG - External Player` path:
`short exported share Activity -> persistent pending tab -> foreground service(token/tab/url) -> share Activity finishAndRemoveTask() -> service-owned private virtual display -> service-owned Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.

Key properties:
- no preparation Activity is launched on display 0 in the normal V2 path;
- `BackgroundPreparationKeepAliveService` owns one `BackgroundPrivateDisplayPreparationSession` per share token;
- private displays use `OWN_CONTENT_ONLY | PRESENTATION`;
- the WebView lives inside a non-Activity `Presentation` with `TYPE_PRIVATE_PRESENTATION` on the private display;
- browser discovery remains serialized because `ServiceWorkerController` is process-wide;
- no PlayerActivity/Media3/ExoPlayer is created during preparation;
- no privileged embedding, overlay permission, auth/DRM/region/challenge bypass, or browser-credential import.

This is distinct from #212: #212 attempted an Activity launch on the private display; #234 uses a service-owned non-Activity Presentation/Dialog.

### #234 device QA — PASS
User ran the repeated/multi-share test and reported **“no issues detected.”** Treat this as authoritative for the focused BG problem: repeated shares did not reproduce the Vivaldi freezing seen in #227 and no visible issue was noticed.

Exported log confirms the new architecture and exact #234 binary (`Git: 6cd8995b`, Actions build 234):
- 22:15:35.912 `BG_SHARE_PRIVATE_SERVICE_HANDOFF_STARTED`;
- 22:15:35.928 `PRIVATE_PRESENTATION_SERVICE_REQUESTED`;
- 22:15:36.026 `KEEPALIVE_SERVICE_CREATED`;
- 22:15:36.053 `PRIVATE_PRESENTATION_SERVICE_SESSION_STARTED`;
- 22:15:36.113 `VIRTUAL_DISPLAY_CREATED` with `display=3 ... private=true presentation=true`;
- 22:15:36.790 state became `PRIVATE_PRESENTATION_CREATED`;
- 22:15:36.793 `PRIVATE_PRESENTATION_CREATED | display=3 defaultDisplay=false type=PRIVATE_PRESENTATION`;
- 22:15:36.794 `PRIVATE_DISPLAY_WEBVIEW_CREATED`;
- 22:15:36.808 `DIRECT_STARTED`;
- 22:15:38.850 direct finished;
- 22:15:38.858 browser requested.

The pasted excerpt stops during the first tab's browser-request stage and does not include later browser-start/READY anchors or the second tab's telemetry, so do not invent those timestamps. The user-visible repeated-share result is nevertheless PASS. No old `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED/RESUMED` event appears in the supplied #234 excerpt.

Decision: **keep the #234 private-display service architecture. Do not return to the display-0 preparation Activity.**

## Build #236 — current focused QA target: strict browser Auto 720-first
App-code commit `d6c1328823ce2027beecab7970b02420d1cffc7b` (`fix: enforce 720-first browser auto source`). Compared with the #234-validated state, the only app-code file changed is `ResolvedMedia.kt`; BG/share/service/private-display code is untouched.

### Quality fix
`ResolvedMedia.fromJson()` now normalizes automatic browser payloads **before PlayerActivity creates the first MediaSource**:
1. exact 720p if available;
2. otherwise exact 1080p;
3. otherwise highest declared height below 1080p;
4. rare fallback: smallest declared height above 1080p.

The normalization runs only for browser resolver payloads whose `requestedQuality` is automatic (`browser`, `auto`, or blank). Explicit numeric selections such as `480`, `720`, or `1080` are left unchanged, so manual quality choices are not silently reset to Auto.

This also repairs persisted older browser payloads when parsed/opened again, because the correction is at the shared resolver-data boundary.

### CI #236 — PASS
- Run ID `31858887503`.
- Job ID `94948493526`; all build/upload steps succeeded.
- Built head `d6c1328823ce2027beecab7970b02420d1cffc7b`.
- Artifact ID `9239902382`, `VivaldiExternalPlayer-debug-apk`.
- Artifact ZIP size `26,027,146` bytes.
- ZIP SHA-256 / GitHub digest: `f71a07e38922f8d60e41e27633eec823771f8714452cfd60f4e17a2e4d19d366`.
- Extracted APK size `35,527,386` bytes.
- APK SHA-256 `ca24f6943849853d4ba6580ceaf107b9795ebc8b943dc55ac28cdab66b8c3bff`.

### Quality status
- #225 definitively reproduced Auto actual 1080 despite available 720.
- #236 is intended to fix that initial-source contradiction.
- Manual 240 previously worked.
- Manual 480 remains **unfixed/unverified by #236**. The new parser deliberately preserves explicit 480 rather than changing it; test it diagnostically after Auto 720 is checked, then repair separately if it still fails.

## Current UI/backlog
- long-press tab reorder WORKS;
- closing tabs WORKS;
- resume position WORKS;
- tested Back flow after manual Browser Step WORKS;
- operations log PASS/useful;
- icon PASS on #225;
- language selector/change PASS on #225; reopen persistence not separately reported;
- Recently closed implemented but explicit device QA pending;
- secure GitHub log-report shortcut later; never embed PAT/token/client secret.

## Current priority
1. Install **Build #236** over #234.
2. Use one PH technical test where 720p and 1080p are both available and where #225 previously selected 1080.
3. Create a fresh BG tab, let it become READY, open playback, and verify the **actual/diagnostic initial height is 720p** and the quality UI is consistent with Auto 720.
4. Do a quick BG responsiveness sanity check; #236 should behave like #234 because only `ResolvedMedia.kt` changed.
5. If Auto 720 passes, select manual 480p if offered and report whether playback really switches/continues at 480. Do not assume #236 fixed manual 480.
6. If manual 480 still fails, repair that path next.
7. Later test Recently closed and language persistence. No HH until PH BG + quality blockers are cleared.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
