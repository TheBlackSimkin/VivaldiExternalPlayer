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

## BG architecture history
#205: stopped preparation Activity was destroyed almost immediately; do not depend on a stopped WebView Activity behind Vivaldi.
#212: private virtual display creation worked, but Android denied launching a normal app **Activity** onto it; do not retry Activity launch or request privileged `ACTIVITY_EMBEDDING`.
#227: default-display transparent/nonfocusable preparation Activity remained nondeterministic and could freeze Vivaldi on repeated shares; do not return to display-0 Activity tuning.

## #234 private-display service architecture — DEVICE PASS
App code `6cd8995ba615b8b70f83806bad9abca49a024034`; CI #234 PASS; APK SHA-256 `b6d921b2b1dd5f19c9c4b7b1763aad03476a901dc434ed9a05d84bb8a126c351`.

Normal path:
`short share Activity -> pending tab -> foreground service(token/tab/url) -> finishAndRemoveTask -> private virtual display -> service-owned Presentation/WebView -> direct resolver -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.

Key facts:
- no normal preparation Activity on display 0;
- service owns `BackgroundPrivateDisplayPreparationSession`;
- private display uses `OWN_CONTENT_ONLY | PRESENTATION`;
- non-Activity `Presentation`/WebView uses `TYPE_PRIVATE_PRESENTATION`;
- browser ownership serialized because `ServiceWorkerController` is process-wide;
- no PlayerActivity/Media3/ExoPlayer during prep;
- no privileged embedding/overlay permission or access-control bypass.

Repeated/multi-share QA on #234: **no issues detected**. Keep this architecture protected.

## #236 — CURRENT PH + HH CORE BASELINE, DEVICE PASS
App code `d6c1328823ce2027beecab7970b02420d1cffc7b`; CI #236 PASS run `31858887503`; artifact `9239902382`; APK SHA-256 `ca24f6943849853d4ba6580ceaf107b9795ebc8b943dc55ac28cdab66b8c3bff`.

Only `ResolvedMedia.kt` changed from #234. Automatic browser payloads select 720p if present, else 1080p, else highest below 1080p, else smallest >1080p rare fallback. Explicit numeric manual choices remain exact.

PH device QA is fully PASS:
- BG share/Vivaldi responsiveness;
- Auto 720-first;
- manual quality including 480p;
- playback sanity.
Final PH manual check: user reported **“All worked perfectly.”**

HH technical smoke test was then run on the **unchanged #236 binary**. User reported **“All OK.”** Treat the requested HH dimensions as PASS: BG responsiveness, automatic preparation, playback, Auto-quality sanity, and manual quality behavior where applicable. No HH-specific code change was needed.

Do not change PH/HH BG or quality architecture without a concrete new regression.

## Remaining checks / backlog
Still explicit device QA:
- Recently Closed end-to-end behavior;
- language persistence after app close/reopen/restart.

Later hardening:
- PH/HH + both Vivaldi share-target regression;
- operations-log noise cleanup;
- stale historical/dead-path cleanup where safe;
- About/version/docs consistency;
- secure GitHub log-report shortcut later, never embedding PAT/token/client secret;
- release signing/distribution decision later; permanent signing remains deferred.

## Current priority
1. On unchanged Build #236, test Recently Closed: close a tab, verify entry, restore it, then test clear behavior.
2. Test language persistence across full app close/reopen/restart.
3. If both pass, move to release hardening/regression rather than more blocker repair.
4. Keep #234/#236 private-display BG path and 720-first parser as protected baseline.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
