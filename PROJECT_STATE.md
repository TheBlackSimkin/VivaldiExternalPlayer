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

This second-Activity handoff is known to be unreliable on the user's device and is no longer the chosen normal BG architecture.

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
No global Media3 LoadControl thresholds were changed in #187. Fix resolver/preparation delays first. If device QA still reports slow actual media buffering after the corrected path, profile/tune LoadControl separately rather than risking stalls blindly.

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

## Build #192 BG architecture — self-owned share preparation
App-code head: `a85f03a30ffdcda6d3f3c1c130ee799fd523e3f0`.

The normal BG share path is redesigned so the exported `BackgroundShareActivity` owns preparation itself:
- it creates the persistent tab immediately;
- records PREPARATION_REQUESTED and moves the tab to RESOLVING immediately;
- records the already-created share Activity as the preparation host;
- moves **that same transparent document task** behind Vivaldi instead of launching a second preparer and then destroying the share entry;
- starts direct/yt-dlp resolution from the share Activity independently of MainActivity/dashboard selection;
- after an ordinary direct miss, the same Activity automatically starts its hidden WebView browser-assisted discovery stage;
- local page title is saved when browser resolution succeeds;
- READY starts best-effort local thumbnail extraction;
- `BackgroundPreparationActivity` remains only for retry/preload/process-recovery paths;
- `android:noHistory` was removed from `BackgroundShareActivity`, because noHistory conflicts with intentionally keeping that preparation host alive behind Vivaldi;
- common orientation/screen-size changes are handled in-place to avoid duplicate share tabs;
- each explicit BG share retains its own document task, so multiple BG shares can prepare independently;
- no ExoPlayer is created and no background playback is added.

### Tab-open preparation coupling removed
Dashboard card selection is no longer the normal preparation trigger:
- READY -> Play/Continue;
- NEEDS_ATTENTION -> explicit Browser Step because genuine interaction may be required;
- ERROR -> explicit retry/recovery remains available;
- QUEUED/RESOLVING -> primary card action is disabled/inert and does **not** call `prepareNow()`.

This is intentional proof for device QA: if a BG tab fails to prepare before the dashboard is opened, clicking the card can no longer hide the failure by starting normal preparation.

### State transition expected in #192
Normal BG share should persist:
`TAB_CREATED -> PREPARATION_REQUESTED -> RESOLVING -> DIRECT_STARTED -> DIRECT_FINISHED -> READY`

If direct resolution has an ordinary miss:
`... -> DIRECT_FINISHED -> BROWSER_REQUESTED -> BROWSER_DISCOVERY_STARTED -> READY / NEEDS_ATTENTION / ERROR`

`NEEDS_ATTENTION` is reserved for genuine interaction such as a protected browser challenge which automation must not bypass.

### Local technical diagnostics
`VideoTabStore` persists non-content diagnostics:
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

### Recovery behavior
If Android unexpectedly destroys the self-owned BG Activity while it is still RESOLVING, that tab is returned to QUEUED, records `BG_HOST_DESTROYED_RECOVERY_QUEUED`, and schedules WorkManager direct recovery. WorkManager can run the direct stage without UI. If that recovery direct stage has an ordinary miss, browser continuation still needs a valid Activity lifecycle; the normal fresh BG-share path avoids this dependency because its own share Activity already owns the WebView.

## CI history
### #187
- #179 FAILED at Kotlin compile only: after moving `ResolveTabWorker`, one call referenced `preloadNext` without `TabPreparationManager.`. Fixed in `4ab2c978f6e632e5cc65f4bc28533168daccccee`.
- #182 passed Android build/upload after that compile fix.
- #185 PASS with the per-BG-share document-task model.
- #187 PASS through Android build and debug APK upload on app-code head `5b71129faa0f9815189b515e5e87bdd166c52216`, but device QA proves the BG runtime behavior still fails.
- #187 run ID `31771897702`, debug artifact ID `9208455395`, artifact digest `sha256:09488226ed025f3b22f5540e8b7740d8dff0424cf03936e9ae4d9877bd7293b9`, extracted APK SHA-256 `f3915a70a5b97f22bc54a6e736155b966b8f157e54fa650b4a962dbef21f98f1`.

### #192 focused BG successor
- App-code commit: `a85f03a30ffdcda6d3f3c1c130ee799fd523e3f0` (`Fix BG preparation ownership and tab-open coupling`).
- GitHub Actions **run #192 PASS**: Android/Gradle build succeeded and debug APK upload succeeded.
- Run ID: `31820367544`.
- Debug artifact ID: `9226733915`.
- GitHub artifact ZIP digest: `sha256:76b1d2ff8c9d929d995c91d563bdb4d833996f4d50253cc29adee1dc65763a04`.
- Extracted debug APK SHA-256: `89cafe287d0f3bcf6a63b545efaa9a89ae936e0a42ee50eb2b6f6d6a7a997960`.
- Build #192 is designated for **focused BG lifecycle device QA only**. CI proves compilation/package integrity, not the BG runtime result.
- A later state-only commit which records these #192 facts does not supersede the #192 app-code APK.

## Share-target entry requirement — explicit verification before #192 QA
This is now a protected runtime requirement because foreground/background entry behavior has failed repeatedly:
- `ExternalPlayer` chooser selection must launch exported `ForegroundShareActivity`; that trampoline explicitly starts/raises `MainActivity` with the shared URL using CLEAR_TOP/SINGLE_TOP. `MainActivity.acceptSharedUrl()` immediately invokes the normal visible `resolveAndPlay()` path. Expected device behavior: ExternalPlayer visibly comes to the foreground and starts resolving/playing normally.
- `BG - External Player` chooser selection must launch exported `BackgroundShareActivity`; that already-created Activity creates the persistent tab, records PREPARATION_REQUESTED/RESOLVING/direct start, and then moves its own transparent document task into the **background** so Vivaldi remains visible while preparation continues.
- Both chooser targets are explicitly registered as exported text/plain SEND Activities in the manifest.
- Code inspection confirms the intended entry paths, but do **not** call either behavior device-verified until real-device QA confirms foreground raising and continued background execution. A green CI build is not runtime proof.

English wording: Portuguese `segundo plano` is naturally **“the background”** here. Example: “ExternalPlayer should keep preparing the video **in the background**.”

## Future logo / launcher visual direction — NOT part of #192
For the next visual iteration, preserve the current logo identity, colors, and letter concept (white E / purple scheme), but make it:
- less square / less boxy;
- more stylized and refined;
- still recognizable as the same logo family;
- with the purple portions more noticeable/prominent.
Do not change build #192 for this request; handle it in a later visual iteration after the BG lifecycle priority is resolved.

## Build #192 QA clarification — Android Recents is not a BG proof
During #192 device QA, the user shared three PH links through `BG - External Player`, then opened Android Recents with the square button and did not see an ExternalPlayer task there.

This is **expected by current design and is not a failure signal by itself**: `BackgroundShareActivity` is intentionally declared with `android:excludeFromRecents="true"`. Its transparent document task may therefore continue preparation while being intentionally absent from the Recents UI.

Do not use Recents visibility as the BG success criterion. The real criterion remains: without manually opening ExternalPlayer or clicking individual cards, the three tabs must advance from QUEUED/RESOLVING through their technical stages and preferably reach READY. The user should continue the focused test and later open ExternalPlayer once only to observe the already-existing tab states/`tech ...` markers.

## Current development priority
1. Device-test build #192 first for **both share-entry semantics**: `ExternalPlayer` must visibly raise/open the app and begin the foreground flow; `BG - External Player` must leave Vivaldi visible while the app's already-launched BG Activity actually begins preparation.
2. Do **not** require the BG Activity to appear in Android Recents; it is intentionally excluded there.
3. For BG specifically, verify tabs leave QUEUED and prepare **before ExternalPlayer or the individual card is manually opened**.
4. Test multiple BG shares without clicking their cards; each should progress according to its own document/preparation task.
5. Use the local `tech ...` stage marker to identify exactly where a failure stops if device behavior still differs from the intended lifecycle.
6. Confirm direct -> safe browser fallback is automatic when ordinary resolution fails, while protected controls still stop at NEEDS_ATTENTION/ERROR as appropriate.
7. Do not treat #192 as a successful BG fix until device QA confirms it.
8. Only after BG is fixed, resume remaining #187 checks (dashboard gestures, Back navigation, HH quality, buffering) unless the user volunteers results earlier.
9. After the BG lifecycle is solved, include the requested logo refinement in a later visual iteration.

## QA format
Whenever asking user to test, provide EXACTLY:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
