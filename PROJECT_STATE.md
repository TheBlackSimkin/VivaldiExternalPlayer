# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA results, failures, decisions, or priorities change.

## Working / safety rules
- Conversation English; Android UI bilingual (English/Spanish). Explain plainly; user is not an advanced developer.
- Use connected GitHub tools directly. Source should contain abundant English comments.
- PH and HH are technical playback targets. URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed.
- Do **not** inspect, describe, classify, summarize, or request PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected playback baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p; >1080 only rare fallback.
Preserve Vivaldi share targets; yt-dlp first/browser fallback; automatic/manual quality; video+audio; adaptive/sibling switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate limits/order; page-config families; no imagery-based ranking; exactly one actual ExoPlayer playback session.
Permanent release signing remains deferred. Debug GitHub Actions APKs are the QA path; never commit a permanent signing key.

## BG architecture history / protected decision
- #205: stopped preparation Activity could be destroyed almost immediately even with foreground process importance.
- #212: app-private virtual display creation worked, but Android denied launching a normal app Activity onto it; do not retry or request privileged `ACTIVITY_EMBEDDING`.
- #227: transparent/nonfocusable preparation Activity on display 0 still froze Vivaldi on repeated shares; do not return to display-0 Activity tuning.
- #234: service-owned private `Presentation`/WebView on app-private virtual display passed repeated/multi-share device QA. This architecture is protected.

Normal BG path:
`short share Activity -> persistent pending tab -> foreground service -> private virtual display -> service-owned Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.
No PlayerActivity/Media3/ExoPlayer exists during preparation.

## Build #236 — current playback baseline: DEVICE PASS
App-code commit `d6c1328823ce2027beecab7970b02420d1cffc7b`; CI #236 PASS; APK SHA-256 `ca24f6943849853d4ba6580ceaf107b9795ebc8b943dc55ac28cdab66b8c3bff`.

PH device QA PASS:
- BG share / Vivaldi responsiveness;
- automatic 720-first when 720p and 1080p are both available;
- manual quality switching including 480p;
- playback sanity.

HH technical smoke on unchanged #236: PASS for BG responsiveness, automatic preparation, playback and quality sanity. No target-specific HH code was required.

Product-state QA on unchanged #236: PASS for Recently Closed close/restore/clear behavior and language persistence after reopen/restart. User explicitly reported that Recently Closed worked but its UI was poor.

Do not change the private-display BG architecture or 720-first quality parser without a concrete new regression.

## UI redesign direction — user approved, implementation pending device QA
The user requested a broad UI-improvement pass after core playback stabilized. Agreed direction:
- take only interaction/layout inspiration from Vivaldi Android tab distribution; do not clone its visual design;
- open tabs: thumbnail-first grid, **2 columns portrait / 3 columns landscape**;
- remove technical lifecycle text from normal tab cards; keep it in diagnostics/operations log;
- Recently Closed: dedicated grid visually related to open tabs, keep thumbnails, with permanently visible **Recover all** and **Delete all** actions;
- Settings: grouped cards/rows, not a stack of plain text buttons; About moves under Settings;
- main screen: tabs are primary; manual URL entry becomes collapsible/secondary; Settings becomes compact gear access;
- empty/loading/ready/error/browser-step states should look intentional and plain-language;
- player: keep playback behavior, but move Quality + Diagnostics inside one gear menu; existing tab access becomes a square count immediately left of the gear;
- quality selector/browser-assisted resolver/diagnostics/About should share one visual language;
- consistent primary/secondary/destructive button hierarchy, icons, restrained dark graphite palette and minimal animation;
- use a clean Android system sans-serif family; no font file needs to be bundled;
- screen may be tall; density is secondary to clear tab grids and touch targets;
- overall direction: dark, clean, media-oriented, slightly technical but not developer-looking.

### Current UI implementation
A UI-only implementation is now staged on top of current `main` while preserving #236 playback logic. It includes:
- redesigned `MainActivity`/`activity_main.xml` grid dashboard + collapsible manual URL;
- redesigned `TabDashboardAdapter` without lifecycle markers;
- dedicated `RecentlyClosedActivity` + thumbnail grid adapter;
- thumbnail cache retention while a tab lives in Recently Closed;
- grouped Settings/About UI;
- UI-only `PlayerChromeProvider` that reuses existing PlayerActivity Quality/Diagnostics click handlers and restyles the existing tab button, without touching ExoPlayer/resolver logic;
- refreshed browser-assisted resolver layout;
- shared icon/shape/string/theme resources.

This implementation must pass CI and then receive visual/device QA before becoming the new UI baseline.

## Remaining backlog after UI pass
- user review/iteration on visual direction;
- final PH/HH + both Vivaldi share-target regression after UI settles;
- operations-log noise cleanup and dead-path cleanup where proven safe;
- secure GitHub log-report shortcut later; never embed PAT/token/client secret;
- version/About/docs cleanup and eventual release signing/distribution decision.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
