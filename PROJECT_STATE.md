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

Previously verified: Bitmovin/PH/HH core baseline, build #62 final-tab/title follow-up, build #74 clean loading. Do not repeat old PASS items without regression reason.

Permanent release signing remains deliberately deferred. Debug GitHub Actions APKs are the QA path; never commit the permanent key.

## Earlier known results
#109 PASS/correct: install, icon, About/build info, Settings, no background playback, clean browser UX, local browser title. Age/cookie prompts not shown.

#109 regressions led to #124 fixes: real hidden browser-capable BG preparation and stronger adaptive Media3 quality switching. User postponed device QA while requesting later UI/features.

#143 added local thumbnails, labels `ExternalPlayer` / `BG - External Player`, dark UI, Settings/About polish. Launcher is now white E + hollow purple diamond, no triangle.

#162 introduced first-class dashboard, unified prep concept, and persisted Manual vs Actual quality; CI PASS on `5bd11518e44a8e146fcb9456481b562979152e0c`.

## Build #162 device QA — actual user findings
Treat these as regressions requiring the #187 pass; do not ask user to re-explain them.
1. Foreground `ExternalPlayer` share did not visibly open/raise the app.
2. BG shares created tabs but they remained `En cola`; real preparation did not happen until ExternalPlayer was manually opened and the user clicked the tab / `Paso del navegador`.
3. User clarified required BG semantics: Vivaldi must remain visible while ExternalPlayer actually runs behind it and immediately prepares the newly created tab. Later opening ExternalPlayer should show the tab identifiable/READY if automatic preparation succeeded. BG must never mean “store URL and wait for user to open app”.
4. Automatic fallback should catch ordinary/direct resolver failure and try the safe browser method by itself. Only genuine interaction should become Browser Step; protected controls are never bypassed.
5. Swipe-to-close did not work.
6. Android Back from Player exposed `Elegir video` / browser candidate chooser. Normal Back must never land there.
7. PH manual quality switching worked; HH manual quality switching did not actually change rendition.
8. Buffering felt slow.
9. Failed/queued tabs were hard to distinguish because no title/thumbnail had been obtained yet.
10. Arrow reorder controls were uncomfortable; user wants Vivaldi-Mobile-style long-press/drag reorder.
11. Pseudo-random thumbnail timestamp was not representative/useful enough.

## #187 reliability / UX regression pass
App-code head: `5b71129faa0f9815189b515e5e87bdd166c52216`.

### Foreground share
New `ForegroundShareActivity` is the exported `ExternalPlayer` SEND target. It explicitly raises/starts MainActivity with the shared URL, then exits. MainActivity is launcher/singleTop rather than being the ambiguous direct SEND target.

### BG semantics and handoff attempted in #187
`BG - External Player` was intended to:
- create the persistent tab immediately;
- start a second `BackgroundPreparationActivity` before the tiny share Activity disappears;
- let that preparer move the isolated task behind Vivaldi;
- perform direct resolution first, then hidden WebView fallback;
- use document tasks, a launch watchdog and WorkManager recovery.

This second-Activity handoff is now known to be unreliable on the user's device and is no longer the chosen normal BG architecture.

### Identifiable BG tabs / thumbnails from #187
- Hidden browser preparation saves the local page title into the READY payload.
- Pending generic cards display local source host as `Video • host` until a better local title exists.
- Successful BG READY completion starts best-effort local thumbnail extraction.
- Thumbnail timestamp uses meaningful saved/current position when available, otherwise ~35% of known duration, otherwise 15s. No frame-content analysis or classification occurs.

### Dashboard gestures
Dashboard uses RecyclerView + ItemTouchHelper:
- long-press and drag vertically to reorder, persisted via VideoTabStore;
- horizontal swipe closes;
- explicit × remains;
- ↑/↓ buttons removed;
- live 1.25s state refresh is suspended during a drag/swipe so it cannot cancel the gesture.

### Back navigation
`PlayerNavigationRuntime` intercepts normal Back from PlayerActivity and returns to MainActivity dashboard using CLEAR_TOP/SINGLE_TOP, removing any browser resolver underneath. Browser candidate selection remains available only through explicit recovery/browser actions.

### HH quality reinforcement
`AdaptiveQualityRuntime` detects numeric requested quality after browser sibling-URL reloads and re-applies an exact Media3 video track/size constraint when that replacement source itself has adaptive tracks. Actual quality remains confirmed only through Media3 `VideoSize`; up to 3 bounded verification re-applies. Verification retries no longer re-seek, reducing unnecessary rebuffering. One tiny refresh remains only for an explicit adaptive manual choice.

### Buffering decision
No global Media3 LoadControl thresholds were changed in #187. The pass removes repeated quality verification re-seeks and fixes resolver/preparation delays first. If device QA still reports slow actual media buffering after the corrected path, profile/tune LoadControl separately rather than risking stalls blindly.

## Build #187 device QA — BG failure confirmed
User tested #187 and reported the same core BG failure as #162, with an important stronger clarification:
- selecting `BG - External Player` did **not** result in real preparation behind Vivaldi;
- the tab was created/saved but remained effectively inactive/`En cola`;
- manually opening ExternalPlayer was not enough to make all pending tabs prepare;
- **each individual pending tab had to be clicked/opened before its own preparation/resolution began**.

Treat this as a hard device failure. CI PASS on #187 is only a compile/package result, not a successful BG fix.

The exact coupling found in code was:
- `BackgroundShareActivity` created the tab and delegated work to a second hidden Activity;
- if that Activity handoff did not survive, the tab stayed QUEUED;
- MainActivity's dashboard primary action explicitly called `UnifiedPreparationCoordinator.prepareNow()` for a non-RESOLVING tab, making card selection a reliable trigger;
- WorkManager could perform direct resolution independently, but an ordinary direct miss called `browserStageNeeded()`, whose browser-capable continuation required a usable Activity lifecycle.

## Current BG architecture change — self-owned share preparation
A focused successor implementation is being prepared on top of `main` with this architecture:
- the exported `BackgroundShareActivity` is no longer a tiny trampoline;
- it creates the persistent tab itself, records preparation requested, immediately moves the tab to `RESOLVING`, and owns direct resolution;
- it moves **its own already-created transparent document task** behind Vivaldi instead of launching a second preparer and then destroying the share entry;
- the same Activity owns a hidden WebView and automatically runs the safe browser-assisted discovery stage after an ordinary direct miss;
- no normal BG-share path needs `BackgroundPreparationActivity` to be created before work can start;
- `BackgroundPreparationActivity` remains for retry/preload/process-recovery paths only;
- `android:noHistory` is removed from `BackgroundShareActivity`, because noHistory conflicts with intentionally keeping that Activity alive after it is moved behind Vivaldi;
- each explicit BG share keeps its own document task, so multiple shares may prepare independently;
- no ExoPlayer is created and no background playback is added.

### Tab-open decoupling
Dashboard card selection is no longer the normal preparation trigger:
- READY -> Play/Continue;
- NEEDS_ATTENTION -> explicit Browser Step because genuine interaction may be required;
- ERROR -> explicit retry/recovery remains available;
- QUEUED/RESOLVING -> the primary card action is disabled/inert and does **not** call `prepareNow()`.

This gives a strong code-path proof for the next QA build: ordinary BG preparation must have started at share time, because clicking a queued card can no longer start it.

### Local technical diagnostics
`VideoTabStore` now persists non-content diagnostics for new/recovery preparation attempts:
- created timestamp;
- preparation requested timestamp;
- preparation host created timestamp;
- direct resolver started/finished timestamps;
- browser stage requested timestamp;
- browser WebView created timestamp;
- browser discovery started timestamp;
- READY timestamp;
- last technical preparation stage + timestamp.

The dashboard shows a compact local marker such as `tech DIRECT_STARTED +1s` or `tech BROWSER_DISCOVERY_STARTED +4s`. These fields contain no media imagery/content, credentials, page text or thumbnail data.

## CI history for #187 pass
- #179 FAILED at Kotlin compile only: after moving `ResolveTabWorker`, one call referenced `preloadNext` without `TabPreparationManager.`. Fixed in `4ab2c978f6e632e5cc65f4bc28533168daccccee`.
- #182 passed Android build/upload after that compile fix.
- #185 PASS with the per-BG-share document-task model.
- **#187 PASS through Android build and debug APK upload** on app-code head `5b71129faa0f9815189b515e5e87bdd166c52216`, but device QA proves the BG runtime behavior still fails.
- Run ID `31771897702`.
- Debug artifact ID `9208455395`.
- GitHub artifact ZIP digest `sha256:09488226ed025f3b22f5540e8b7740d8dff0424cf03936e9ae4d9877bd7293b9`.
- Extracted APK SHA-256 `f3915a70a5b97f22bc54a6e736155b966b8f157e54fa650b4a962dbef21f98f1`.
- Build #187 is **not** considered a successful BG fix despite CI PASS.

## Current development priority
1. Finish and compile the self-owned `BackgroundShareActivity` BG lifecycle change.
2. Verify by code inspection that BG share itself records PREPARATION_REQUESTED -> RESOLVING/DIRECT_STARTED without MainActivity/card selection.
3. Verify ordinary direct failure continues automatically into the same share Activity's safe hidden-WebView browser stage.
4. Preserve candidate ranking, quality policy, protected controls, no-background-playback, local title and thumbnail behavior.
5. Run GitHub Actions and do not designate a QA APK until CI passes.
6. After CI passes, update both state files with exact commit/run/artifact details and give one focused device test for BG preparation-before-app/tab-open only.
7. Only after BG is fixed, resume remaining #187 checks (dashboard gestures, Back navigation, HH quality, buffering) unless the user volunteers results earlier.

## QA format
Whenever asking user to test, provide EXACTLY:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
