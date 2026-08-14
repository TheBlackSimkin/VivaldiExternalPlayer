# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA, failures, decisions, or priorities change.

## Working rules
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Source should contain abundant English comments.
- Use connected GitHub tools directly whenever possible.
- PH and HH are real technical playback targets tested by the user. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed. Do not inspect/describe media content or thumbnail imagery; never ask user for PH/HH titles or thumbnails.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p; >1080 only rare fallback.

Protect Vivaldi share; yt-dlp first/browser fallback; automatic best/manual fallback; video+audio; adaptive and sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; 80 stored/20 manual candidate limits; first-seen HLS/DASH order; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no imagery-based resolver/ranking; one actual ExoPlayer playback session.

Previously verified core baseline includes Bitmovin/PH/HH playback, build #62 follow-up, build #74 clean loading, and #109 install/About/Settings/no-background-playback/local browser title. Do not repeat old PASS items without a regression reason.

Permanent release signing remains deliberately deferred. Debug GitHub Actions APKs are the QA path; never commit the permanent key.

## Known history before #192
- #143 added local thumbnails, chooser labels `ExternalPlayer` / `BG - External Player`, dark UI, Settings/About polish, and the white-E / purple-diamond launcher identity.
- #162 introduced the first-class dashboard, unified preparation concept, and persisted Manual vs Actual quality.
- #162/#187 real-device regressions included foreground share not visibly raising the app; BG shares staying queued until individual tabs were clicked; swipe close failure; Back exposing browser chooser; HH manual quality not changing rendition; slow buffering; weak pending-tab identification; uncomfortable arrow reorder; poor thumbnail timing.
- #187 code attempted a second hidden `BackgroundPreparationActivity` hand-off. Device QA proved that hand-off unreliable. Each individual pending tab still had to be clicked before its own preparation began.

## #192 BG architecture
App-code commit: `a85f03a30ffdcda6d3f3c1c130ee799fd523e3f0`.

Normal `BG - External Player` was redesigned so exported `BackgroundShareActivity` owns preparation itself:
- creates a persistent tab immediately;
- records PREPARATION_REQUESTED / host creation / RESOLVING before dashboard use;
- moves its own transparent document task behind Vivaldi rather than launching a second preparer;
- starts direct/yt-dlp independently of MainActivity/dashboard selection;
- ordinary direct miss starts the same Activity's WebView browser-discovery stage;
- local title is saved on browser success;
- READY starts best-effort thumbnail extraction;
- no ExoPlayer/background playback is added;
- `android:noHistory` is not used on the BG host;
- each explicit BG share receives its own excluded document task.

Dashboard coupling was intentionally removed:
- READY -> Play/Continue;
- NEEDS_ATTENTION -> Browser Step only when real interaction is required;
- ERROR -> explicit recovery;
- QUEUED/RESOLVING -> disabled/inert; clicking a pending card cannot start normal BG preparation and hide a lifecycle failure.

Technical lifecycle markers are persisted and shown locally as `tech ... +Xs`; they contain lifecycle metadata only, not media content/imagery/page text/credentials.

## Build #192 CI
- GitHub Actions run #192 PASS; run ID `31820367544`.
- Debug artifact ID `9226733915`.
- Artifact ZIP digest `sha256:76b1d2ff8c9d929d995c91d563bdb4d833996f4d50253cc29adee1dc65763a04`.
- Extracted APK SHA-256 `89cafe287d0f3bcf6a63b545efaa9a89ae936e0a42ee50eb2b6f6d6a7a997960`.
- CI proves compile/package integrity only, not runtime behavior.

## Share-entry semantics
- `ExternalPlayer` chooser target is exported `ForegroundShareActivity`, which explicitly starts/raises `MainActivity` with the shared URL. `MainActivity.acceptSharedUrl()` enters normal visible `resolveAndPlay()`.
- Current `BG - External Player` chooser target is exported `BackgroundShareActivityV2`; it is a real launched Activity but intentionally moves its task behind Vivaldi.
- BG host is `android:excludeFromRecents="true"`; absence from Android Recents is expected and is explicitly preferred by the user.

## Build #192 PH device QA — authoritative
User tested three PH links shared through `Compartir enlace` -> `BG - External Player`.

Important improvement versus #187:
- after later opening ExternalPlayer once, **all three tabs were already actively in `Preparando` without the user first clicking each individual tab**. This proved the self-owned share path materially removed the old tab-click-to-start coupling.

Remaining failure:
- preparations did not finish automatically;
- each took roughly **244–270 seconds**;
- all ended `Falta paso del navegador` / `NEEDS_ATTENTION`;
- manually clicking `Paso del navegador` succeeded in roughly **5–10 seconds** per tested PH URL.

Code inspection after #192 found:
- browser timeout itself was only 22 seconds, so most delay happened before it in direct/yt-dlp;
- visible `BrowserResolverActivity` observed Service Worker requests while #192 BG did not;
- #192 BG WebView was `INVISIBLE` and 1x1 rather than normally laid out.

### Browser-fallback architectural decision after #192 QA
Do **not** fake a literal UI click on `Paso del navegador`.

Desired behavior:
1. direct/yt-dlp first but bounded;
2. ordinary miss/budget expiry -> automatic browser discovery behind Vivaldi;
3. BG discovery stores READY metadata only and never launches PlayerActivity/ExoPlayer/background playback;
4. ordinary browser timeout/failure -> ERROR;
5. NEEDS_ATTENTION / Browser Step only for genuine human/protected interaction;
6. process-wide Service Worker observation must avoid cross-tab attribution.

## Build #202 implementation
App-code head: `b02561ed0c154b7f8abe66bd4e3212ba780b7fdf`.

Commits:
- `b970a60e4ab639feb550695ad69815f85aa06a02` — add `BackgroundShareActivityV2`;
- `08a8729b87498f68b6edce68bdd75cda93799dd7` — route BG chooser to V2;
- `b02561ed0c154b7f8abe66bd4e3212ba780b7fdf` — bound direct resolver socket/retry behavior.

V2 behavior:
- creates/marks persistent tab at share time;
- direct yt-dlp first;
- 12-second budget requests automatic browser fallback even if direct is still blocking;
- resolver.py uses `socket_timeout=12`, `retries=1`, `extractor_retries=1`, `fragment_retries=1`;
- hidden WebView is technically VISIBLE and normally laid out, while the Activity is behind Vivaldi;
- `mediaPlaybackRequiresUserGesture=true`; no PlayerActivity or ExoPlayer is created;
- discovery observes WebView requests, Service Worker requests, DOM VIDEO/SOURCE, Performance resources, and page config;
- hidden browser discovery is serialized because ServiceWorkerController is process-wide.

### Build #202 CI / artifact
- run #202 PASS; run ID `31830708434`;
- artifact ID `9230598176`;
- artifact ZIP digest `sha256:6b08abecf71c650b8844d44ba4bc664642127d986bee009fe1b2318fee95302a`;
- APK SHA-256 `dab7f1a312d838e966eb552867679b696f887cf6a71a94c2d0c354fd99458114`.

## Build #202 PH device QA — authoritative FAIL for automatic completion
User tested three PH BG shares without opening/clicking individual cards while preparation was running.

What #202 proved:
- share-time decoupling still works: no tab/card was clicked before the automatic process stopped;
- BG entries stayed out of Android Recents, a PASS and the user's preferred behavior;
- therefore opening an individual tab is no longer the trigger which begins preparation.

What failed:
- after about **120 seconds** before first opening ExternalPlayer, all three were still `Preparing`;
- after another ~120 seconds they began visibly cycling through errors/other methods;
- after another ~120 seconds (about six minutes total) all three ended `Paso del Navegador` / `NEEDS_ATTENTION`;
- final visible tech marker was `NEEDS_ATTENTION` for all three;
- user never observed `ERROR` or `BROWSER_WAITING_FOR_SLOT`, though intermediate markers changed too quickly to read;
- manually clicking `Paso del Navegador` then reached READY in about **5–7 seconds** per tab.

The multi-minute automatic route is unacceptable for normal use. The 120-second wait was diagnostic only, not a desired UX target.

Additional #202 PH findings:
- manual **240p works**;
- manual **480p does not work**;
- Auto chooses **1080p even when 720p is available**, violating the protected policy;
- launcher icon remained unchanged. Logo refinement was deliberately deferred, but the user explicitly reminded us it is still outstanding.

Do not test HH yet. PH automatic BG completion remains the blocker.

## #202 failure diagnosis — silent fallback into old architecture
Code inspection found a path matching the device timing:
1. V2 starts correctly at share time.
2. If Android destroys V2 while RESOLVING, #202 V2 `onDestroy()` marks `BG_HOST_DESTROYED_RECOVERY_QUEUED` and enqueues `TabPreparationManager` / WorkManager.
3. The Worker can do direct resolution but cannot own a WebView.
4. On direct miss it records browser-stage-needed and waits for `UnifiedPreparationCoordinator` to obtain an Activity lifecycle.
5. The coordinator can launch older `BackgroundPreparationActivity`, whose browser timeout still ends NEEDS_ATTENTION / Browser Step.

Thus #202 could begin correctly, then silently revert to the old multi-minute path. This explains why runtime behavior contradicted V2's intended 30-second ordinary-browser ERROR semantics.

## Build #205 implementation — protect V2 lifetime and expose operations log
App-code head: `48605a4c1eb8972d6275478993db5ce7b104478e`.

### Foreground preparation keep-alive
- Added `BackgroundPreparationKeepAliveService`, declared foreground `dataSync`.
- It is a **process-lifetime lease only**. It owns no WebView, no Media3, no PlayerActivity and no ExoPlayer.
- `BackgroundShareActivityV2` remains the owner of direct + hidden-browser preparation.
- Application lifecycle acquires one keep-alive lease for each user-launched V2 share.
- Android may show a low-priority foreground preparation notification while the lease is active; this is separate from Android Recents.
- The service stops after the last V2 lease is released.

### No silent V2 -> legacy recovery
- V2's existing `onDestroy()` may briefly queue its #202 WorkManager recovery, but the application V2-destroy callback immediately cancels that scheduled recovery and converts the tab to explicit technical ERROR with `BG_HOST_DESTROYED_NO_LEGACY_FALLBACK`.
- Whole-process death bypasses Activity `onDestroy()`. On next process start, `VideoTabStore.initialize()` first converts stale RESOLVING to `PROCESS_RESTART_QUEUED`; application startup detects V2 host + WebView timestamps and converts it to `PROCESS_RESTART_BG_HOST_ERROR` **before** `TabPreparationManager.resumePending()` can revive the old Worker.
- Explicit retry/preload recovery remains available separately; normal BG shares no longer silently rely on it.

### Exportable operations log
Added persistent `OperationLog` plus Settings button `Share operations log` / `Compartir registro de operaciones`.

The log records technical lifecycle/state only:
- BG Activity created/started/resumed/paused/stopped/destroyed;
- foreground keep-alive service/lease lifetime;
- tab preparation state, tech marker and timing fields;
- bounded technical error text.

It intentionally does **not** log thumbnails, media frames, page/body text, resolved media payloads, request headers, cookies, authorization values or credentials. Common credential-shaped strings are redacted. Export uses Android's normal text share sheet, so WhatsApp can be selected when installed.

## Build #205 code-path inspection — PASS
Inspection of committed `main` after implementation confirms preparation is scheduled/started at BG-share time, not tab-open time:
- `BackgroundShareActivityV2.onCreate()` creates the pending tab, marks PREPARATION_REQUESTED / host / RESOLVING, creates/configures its WebView, queues its task move behind Vivaldi, calls `attemptDirectFirst()`, and schedules the 12-second fallback timer.
- `TabbedPlayerApplication.onActivityCreated(BackgroundShareActivityV2)` runs after V2 `onCreate()` returns and requests the foreground keep-alive for that user-launched share before the queued `moveTaskToBack()` executes.
- MainActivity/dashboard-card selection is not part of that startup sequence.
- Keep-alive service source contains no PlayerActivity/Media3/ExoPlayer ownership; it only protects process lifetime and journals state.

## Build #205 CI / focused QA artifact
- GitHub Actions **run #205 PASS**.
- Run ID: `31838190231`.
- App-code commit: `48605a4c1eb8972d6275478993db5ce7b104478e`.
- Debug artifact ID: `9233311193`.
- Artifact ZIP size: `25,991,863` bytes.
- Artifact ZIP digest: `sha256:c9b8473fab112aaa525111505389726724e50be8979d993777fa1355bccc930b`.
- Extracted debug APK size: `35,486,970` bytes.
- Extracted APK SHA-256: `635b100073068f1062ef660fb9b61d46fb62613e7c71aa4e3814c158aeb71d72`.
- #205 is the designated focused PH BG-lifecycle QA APK. Later state-only commits do not supersede its app code.
- CI proves compile/package integrity only; device QA is still required.

## Current quality status / later fix
Protected policy remains exact 720 -> otherwise 1080 -> otherwise best below 1080. #202 PH QA proves current runtime violates it by initially choosing 1080 when 720 exists.

Code clue:
- browser candidate scoring gives 720 only a small advantage over 1080, so other candidate factors can still make a 1080 sibling rank first;
- Media3 adaptive `PlayerActivity` policy explicitly prefers 720 when the same adaptive group exposes it;
- manual 240 works while manual 480 fails on the tested PH path.

Do not mix a speculative quality rewrite into #205 BG lifecycle proof. Once PH BG preparation passes, fix/verify strict initial family quality selection and 480 switching before HH regression testing.

## Current UI observations / backlog
- long-press tab move/reorder WORKS from #192;
- closing tabs WORKS from #192;
- resume position WORKS from #192;
- tested Back flow after manual Browser Step WORKS from #192;
- Settings still needs explicit app-language selector;
- #202 BG absence from Android Recents PASS and preferred.

## Future logo / launcher direction
Still required after the BG blocker:
- preserve white-E / purple identity and recognizable family;
- make it less square/boxy and more stylized/refined;
- make purple portions more prominent.
The user explicitly reminded us after #202 that the icon is unchanged. Do not mix this visual change into the focused #205 BG lifecycle QA.

## Current development priority
1. Device-test build #205 with PH only; no HH yet.
2. Share 2–3 PH links through `BG - External Player` without opening/clicking their cards.
3. Keep Vivaldi foreground. Absence from Android Recents remains expected/preferred.
4. Use a practical initial observation window (about 60 seconds for three shares), not the old 120-second diagnostic wait as a normal expectation.
5. Target: tabs prepare automatically before ExternalPlayer/card open; no manual Browser Step needed.
6. If a tab is not READY or ends ERROR/NEEDS_ATTENTION, export `Settings -> Share operations log` **before** pressing Browser Step so the backstage sequence is preserved.
7. NEEDS_ATTENTION remains valid only for a genuine detected protected/human interaction; an interrupted V2 lifecycle should now be explicit ERROR rather than hidden legacy retry.
8. If PH #205 passes, record QA in both state files, then fix strict 720 preference + 480 switching and test HH separately.
9. Add explicit app-language choice after BG blocker.
10. Later perform recorded launcher/logo refinement.

## QA format
Whenever asking user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
