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
- BG host is `android:excludeFromRecents="true"`; absence from the Android square/Recents screen is therefore expected by current design and is not a BG success/failure criterion.
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

Three app-code commits produced this focused successor:
- `b970a60e4ab639feb550695ad69815f85aa06a02` — add `BackgroundShareActivityV2`;
- `08a8729b87498f68b6edce68bdd75cda93799dd7` — route `BG - External Player` manifest target to V2;
- `b02561ed0c154b7f8abe66bd4e3212ba780b7fdf` — bound direct resolver socket/retry behavior.

### V2 lifecycle
`BG - External Player` now launches `BackgroundShareActivityV2`, which retains the self-owned share/task behavior that #192 device QA proved starts without card clicks.

Direct stage:
- yt-dlp/direct still starts first;
- after 12 seconds, if normal preparation has not completed, V2 automatically requests browser fallback even if the blocking direct call has not returned yet;
- resolver.py now uses `socket_timeout=12`, `retries=1`, `extractor_retries=1`, `fragment_retries=1` so direct misses do not use yt-dlp's large default retry chains;
- hard DRM/paywall/subscription/purchase/geo markers remain hard stops;
- challenge/login-style extractor messages remain normalized into ordinary browser assistance rather than being treated as a bypassable access control.

Automatic browser stage:
- there is no fake UI click;
- the hidden WebView is technically `VISIBLE`, normally laid out at Activity size, and the entire transparent Activity task is moved behind Vivaldi before browser loading;
- `mediaPlaybackRequiresUserGesture=true`; no PlayerActivity or ExoPlayer is created;
- discovery observes ordinary WebView requests plus Service Worker requests, DOM VIDEO/SOURCE URLs, Performance API resources, and page-config URLs/declared qualities;
- candidate protections/ranking remain: max 80, first-seen HLS/DASH preference, page-config family IDs, ad demotion, soft audio/video child demotion, no imagery decisions, 720 -> 1080 -> best below 1080;
- exact cookie/18+ automation remains conservative; CAPTCHA/challenge/login/payment/subscription/DRM/region controls are not automated.

Multiple BG tabs:
- Android ServiceWorkerController is process-wide, so hidden browser discovery is serialized into one browser slot at a time;
- direct attempts can still run independently;
- waiting tabs show `tech BROWSER_WAITING_FOR_SLOT` and start browser discovery when the previous BG browser releases the slot;
- this prevents Service Worker requests from one simultaneous BG tab being attributed to another.

State semantics:
- browser success -> READY;
- explicit detected protected/human-interaction challenge -> NEEDS_ATTENTION;
- ordinary automatic browser timeout/no candidate -> ERROR, not NEEDS_ATTENTION;
- dashboard card clicks remain irrelevant for QUEUED/RESOLVING normal BG work.

### Build #202 CI / artifact
- GitHub Actions **run #202 PASS**; run ID `31830708434`.
- Build step succeeded; debug APK upload succeeded.
- Debug artifact ID `9230598176`.
- Artifact ZIP size `25,979,380` bytes.
- Artifact ZIP digest `sha256:6b08abecf71c650b8844d44ba4bc664642127d986bee009fe1b2318fee95302a`.
- Extracted debug APK size `35,466,650` bytes.
- Extracted APK SHA-256 `dab7f1a312d838e966eb552867679b696f887cf6a71a94c2d0c354fd99458114`.
- #202 is the designated focused PH BG QA APK. Later state-only commits do not supersede its app code.
- CI proves compilation/package integrity only; device QA is still required to prove automatic PH BG completion.

## Additional #192 device observations (PH only)
Treat these as authoritative until retested:
- long-press tab moving/reorder: WORKS;
- closing tabs: WORKS;
- playback resumes from where the user left off: WORKS;
- Back after the manually successful Browser Step/player returned to dashboard correctly in the tested flow;
- attempting to change quality: DOES NOT WORK in #192 PH. This is a current regression and supersedes older PH quality PASS for current-build status;
- Settings currently has four options enabled, but user wants an explicit **app language selector** in Settings;
- tests in that round were PH only.

Do not request HH testing yet. PH automatic BG preparation must pass first. After that, use HH as a separate regression target, especially for quality switching.

## Future logo / launcher visual direction
Keep this requirement even while BG work has priority. For the next visual iteration:
- preserve the current letter/color identity (white E / purple scheme);
- keep it recognizable as the same logo family;
- make it less square/boxy and more stylized/refined;
- make the purple portions more noticeable/prominent.
Do not mix this visual change into the focused BG lifecycle fix.

## Current development priority
1. Device-test build #202 with PH only for automatic `BG - External Player` preparation.
2. Add 2–3 PH BG tabs without opening/clicking their cards; Vivaldi should remain foreground.
3. Expect ordinary direct misses to enter automatic browser fallback around the 12-second BG budget rather than ~4 minutes.
4. Expect successful PH automatic browser discovery to become READY without pressing `Paso del navegador`.
5. If multiple tabs need browser fallback, `BROWSER_WAITING_FOR_SLOT` is acceptable temporarily; each should later acquire the slot and continue automatically.
6. `NEEDS_ATTENTION` is acceptable only for a genuine detected human/protected interaction. Ordinary no-candidate timeout should be ERROR instead.
7. Do not test HH yet. If PH #202 passes, record that result in both state files, then test HH and revisit the current quality-switch regression.
8. Add app-language choice in Settings after the BG blocker.
9. Later perform the recorded logo refinement.

## QA format
Whenever asking user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
