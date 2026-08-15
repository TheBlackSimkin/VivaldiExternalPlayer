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
Preserve Vivaldi share targets; yt-dlp first/browser fallback; automatic/manual quality; video+audio; adaptive/sibling switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate limits/order; page-config families; no imagery-based ranking; exactly one actual ExoPlayer playback session.
Permanent release signing remains deferred. Debug GitHub Actions APKs are the QA path; never commit a permanent signing key.

## BG architecture history
#205: stopped preparation Activity could be destroyed almost immediately even with foreground process importance.
#212: app-private virtual display creation worked, but Android denied launching a normal app **Activity** onto it; do not retry Activity launch or request privileged `ACTIVITY_EMBEDDING`.
#227: default-display transparent/nonfocusable preparation Activity remained nondeterministic and could freeze Vivaldi on repeated shares; do not return to display-0 Activity tuning.

## Build #234 — service-owned private Presentation/WebView: DEVICE PASS
App-code head `6cd8995ba615b8b70f83806bad9abca49a024034`; Actions #234 PASS; APK SHA-256 `b6d921b2b1dd5f19c9c4b7b1763aad03476a901dc434ed9a05d84bb8a126c351`.

Normal `BG - External Player` path:
`short share Activity -> persistent pending tab -> foreground service(token/tab/url) -> finishAndRemoveTask() -> app-private virtual display -> service-owned Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.

Protected architecture facts:
- no normal preparation Activity on display 0;
- foreground service owns `BackgroundPrivateDisplayPreparationSession` objects;
- private displays use `OWN_CONTENT_ONLY | PRESENTATION`;
- WebView lives in non-Activity `Presentation` with `TYPE_PRIVATE_PRESENTATION`;
- browser discovery stays serialized because `ServiceWorkerController` is process-wide;
- no PlayerActivity/Media3/ExoPlayer during preparation;
- no privileged embedding/overlay permission or access-control bypass.

Repeated/multi-share device QA on #234 reported **no issues detected**. Supplied log confirmed the private path and no old display-0 prep Activity anchors in the excerpt. Decision: keep this architecture.

## Build #236 — PH core baseline: DEVICE PASS
App-code commit `d6c1328823ce2027beecab7970b02420d1cffc7b`; CI #236 PASS run `31858887503`; artifact `9239902382`; APK SHA-256 `ca24f6943849853d4ba6580ceaf107b9795ebc8b943dc55ac28cdab66b8c3bff`.

Compared with #234, only `ResolvedMedia.kt` changed. Automatic browser payloads are normalized before first playback source creation:
1. 720p if available;
2. else 1080p;
3. else highest below 1080p;
4. else smallest >1080p rare fallback.
Explicit numeric manual choices remain exact and are not normalized back to Auto.

### #236 device QA — PH CORE PASS
User reported **no issues**. In a case where both 1080p and 720p were available, playback started at **720p**, closing the #225 Auto-1080 contradiction. User also reported manual switching to other qualities worked.

Final PH manual-quality check was then repeated technically on the same #236 build and the user reported: **“All worked perfectly.”** Treat manual 480p as PASS: it was available/selectable, playback continued, and no app problem was observed. Do not reopen the 480 blocker without a new regression.

Supplied #236 operations log identifies exact binary (`Git: d6c13288`, Actions 236) and again confirms the validated private-display BG architecture (`VIRTUAL_DISPLAY_CREATED ... private=true presentation=true`, `PRIVATE_PRESENTATION_CREATED ... defaultDisplay=false type=PRIVATE_PRESENTATION`). The excerpt stops before browser completion/READY; do not invent missing timestamps.

### PH status
The previously blocking PH items are now closed on-device:
- BG share preparation without Vivaldi freeze: PASS;
- automatic 720-first when 720 and 1080 are both available: PASS;
- manual quality switching including 480p: PASS;
- playback sanity: PASS.

Build #236 is now the protected PH baseline. Do not make further PH BG/quality architecture changes unless a real regression appears.

## Current UI/backlog
- long-press tab reorder WORKS;
- closing tabs WORKS;
- resume position WORKS;
- tested Back flow WORKS;
- operations log PASS/useful;
- icon PASS;
- language selector/change PASS; reopen persistence still needs explicit confirmation;
- Recently closed implemented but explicit device QA pending;
- secure GitHub log-report shortcut later; never embed PAT/token/client secret.

## Current priority / what comes next
1. Use existing **Build #236** for one small **HH technical smoke test**: BG share, automatic preparation, playback, Auto-quality sanity, and Vivaldi responsiveness. Do not inspect or describe media imagery/content.
2. If HH passes, test remaining product-state items: Recently closed behavior and language persistence after app reopen/restart.
3. Then move from blocker-fixing to release hardening: regression pass over PH/HH/share targets, operations-log cleanup, stale historical/dead-path cleanup where safe, About/version consistency, documentation, and later decide release signing/distribution.
4. Keep permanent signing deferred unless explicitly chosen.
5. Do not make further BG architecture changes unless a real regression appears; #234/#236 private-display path is protected baseline.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
