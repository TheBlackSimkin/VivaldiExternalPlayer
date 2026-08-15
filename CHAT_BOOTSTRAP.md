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
#227: default-display transparent/nonfocusable preparation Activity remained nondeterministic and could still freeze Vivaldi on repeated shares; do not return to display-0 Activity tuning.

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

User repeated/multi-share QA on #234: **no issues detected**. Supplied log confirmed `private=true presentation=true`, `defaultDisplay=false type=PRIVATE_PRESENTATION`, private WebView creation, and no old display-0 prep Activity anchors in the excerpt. Keep this architecture as protected baseline.

## #236 Auto-quality fix — DEVICE PASS
App code `d6c1328823ce2027beecab7970b02420d1cffc7b`; CI #236 PASS run `31858887503`; artifact `9239902382`; APK SHA-256 `ca24f6943849853d4ba6580ceaf107b9795ebc8b943dc55ac28cdab66b8c3bff`.

Only `ResolvedMedia.kt` changed from validated #234. Automatic browser payloads now select 720p if present, else 1080p, else highest below 1080p, else smallest >1080p rare fallback. Explicit numeric manual choices remain exact.

Device result: user reported **no issues**. With both 1080p and 720p available, playback started at **720p**. Changing to other qualities worked. This closes the #225 Auto-1080 contradiction.

Supplied #236 log identifies exact binary (`Git: d6c13288`, Actions 236) and again confirms private-display prep (`display=9`, `private=true presentation=true`, `defaultDisplay=false type=PRIVATE_PRESENTATION`). Excerpt stops before browser completion/READY; do not invent missing timings.

### Manual 480 remains only technically unverified
User could not visually tell whether 480p was different and believed that was eyesight rather than app failure. Do not call 480 PASS/FAIL from appearance. Next check: select 480p and read the player diagnostics/reported actual height. If it says 480p and playback continues, close the blocker without code changes.

## Other backlog
- Recently closed implemented but explicit device QA pending.
- Language selector/change PASS; persistence after reopen/restart not separately confirmed.
- Secure GitHub log-report shortcut later; never embed GitHub PAT/token/client secret.

## Current priority
1. On existing Build #236, verify manual 480p using technical reported height, not eyesight.
2. If actual height=480p and playback continues, PH core BG/quality blockers are cleared.
3. Then run one small HH technical smoke test: BG share, automatic prep, playback, Auto-quality sanity, and Vivaldi responsiveness. No content/imagery descriptions.
4. If HH passes, test Recently closed and language persistence after reopen/restart.
5. Then move to release hardening/regression rather than further architecture changes: PH/HH/share-target regression, operations-log/dead-path cleanup where safe, About/version/docs consistency, and later release signing/distribution decision.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
