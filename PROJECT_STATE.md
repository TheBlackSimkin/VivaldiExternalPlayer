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
- BG host is `android:excludeFromRecents="true"`; absence from the Android square/Recents screen is expected and is now explicitly preferred by the user.
- Portuguese `segundo plano` is naturally “the background” in this context.

## Build #192 PH device QA — authoritative
User tested three PH links shared through `Compartir enlace` -> `BG - External Player`.

Important improvement versus #187:
- after later opening ExternalPlayer once, **all three tabs were already actively in `Preparando` without the user first clicking each individual tab**. This proves the #192 self-owned share path materially removed the old tab-click-to-start coupling.

Remaining failure:
- the three preparations did not finish automatically;
- each took roughly **244–270 seconds** from creation to the final visible failure marker;
- all ended `Falta paso del navegador` / `NEEDS_ATTENTION` with the Browser Step button;
- clicking `Paso del navegador` manually then succeeded in roughly **5–10 seconds** for each tested PH URL;
- after successful visible browser resolution, Back returned to the tab dashboard and the tab contained its information correctly.

Code inspection after this result found the likely divergence:
- #192 browser timeout itself was only 22 seconds, so most delay happened before that in the direct/yt-dlp stage;
- visible `BrowserResolverActivity` observes Service Worker requests while #192 BG did not;
- #192 BG WebView was `INVISIBLE` and only 1x1, unlike the normally laid-out visible Browser Step.

### Browser-fallback architectural decision after #192 QA
Do **not** solve this by literally faking a UI click on `Paso del navegador`.

The intended behavior is equivalent from the user's perspective but cleaner technically:
1. direct/yt-dlp remains first but is bounded enough that it cannot monopolize several minutes;
2. on ordinary miss/budget expiry, automatically run browser discovery behind Vivaldi;
3. BG discovery stores READY metadata only and never launches PlayerActivity/ExoPlayer or produces background audio/video;
4. ordinary browser discovery timeout/failure is technical ERROR, not NEEDS_ATTENTION;
5. NEEDS_ATTENTION / `Paso del navegador` is reserved for genuine user interaction such as CAPTCHA/challenge/login/payment/DRM/region controls that automation must not bypass;
6. process-wide Service Worker observation must avoid cross-tab request attribution.

## Build #202 implementation — automatic Browser Step semantics behind Vivaldi
App-code head: `b02561ed0c154b7f8abe66bd4e3212ba780b7fdf`.

Three app-code commits produced #202:
- `b970a60e4ab639feb550695ad69815f85aa06a02` — add `BackgroundShareActivityV2`;
- `08a8729b87498f68b6edce68bdd75cda93799dd7` — route `BG - External Player` manifest target to V2;
- `b02561ed0c154b7f8abe66bd4e3212ba780b7fdf` — bound direct resolver socket/retry behavior.

### V2 lifecycle
`BG - External Player` launches `BackgroundShareActivityV2`, which retains the self-owned share/task behavior that starts without card clicks.

Direct stage:
- yt-dlp/direct starts first;
- after 12 seconds, if normal preparation has not completed, V2 requests browser fallback even if the blocking direct call has not returned yet;
- resolver.py uses `socket_timeout=12`, `retries=1`, `extractor_retries=1`, `fragment_retries=1`;
- hard DRM/paywall/subscription/purchase/geo markers remain hard stops;
- challenge/login-style extractor messages are normalized into ordinary browser assistance rather than treated as bypassable access controls.

Automatic browser stage:
- there is no fake UI click;
- the hidden WebView is technically `VISIBLE`, normally laid out at Activity size, and the entire transparent Activity task is moved behind Vivaldi before browser loading;
- `mediaPlaybackRequiresUserGesture=true`; no PlayerActivity or ExoPlayer is created;
- discovery observes ordinary WebView requests plus Service Worker requests, DOM VIDEO/SOURCE URLs, Performance API resources, and page-config URLs/declared qualities;
- candidate protections remain: max 80, first-seen HLS/DASH preference, page-config family IDs, ad demotion, soft audio/video child demotion, no imagery decisions;
- exact cookie/18+ automation remains conservative; CAPTCHA/challenge/login/payment/subscription/DRM/region controls are not automated.

Multiple BG tabs:
- Android ServiceWorkerController is process-wide, so hidden browser discovery is serialized into one browser slot at a time;
- direct attempts can still run independently;
- waiting tabs may show `tech BROWSER_WAITING_FOR_SLOT` and should start browser discovery when the previous BG browser releases the slot.

Intended #202 state semantics:
- browser success -> READY;
- explicit detected protected/human-interaction challenge -> NEEDS_ATTENTION;
- ordinary automatic browser timeout/no candidate -> ERROR, not NEEDS_ATTENTION;
- dashboard card clicks remain irrelevant for QUEUED/RESOLVING normal BG work.

### Build #202 CI / artifact
- GitHub Actions **run #202 PASS**; run ID `31830708434`.
- Debug artifact ID `9230598176`.
- Artifact ZIP size `25,979,380` bytes.
- Artifact ZIP digest `sha256:6b08abecf71c650b8844d44ba4bc664642127d986bee009fe1b2318fee95302a`.
- Extracted debug APK size `35,466,650` bytes.
- Extracted APK SHA-256 `dab7f1a312d838e966eb552867679b696f887cf6a71a94c2d0c354fd99458114`.
- CI proves compilation/package integrity only.

## Build #202 PH device QA — authoritative FAIL for automatic completion
The user tested three PH BG shares without opening/clicking individual cards while preparation was running.

What #202 proved:
- share-time decoupling still works: **no tab/card was clicked before the automatic process stopped by itself**;
- the BG entries stayed out of Android Recents, which is a PASS and the user's preferred behavior;
- therefore opening an individual tab is no longer the trigger which begins preparation.

What failed:
- after waiting about **120 seconds** before first opening ExternalPlayer, all three tabs were still `Preparing`;
- after about another **120 seconds**, the tabs began visibly cycling through errors/other methods;
- after roughly another **120 seconds** (about six minutes total), all three ended `Paso del Navegador` / `NEEDS_ATTENTION`;
- final visible tech marker was `NEEDS_ATTENTION` for all three;
- the user never observed `ERROR` or `BROWSER_WAITING_FOR_SLOT`, although some intermediate tech markers changed too quickly to read;
- manually clicking `Paso del Navegador` then reached READY in about **5–7 seconds** per tested tab.

The approximately six-minute automatic route is unacceptable for normal use. The 120-second wait was a diagnostic test only, not a desired UX target.

Additional current PH findings from #202:
- manual change to **240p works**;
- manual change to **480p does not work**;
- Auto still chooses **1080p even when 720p is available**, violating the protected `720 -> 1080 -> below` policy;
- the launcher icon was not changed in #202. This was intentional because logo refinement remained deferred, but the user explicitly reminded us it is still outstanding.

Do not test HH yet. PH automatic BG completion remains the blocker.

## #202 failure diagnosis — lifecycle fallback escaped back into the old architecture
Code inspection after the device result found an important path which matches the observed timing:

1. V2 owns the intended direct + hidden-browser work while its Activity remains alive.
2. If Android destroys V2 while it is still RESOLVING, V2's `onDestroy()` in #202 marks `BG_HOST_DESTROYED_RECOVERY_QUEUED` and enqueues `TabPreparationManager` / WorkManager.
3. That Worker can do direct resolution but **cannot own a WebView**.
4. On an ordinary direct miss it records `WORKER_BROWSER_STAGE_NEEDED` and waits for `UnifiedPreparationCoordinator` to obtain a usable Activity lifecycle.
5. The coordinator can then launch the older `BackgroundPreparationActivity`, whose browser timeout still ends in NEEDS_ATTENTION / Browser Step.

This means #202 can begin correctly at share time but, after the BG Activity is interrupted, silently fall back into the older architecture. That explains why real-device behavior could take minutes and end in NEEDS_ATTENTION even though V2's own automatic browser timeout was only 30 seconds and was supposed to use ERROR on an ordinary miss.

## Post-#202 BG lifecycle fix — implementation staged, CI pending
The focused successor keeps V2 as the owner of actual preparation but adds process-lifetime protection and persistent diagnostics.

### Foreground preparation keep-alive
- A short-lived `BackgroundPreparationKeepAliveService` is started from the lifecycle of the **user-launched V2 BG share Activity before its posted `moveTaskToBack()` executes**.
- The service is declared as foreground `dataSync` because it protects user-requested network preparation only.
- It has no Media3/ExoPlayer/PlayerActivity code and cannot play audio/video.
- Android therefore shows a low-priority foreground-service notification while BG preparation is active; this is intentionally separate from Android Recents.
- Each V2 Activity owns one service lease; the service stops after the last lease is released.

### Do not hide V2 interruption behind legacy recovery
- If a live V2 Activity is destroyed while unresolved, its #202 WorkManager fallback is immediately cancelled and the tab becomes a technical ERROR instead of silently continuing through the old browser-step architecture.
- If the whole process dies and `VideoTabStore` restores a V2 session as `PROCESS_RESTART_QUEUED`, application startup recognizes the V2 host/WebView timestamps and converts that interrupted session to ERROR **before** `resumePending()` can enqueue the old Worker.
- Explicit retry/preload paths may still use the older recovery coordinator; this change is specifically about normal `BG - External Player` shares.

### Exportable operations log
The user requested a better way to report fast backstage states. The successor adds a persistent local operations journal and a Settings button to share it as plain text through Android's normal share sheet (including WhatsApp when installed).

The journal records technical lifecycle/state only:
- BG Activity created/started/resumed/paused/stopped/destroyed;
- foreground keep-alive leases/service lifetime;
- tab preparation state, tech marker, and preparation timestamps;
- bounded technical error text.

It intentionally does **not** log thumbnails, media frames, page/body text, resolved media payloads, request headers, cookies, authorization values, or credentials. Common credential-shaped strings are additionally redacted before writing.

## Current quality status / later fix
The protected policy remains exact 720 -> otherwise 1080 -> otherwise best below 1080. #202 PH QA proves current runtime behavior violates it by initially choosing 1080 when 720 exists.

Current code clue:
- browser candidate scoring gives 720 only a small score advantage over 1080, so other candidate factors can still make a 1080 sibling rank first;
- Media3's adaptive `PlayerActivity` policy itself explicitly prefers 720 when the same adaptive track group exposes it;
- manual 240 works while manual 480 fails on the current tested PH path.

Do not mix a speculative quality rewrite into the focused BG lifecycle proof. Once PH BG preparation passes, fix/verify strict initial family quality selection and the 480 switching failure before HH regression testing.

## Additional verified/current UI observations
- long-press tab moving/reorder: WORKS from #192;
- closing tabs: WORKS from #192;
- playback resumes from where the user left off: WORKS from #192;
- tested Back flow after manual Browser Step: WORKS from #192;
- Settings still needs an explicit **app language selector**;
- #202 BG absence from Android Recents: PASS and preferred.

## Future logo / launcher visual direction
Keep this requirement even while BG work has priority. For the next visual iteration:
- preserve the current letter/color identity (white E / purple scheme);
- keep it recognizable as the same logo family;
- make it less square/boxy and more stylized/refined;
- make the purple portions more noticeable/prominent.
The user explicitly reminded us after #202 that the icon is still unchanged. Do not mix this visual change into the focused BG lifecycle fix.

## Current development priority
1. Finish/CI the post-#202 foreground keep-alive + no-legacy-fallback + operations-log implementation.
2. Inspect `main` after CI to verify normal BG preparation is still started in `BackgroundShareActivityV2.onCreate()` at share time and that the foreground keep-alive is acquired from V2 creation **before** V2 is moved behind Vivaldi.
3. Verify CI passes before designating a QA APK.
4. PH-only device QA: share 2–3 BG tabs, do not open/click their cards, keep Vivaldi foreground, and expect automatic readiness on a practical timescale substantially shorter than the #202 multi-minute failure.
5. If the next test fails, export/share the operations log before pressing Browser Step so the exact backstage path is preserved.
6. Do not test HH until PH automatic BG preparation passes.
7. After PH BG passes, fix/verify strict 720 preference and the current 480 quality-switch failure, then use HH as a separate regression target.
8. Add explicit app-language choice in Settings after the BG blocker.
9. Later perform the recorded launcher/logo refinement.

## QA format
Whenever asking user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
