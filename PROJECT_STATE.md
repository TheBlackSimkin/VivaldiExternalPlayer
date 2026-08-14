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
- `BG - External Player` chooser target is exported `BackgroundShareActivity`, which is a real launched Activity but intentionally moves its task behind Vivaldi.
- BG host is `android:excludeFromRecents="true"`; absence from the Android square/Recents screen is therefore expected by current design and is not a BG success/failure criterion.
- Portuguese `segundo plano` is naturally “the background” in this context.

## Build #192 PH device QA — authoritative new result
User tested three PH links shared through `Compartir enlace` -> `BG - External Player`.

Important improvement versus #187:
- after later opening ExternalPlayer once, **all three tabs were already actively in `Preparando` without the user first clicking each individual tab**. This proves the #192 self-owned share path materially removed the old tab-click-to-start coupling.

Remaining failure:
- the three preparations did not finish automatically;
- each took roughly **244–270 seconds** from creation to the final visible failure marker;
- all ended `Falta paso del navegador` / `NEEDS_ATTENTION` with the Browser Step button;
- clicking `Paso del navegador` manually then succeeded in roughly **5–10 seconds** for each tested PH URL;
- after successful visible browser resolution, Back returned to the tab dashboard and the tab contained its information correctly.

Code inspection after this result found a strong timing clue:
- `BackgroundShareActivity` browser timeout is only 22 seconds;
- therefore the ~244–270 second total strongly indicates most delay occurs before browser timeout, inside the direct/yt-dlp stage, which currently has no hard wall-clock deadline;
- visible `BrowserResolverActivity` also observes Service Worker requests, while the #192 BG browser copy does not;
- the #192 BG WebView is `INVISIBLE` and only 1x1, whereas the visible Browser Step uses a normal visible WebView. Modern pages may defer normal player/config initialization when their WebView is not technically visible.

### Browser-fallback architectural decision after #192 QA
Do **not** solve this by literally faking a UI click on `Paso del navegador`.

The intended behavior is equivalent from the user's perspective but cleaner technically:
1. direct/yt-dlp remains first but receives a bounded BG deadline so one extractor cannot monopolize several minutes;
2. on ordinary miss/deadline, automatically run the **same technical browser-discovery engine/signals as the successful visible Browser Step** behind Vivaldi;
3. BG discovery must store READY metadata only; it must never launch PlayerActivity/ExoPlayer or produce background audio/video;
4. ordinary browser discovery timeout/failure must not be mislabeled as NEEDS_ATTENTION;
5. NEEDS_ATTENTION / `Paso del navegador` is reserved for genuine user interaction such as CAPTCHA/challenge/login/payment/DRM/region controls that automation must not bypass;
6. because Android exposes process-wide Service Worker observation, multiple simultaneous BG browser stages must avoid cross-tab request attribution (serialize or otherwise route safely).

This eliminates duplicated browser behavior drifting apart between BG and visible Browser Step.

## Additional #192 device observations (PH only)
Treat these as authoritative for #192:
- long-press tab moving/reorder: WORKS;
- closing tabs: WORKS;
- playback resumes from where the user left off: WORKS;
- Back after the manually successful Browser Step/player returned to dashboard correctly in the tested flow;
- attempting to change quality: DOES NOT WORK in this #192 PH test. This is a current regression and supersedes older PH quality PASS for current-build status;
- Settings currently has four options enabled, but user wants an explicit **app language selector** in Settings;
- tests in this round were PH only.

Do not request HH testing yet. PH already provides enough evidence to fix the BG automatic browser path first. After PH automatic BG preparation is confirmed, use HH as a separate regression target, especially for quality switching.

## Future logo / launcher visual direction
Keep this requirement even while BG work has priority. For the next visual iteration:
- preserve the current letter/color identity (white E / purple scheme);
- keep it recognizable as the same logo family;
- make it less square/boxy and more stylized/refined;
- make the purple portions more noticeable/prominent.
Do not mix this visual change into the focused BG lifecycle fix.

## Current development priority
1. Stop broad #192 testing; no HH test is needed yet.
2. Fix PH automatic BG fallback so it reaches the same technical discovery success as manual Browser Step without the user pressing it.
3. Add a bounded direct-stage deadline; avoid the observed ~4-minute extractor stall before browser work.
4. Unify/mirror visible Browser Step discovery signals in the BG path, including Service Worker handling and normal WebView initialization behavior, without background playback.
5. Keep NEEDS_ATTENTION only for genuine protected/user-interaction cases.
6. Build/CI and inspect the code path, then ask for one focused PH BG re-test.
7. Once PH BG automatic preparation passes, test HH separately and revisit current quality-switch regression.
8. Add app-language choice in Settings after the BG blocker unless it is naturally bundled with a later UI/settings pass.
9. Later perform the recorded logo refinement.
