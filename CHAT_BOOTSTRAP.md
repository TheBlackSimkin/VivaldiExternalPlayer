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

## Build #202 architecture recap
App-code head: `b02561ed0c154b7f8abe66bd4e3212ba780b7fdf`.

`BG - External Player` routes to exported `BackgroundShareActivityV2`:
- creates persistent tab and marks preparation immediately at share time;
- moves its transparent excluded document task behind Vivaldi;
- direct yt-dlp first;
- 12-second budget requests automatic hidden browser fallback even if direct is still blocking;
- resolver.py uses `socket_timeout=12`, `retries=1`, `extractor_retries=1`, `fragment_retries=1`;
- hidden WebView is technically VISIBLE and normally laid out;
- browser signals include WebView requests, Service Worker, DOM VIDEO/SOURCE, Performance resources, and page-config URLs/qualities;
- Service Worker browser discovery is serialized across BG tabs;
- no PlayerActivity/ExoPlayer/background playback is created by BG preparation.

Build #202 CI PASS: run `31830708434`, artifact `9230598176`, APK SHA-256 `dab7f1a312d838e966eb552867679b696f887cf6a71a94c2d0c354fd99458114`.

## Build #202 PH device QA — authoritative FAIL for automatic completion
User tested three PH BG shares without clicking/opening individual cards while preparation was running.

Results:
- after ~120s before first opening ExternalPlayer, all three were still `Preparing`;
- around another ~120s they started showing errors/other methods;
- around another ~120s they all ended `Paso del Navegador` / `NEEDS_ATTENTION`;
- final visible tech marker was NEEDS_ATTENTION on all three;
- user never observed ERROR or BROWSER_WAITING_FOR_SLOT, though some tech markers changed too quickly to read;
- no tab/card was clicked before the automatic process stopped, so share-time decoupling still works;
- manual `Paso del Navegador` then reached READY in about 5–7 seconds for each tested PH tab;
- absence from Android Recents is a PASS and is preferred by the user;
- the multi-minute preparation time is unacceptable for normal use. The 120-second wait was diagnostic only.

Current PH quality observations from #202:
- manual 240p change WORKS;
- manual 480p change DOES NOT WORK;
- Auto chooses 1080p even when 720p exists, violating the protected 720-first policy.

The icon was not changed in #202. Logo refinement is still recorded but deliberately remains outside the focused BG lifecycle fix.

Do not request HH testing yet.

## #202 failure diagnosis
The likely path is now concrete in code:
- V2 begins correctly at share time;
- if Android destroys V2 while it is still RESOLVING, #202 `onDestroy()` marks `BG_HOST_DESTROYED_RECOVERY_QUEUED` and enqueues WorkManager;
- the Worker can do direct resolution but cannot own a WebView;
- on ordinary miss it records browser-stage-needed and waits for `UnifiedPreparationCoordinator` to get an Activity lifecycle;
- the old `BackgroundPreparationActivity` can then run and its browser timeout still ends as NEEDS_ATTENTION.

This can turn a correct V2 start into the old multi-minute Browser-Step path, matching the device result.

## Post-#202 BG lifecycle fix — implementation staged, CI pending
Focused successor architecture:

### Foreground preparation keep-alive
- add `BackgroundPreparationKeepAliveService` as a short-lived foreground `dataSync` service;
- start it from the lifecycle callback for the **user-launched V2 Activity**, after V2's own `onCreate()` has already created/started the tab but before the posted `moveTaskToBack()` runs;
- service owns no WebView and no ExoPlayer; V2 remains the actual preparation owner;
- Android shows a low-priority preparation notification while the service is active;
- each V2 Activity has a lease; service stops after the last lease ends.

### Stop normal BG shares from silently reverting to old recovery
- if V2 is destroyed unresolved, cancel the WorkManager recovery V2 just queued and turn the tab into explicit ERROR with `BG_HOST_DESTROYED_NO_LEGACY_FALLBACK`;
- if the whole process dies, startup detects a restored V2 session using its host/WebView timestamps and converts it to ERROR before `resumePending()` can enqueue the old Worker;
- explicit retry/preload paths can still use the old coordinator; this rule is for normal BG shares.

### Exportable operations log
User requested a persistent backstage log because tech markers change too quickly.
- Settings gains `Share operations log` / `Compartir registro de operaciones`;
- export is ordinary text via Android share sheet, so WhatsApp can be selected when installed;
- records Activity lifecycle, foreground-service leases/lifetime, tab state/tech/timestamps, bounded technical errors;
- never writes thumbnails, frames, page/body text, resolved media payloads, request headers, cookies, authorization or credentials; common credential-shaped strings are redacted.

## Current quality / UI backlog
Keep these current after the BG blocker:
- strict initial 720 preference is currently broken on PH; candidate scoring can let 1080 win despite the policy;
- manual 240 works but manual 480 fails on tested PH path;
- explicit app-language selector still requested in Settings;
- launcher/logo refinement still requested: preserve white-E/purple identity, make less boxy/more refined, make purple more prominent.

## Current priority
1. Finish and CI the foreground keep-alive + no-legacy-fallback + operations-log successor.
2. Inspect `main` after CI: V2 must still start preparation in its `onCreate()` at BG-share time; keep-alive acquisition must happen from V2 creation before it is moved behind Vivaldi.
3. Do not designate an APK unless CI passes and that code path is verified.
4. PH-only focused QA with 2–3 BG shares; no tab/card clicks before observation.
5. If failure occurs, share/export the operations log before pressing Browser Step.
6. Do not test HH until PH automatic BG preparation passes.
7. Then fix strict 720 preference + 480 switching, test HH separately, add app-language selector, and later refine icon.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
