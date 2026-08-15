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
Stopped preparation Activity was destroyed almost immediately even with foreground process importance. Do not depend on a stopped WebView Activity behind Vivaldi.

### #212
Private virtual display creation worked, but Android denied launching the first normal app **Activity** onto it. Do not retry Activity launch there or request privileged `ACTIVITY_EMBEDDING`.

### #215 / #225
#215 achieved automatic PH BG completion but Vivaldi blocked ~3–5s and a flash occurred. #225 alpha 0 fixed the flash but Vivaldi still blocked ~7s. #225 also definitively showed Auto actual 1080 while 720 existed and UI showed `Auto - 720p`.

### #227 — authoritative FAIL
Default-display transparent preparation Activity used alpha 0 + NOT_TOUCHABLE + NOT_FOCUSABLE + NOT_TOUCH_MODAL. One initial share looked clean, but repeated/multi-share QA superseded it: Vivaldi could freeze on the first share and, after clearing tabs, the second share froze. Do not keep tuning display-0 Activity flags.

## Build #234 — validated BG architecture
App-code head `6cd8995ba615b8b70f83806bad9abca49a024034`; CI #234 PASS run `31858367113`; APK SHA-256 `b6d921b2b1dd5f19c9c4b7b1763aad03476a901dc434ed9a05d84bb8a126c351`.

Normal path:
`short share Activity -> pending tab -> foreground service(token/tab/url) -> finishAndRemoveTask -> private virtual display -> service-owned Presentation/WebView -> direct resolver -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.

Important facts:
- no normal preparation Activity is launched on display 0;
- service owns `BackgroundPrivateDisplayPreparationSession` instances;
- private display uses `OWN_CONTENT_ONLY | PRESENTATION`;
- session uses non-Activity `Presentation`/WebView with `TYPE_PRIVATE_PRESENTATION`;
- browser ownership remains serialized because `ServiceWorkerController` is process-wide;
- no PlayerActivity/Media3/ExoPlayer during preparation;
- no privileged embedding/overlay permission or access-control bypass.

This is distinct from #212 because #234 does not launch an Activity onto the private display.

### #234 device QA — PASS
User ran the repeated/multi-share test and reported **“no issues detected.”** Treat the focused Vivaldi-freeze problem as PASS on #234.

Supplied #234 log confirms:
- build `Git: 6cd8995b`, Actions 234;
- `BG_SHARE_PRIVATE_SERVICE_HANDOFF_STARTED` 22:15:35.912;
- `PRIVATE_PRESENTATION_SERVICE_REQUESTED` 22:15:35.928;
- service created 22:15:36.026;
- private session started 22:15:36.053;
- `VIRTUAL_DISPLAY_CREATED` 22:15:36.113 with `display=3 private=true presentation=true`;
- `PRIVATE_PRESENTATION_CREATED` 22:15:36.793 with `defaultDisplay=false type=PRIVATE_PRESENTATION`;
- private WebView created 22:15:36.794;
- direct started 22:15:36.808, finished ~22:15:38.850, browser requested ~22:15:38.858.

The pasted excerpt stops before later browser-start/READY and only shows the first tab's early telemetry. Do not invent missing timings. No old `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED/RESUMED` appears in the supplied #234 excerpt.

Decision: keep #234 private-display service architecture. Do not return to display-0 preparation Activity.

## Build #236 — CURRENT focused quality QA target
App-code commit `d6c1328823ce2027beecab7970b02420d1cffc7b`; CI #236 PASS run `31858887503`; artifact `9239902382`; APK SHA-256 `ca24f6943849853d4ba6580ceaf107b9795ebc8b943dc55ac28cdab66b8c3bff`.

Compared with the #234 validated state, **only `ResolvedMedia.kt` changed**. BG/private-display code is untouched.

### #236 quality fix
`ResolvedMedia.fromJson()` now normalizes automatic browser payloads before the first MediaSource is built:
1. exact 720p;
2. else exact 1080p;
3. else highest below 1080p;
4. else smallest declared >1080p fallback.

Only automatic browser requests (`browser`, `auto`, blank) are normalized. Explicit numeric manual selections such as `480` are preserved exactly. This also repairs persisted old browser payloads when reopened.

CI #236 details:
- job `94948493526`, all steps PASS;
- ZIP `26,027,146` bytes, SHA-256 `f71a07e38922f8d60e41e27633eec823771f8714452cfd60f4e17a2e4d19d366`;
- APK `35,527,386` bytes, SHA-256 `ca24f6943849853d4ba6580ceaf107b9795ebc8b943dc55ac28cdab66b8c3bff`.

Manual 240 previously works. Manual 480 is **not claimed fixed by #236**; the parser change merely guarantees explicit 480 will not be normalized back to Auto.

## Other backlog
- Recently closed implemented but explicit device QA pending.
- Language selector/change PASS; reopen persistence not separately reported.
- Secure GitHub log-report shortcut later; never embed GitHub PAT/token/client secret.

## Current priority
1. Install/test Build #236 over #234.
2. Use PH technical test with both 720p and 1080p available, ideally the case that previously started at 1080.
3. Create a fresh BG tab, wait until READY, play it, and verify actual/diagnostic initial height is **720p** and quality UI is consistent with Auto 720.
4. Quick sanity: Vivaldi should remain responsive exactly as #234 because BG code did not change.
5. If Auto 720 passes, select manual 480 if offered and report whether it truly switches/continues at 480. This is diagnostic; #236 does not claim manual 480 repair.
6. If manual 480 fails, repair that path next.
7. Later test Recently closed/language persistence. No HH until PH BG + quality blockers are clear.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
