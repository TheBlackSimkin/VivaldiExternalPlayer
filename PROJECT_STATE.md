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

### BG semantics and handoff
`BG - External Player` now follows the user's exact requirement:
- creates the persistent tab immediately;
- starts `BackgroundPreparationActivity` before the tiny share Activity disappears;
- preparer lives in the same isolated BG document task and immediately moves that task behind Vivaldi;
- Vivaldi stays visible; no ExoPlayer or background audio/video is created;
- yt-dlp runs first, then safe hidden WebView discovery automatically when needed;
- each explicit BG share gets its own excluded document task (`documentLaunchMode=always`, standard launch mode), so later BG shares do not stack behind one `singleTask`;
- coordinator tracks launch reservations separately from actually-created prep Activities and has a 2s launch watchdog;
- a dropped hidden-Activity launch falls back to WorkManager direct/network preparation instead of leaving a stale global lock;
- unexpectedly destroyed hidden preparation returns RESOLVING -> QUEUED and schedules direct WorkManager recovery; process restart also resumes queued work;
- explicit BG shares can prepare independently; automatic next-tab preload remains serialized.

`resolver.py` now normalizes challenge/login/captcha-style direct-extractor misses into a neutral “needs normal browser assistance” failure. That permits safe browser loading but does not solve/click CAPTCHA/login/payment/DRM/geo controls. Hard protected DRM/paywall/subscription/purchase/geo signals remain terminal.

### Identifiable BG tabs / thumbnails
- Hidden browser preparation saves the local page title into the READY payload.
- Pending generic cards display local source host as `Video • host` until a better local title exists.
- Successful BG READY completion immediately starts best-effort local thumbnail extraction as the hidden task closes; foreground warm-up remains fallback.
- Thumbnail timestamp is no longer pseudo-random: use meaningful saved/current position when available, otherwise ~35% of known duration, otherwise 15s. No frame-content analysis or classification occurs.

### Dashboard gestures
Dashboard now uses RecyclerView + ItemTouchHelper:
- long-press and drag vertically to reorder, persisted via VideoTabStore;
- horizontal swipe closes;
- explicit × remains;
- ↑/↓ buttons removed;
- live 1.25s state refresh is suspended during a drag/swipe so it cannot cancel the gesture.

### Back navigation
`PlayerNavigationRuntime` intercepts normal Back from PlayerActivity and returns to MainActivity dashboard using CLEAR_TOP/SINGLE_TOP, removing any browser resolver underneath. Browser candidate selection remains available only through explicit recovery/browser actions.

### HH quality reinforcement
`AdaptiveQualityRuntime` now detects numeric requested quality after browser sibling-URL reloads and re-applies an exact Media3 video track/size constraint when that replacement source itself has adaptive tracks. Actual quality remains confirmed only through Media3 `VideoSize`; up to 3 bounded verification re-applies. Verification retries no longer re-seek, reducing unnecessary rebuffering. One tiny refresh remains only for an explicit adaptive manual choice.

### Buffering decision
No global Media3 LoadControl thresholds were changed in #187. The pass removes repeated quality verification re-seeks and fixes resolver/preparation delays first. If device QA still reports slow actual media buffering after the corrected path, profile/tune LoadControl separately rather than risking stalls blindly.

## CI history for this pass
- #179 FAILED at Kotlin compile only: after moving `ResolveTabWorker`, one call referenced `preloadNext` without `TabPreparationManager.`. Fixed in `4ab2c978f6e632e5cc65f4bc28533168daccccee`.
- #182 passed Android build/upload after that compile fix.
- #185 PASS with the per-BG-share document-task model.
- **#187 PASS through Android build and debug APK upload** on app-code head `5b71129faa0f9815189b515e5e87bdd166c52216`.
- Run ID `31771897702`.
- Debug artifact ID `9208455395`.
- GitHub artifact ZIP digest `sha256:09488226ed025f3b22f5540e8b7740d8dff0424cf03936e9ae4d9877bd7293b9`.
- Extracted APK SHA-256 `f3915a70a5b97f22bc54a6e736155b966b8f157e54fa650b4a962dbef21f98f1`.
- Build #187 is the designated next device-QA APK. Documentation-only commits afterward do not supersede it.

## Current QA priority
Use #187 for a focused regression pass only:
1. foreground `ExternalPlayer` visibly opens/raises app;
2. BG keeps Vivaldi visible while actual preparation proceeds without user opening ExternalPlayer;
3. multiple BG shares independently progress beyond QUEUED;
4. automatic direct -> safe browser fallback, especially HH, with title/READY and best-effort thumbnail before manual tab click;
5. no background playback;
6. long-press drag reorder + swipe close;
7. normal Back from Player -> dashboard, never chooser;
8. HH actual manual quality switching + PH regression check;
9. assess actual buffering after repeated-reseek removal; if still slow, tune Media3 buffering separately.

## QA format
Whenever asking user to test, provide EXACTLY:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
