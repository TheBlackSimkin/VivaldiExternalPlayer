# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` before substantive work. Keep both state files current whenever QA, architecture, failures, priorities, or decisions change.

## Communication / safety
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Use connected GitHub tools directly. Source should contain abundant English comments.
- PH and HH are real technical targets. URLs/manifests/codecs/resolutions/request metadata/candidate ranking/states/errors/local titles are allowed. Never inspect/describe media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise best below 1080p. Preserve yt-dlp first/browser fallback; automatic best/manual fallback; adaptive/sibling quality handling; double-tap ±10s; seek preview; rotation; bilingual UI; 80 stored/20 manual candidate limits; first-seen HLS/DASH order; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no imagery-based resolver/ranking; one actual ExoPlayer playback session.

## Build #202 PH device QA — authoritative FAIL for automatic completion
Three PH BG shares were tested without opening/clicking individual cards while preparation was running.

Results:
- after ~120s before first opening ExternalPlayer, all three were still `Preparing`;
- around another ~120s they started showing errors/other methods;
- around another ~120s they all ended `Paso del Navegador` / `NEEDS_ATTENTION`;
- final visible tech marker NEEDS_ATTENTION on all three;
- no tab/card clicked before automatic processing stopped, so share-time decoupling still works;
- manual Browser Step then reached READY in about 5–7 seconds per PH tab;
- no Android Recents entry = PASS and preferred;
- multi-minute timing is unacceptable for normal use; 120s was diagnostic only.

Current #202 PH quality observations:
- manual 240p works;
- manual 480p does not;
- Auto chooses 1080p even when 720p exists, violating protected 720-first policy.

Icon remained unchanged in #202; refinement is still required later but is deliberately not mixed into the BG blocker.

Do not request HH testing yet.

## #202 failure diagnosis
The code explains how V2 could start correctly and still end in the old path:
- V2 `onDestroy()` while RESOLVING marks `BG_HOST_DESTROYED_RECOVERY_QUEUED` and enqueues WorkManager;
- Worker can do direct resolution but cannot own WebView;
- on direct miss it waits for `UnifiedPreparationCoordinator` to obtain an Activity;
- older `BackgroundPreparationActivity` can then run and still ends its browser timeout as NEEDS_ATTENTION.

This matches the device result better than V2's own intended 30-second ERROR behavior.

## Build #205 — current focused PH BG-lifecycle target
App-code commit: `48605a4c1eb8972d6275478993db5ce7b104478e`.

### BG architecture changes
- Added short-lived foreground `dataSync` `BackgroundPreparationKeepAliveService`.
- Service is process-lifetime protection/diagnostics only: no WebView ownership, no PlayerActivity, no Media3/ExoPlayer, no playback.
- V2 remains the actual preparation owner.
- One service lease is acquired for each user-launched V2 share from `TabbedPlayerApplication.onActivityCreated(BackgroundShareActivityV2)`.
- V2 `onCreate()` has already created/marked its tab and started direct preparation at that point; the keep-alive request happens before V2's queued `moveTaskToBack()` runs.
- Android may show a low-priority preparation notification while active; BG still remains excluded from Recents.

### Stop silent legacy fallback
- if V2 is destroyed unresolved, application callback cancels the WorkManager recovery V2 just queued and marks explicit technical ERROR `BG_HOST_DESTROYED_NO_LEGACY_FALLBACK`;
- if whole process dies, startup recognizes restored V2 host/WebView timestamps and converts it to `PROCESS_RESTART_BG_HOST_ERROR` before `resumePending()` can enqueue the old Worker;
- explicit retry/preload recovery remains separate.

### Exportable operations log
Settings now includes `Share operations log` / `Compartir registro de operaciones`.
- plain text via Android share sheet; WhatsApp can be selected if installed;
- records BG Activity lifecycle, keep-alive service/leases, tab states/tech/timestamps, bounded errors;
- never logs thumbnails, frames, page/body text, resolved payloads, headers, cookies, authorization or credentials; common credential-shaped values are redacted.

## Build #205 code-path inspection — PASS
Committed `main` was inspected after implementation:
- `BackgroundShareActivityV2.onCreate()` creates pending tab, marks preparation, creates/configures WebView, queues task-behind move, calls `attemptDirectFirst()`, and schedules the 12-second browser-fallback timer;
- `TabbedPlayerApplication.onActivityCreated(V2)` then requests the keep-alive for that share before the posted task-behind move executes;
- MainActivity/dashboard card open is not part of preparation startup;
- keep-alive service contains no playback ownership.

## Build #205 CI / artifact
- GitHub Actions run #205 **PASS**; run ID `31838190231`.
- Debug artifact ID `9233311193`.
- Artifact ZIP size `25,991,863` bytes.
- Artifact ZIP digest `sha256:c9b8473fab112aaa525111505389726724e50be8979d993777fa1355bccc930b`.
- Extracted APK size `35,486,970` bytes.
- APK SHA-256 `635b100073068f1062ef660fb9b61d46fb62613e7c71aa4e3814c158aeb71d72`.
- #205 is the designated focused PH BG-lifecycle QA APK. Later state-only commits do not supersede its app code.
- CI proves compile/package integrity only; device QA is required.

## Current quality / UI backlog
After PH BG passes:
- fix strict initial 720 preference (currently 1080 can win despite 720 existing);
- fix/verify manual 480 switching (240 currently works on tested PH path);
- test HH separately;
- add explicit app-language selector in Settings;
- later refine icon while preserving white-E/purple identity, making it less boxy/more refined and purple more prominent.

## Current priority
1. Test build #205 with PH only; no HH.
2. Share 2–3 links through `BG - External Player`; do not open/click cards first.
3. Keep Vivaldi foreground; no Recents entry remains expected/preferred.
4. Use about 60 seconds as the first focused observation window for three shares, not the old 120-second diagnostic wait as normal UX.
5. Target is READY before ExternalPlayer/card open, with no manual Browser Step.
6. If any tab is not READY or ends ERROR/NEEDS_ATTENTION, export `Settings -> Share operations log` **before** pressing Browser Step.
7. If PH #205 passes, update both state files, then address 720/480 and test HH separately.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
