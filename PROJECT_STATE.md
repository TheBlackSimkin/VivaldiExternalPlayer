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
#205 proved a stopped preparation Activity could be destroyed almost immediately even with foreground process importance.
#212 proved app-private virtual-display creation works, but Android denies launching a normal app **Activity** onto that display. Do not retry Activity launch there or request privileged `ACTIVITY_EMBEDDING`.
#227 proved a default-display transparent/nonfocusable preparation Activity remained nondeterministic and could freeze Vivaldi on repeated shares. Do not return to display-0 Activity tuning.

## Build #234 — private-display service architecture: DEVICE PASS
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

Repeated/multi-share device QA on #234 reported **no issues detected**. Supplied log confirmed the private-display path and no old display-0 preparation-Activity anchors. Decision: keep this architecture as protected baseline.

## Build #236 — current PH + HH core baseline: DEVICE PASS
App-code commit `d6c1328823ce2027beecab7970b02420d1cffc7b`; CI #236 PASS run `31858887503`; artifact `9239902382`; APK SHA-256 `ca24f6943849853d4ba6580ceaf107b9795ebc8b943dc55ac28cdab66b8c3bff`.

Compared with #234, only `ResolvedMedia.kt` changed. Automatic browser payloads are normalized before first playback source creation:
1. 720p if available;
2. else 1080p;
3. else highest below 1080p;
4. else smallest >1080p rare fallback.
Explicit numeric manual choices remain exact and are not normalized back to Auto.

### PH device QA — PASS
- repeated BG sharing / Vivaldi responsiveness: PASS;
- automatic 720-first with both 720p and 1080p available: PASS;
- manual quality switching including 480p: PASS;
- playback sanity: PASS.
User reported the final manual-quality check **“All worked perfectly.”** Do not reopen PH BG/quality blockers without a new regression.

### HH device smoke QA — PASS
User ran the requested HH technical smoke test on the **unchanged Build #236** and reported **“All OK.”** Treat the requested HH smoke dimensions as PASS: BG share/Vivaldi responsiveness, automatic preparation, playback, Auto-quality sanity, and manual quality behavior where applicable. No new HH-specific code was needed.

This is important evidence that the same #234/#236 private-display resolver/playback architecture generalizes across both current technical targets. Do not introduce target-specific architecture unless a concrete regression requires it.

## Current product/UI status
Verified working:
- PH core BG + playback + automatic/manual quality;
- HH technical smoke path;
- long-press tab reorder;
- closing tabs;
- resume position;
- tested Back flow;
- operations log;
- refreshed icon;
- language selector/change itself.

Still needing explicit device confirmation:
- Recently Closed behavior/restoration/clear flow;
- selected language persistence after app close/reopen/restart.

Later hardening/backlog:
- secure GitHub log-report shortcut; never embed PAT/token/client secret;
- regression pass over both Vivaldi share targets and PH/HH;
- operations-log noise cleanup;
- stale historical/dead-path cleanup where safe, without disturbing protected architecture;
- About/version/build consistency and documentation cleanup;
- release signing/distribution decision later; permanent signing remains deferred unless explicitly chosen.

## Current priority / what comes next
1. On unchanged Build #236, test **Recently Closed** end-to-end: close a tab, verify it appears, restore it, then test clear behavior.
2. Test **language persistence**: change language, fully close/reopen the app (and if useful a normal process restart), verify selected language remains.
3. If both pass, move into release-hardening/regression rather than feature/blocker repair.
4. Do not change the private-display BG architecture or 720-first parser unless a real regression appears.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
