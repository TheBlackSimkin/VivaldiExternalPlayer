# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA, failures, decisions, or priorities change.

## Working rules
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Source should contain abundant English comments.
- Use connected GitHub tools directly whenever possible.
- PH and HH are real technical playback targets tested by the user. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed. Do not inspect/describe media content or thumbnail imagery; never ask user for PH/HH titles or thumbnails.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p; >1080 only rare fallback.

Protect Vivaldi share; yt-dlp first/browser fallback; automatic best/manual fallback; video+audio; adaptive and sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; 80 stored/20 manual candidate limits; first-seen HLS/DASH order; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no imagery-based resolver/ranking; one actual ExoPlayer playback session.

Previously verified core baseline includes Bitmovin/PH/HH playback, build #62 follow-up, build #74 clean loading, and #109 install/About/Settings/no-background-playback/local browser title. Do not repeat old PASS items without a regression reason.

Permanent release signing remains deliberately deferred. Debug GitHub Actions APKs are the QA path; never commit the permanent key.

## Historical BG progression
### #187
A second hidden `BackgroundPreparationActivity` handoff was unreliable. Pending tabs still had to be clicked before preparation began.

### #192
Exported BG share Activity began preparation itself. Real PH QA proved all three tabs had already started `Preparando` before any card click, fixing the old tab-click-to-start coupling. Automatic completion still failed after roughly 244–270 seconds and ended `Falta paso del navegador`; manual Browser Step then worked in roughly 5–10 seconds.

### #202
`BackgroundShareActivityV2` added:
- direct yt-dlp first;
- 12-second budget for automatic browser fallback;
- bounded yt-dlp retry/socket behavior;
- normally-sized technically-visible hidden WebView;
- WebView + Service Worker + DOM + Performance + page-config discovery;
- serialized browser slot because `ServiceWorkerController` is process-wide.

PH device QA still failed:
- all three tabs remained Preparing after ~120 seconds;
- after several more minutes they ended `NEEDS_ATTENTION` / Browser Step;
- no card was clicked before the automatic route stopped;
- manual Browser Step reached READY in about 5–7 seconds per tab;
- absence from Android Recents PASS and is preferred by the user.

Additional #202 PH regressions still current:
- manual 240p works;
- manual 480p does not;
- Auto chooses 1080p even when 720p exists;
- launcher icon remained unchanged.

## Build #205 — foreground keep-alive + operations log
App-code commit: `48605a4c1eb8972d6275478993db5ce7b104478e`.
CI run #205 PASS, run ID `31838190231`.
Artifact ID `9233311193`; ZIP SHA-256 `c9b8473fab112aaa525111505389726724e50be8979d993777fa1355bccc930b`; APK SHA-256 `635b100073068f1062ef660fb9b61d46fb62613e7c71aa4e3814c158aeb71d72`.

#205 added:
- short-lived foreground `dataSync` keep-alive service;
- exportable Settings -> `Share operations log` / `Compartir registro de operaciones`;
- technical lifecycle/state logging only, with no thumbnails/frames/page text/headers/cookies/credentials;
- an attempted application-level cancellation of V2's old destroyed-host Worker fallback.

## Build #205 PH device QA — authoritative FAIL, operations log decisive
User tested three PH BG shares and supplied screenshots plus exported operations log.

At the first focused observation (~60 seconds after the third share), all three cards were still `Preparando`. Visible markers included `PREPARATION_HOST_CREATED +84s`, `DIRECT_STARTED +79s`, and `DIRECT_STARTED +76s`.

Later, two tabs showed `Falta paso del navegador` / `NEEDS_ATTENTION` at roughly +149s/+165s while the third was still `DIRECT_STARTED +161s`.

The operations log proved the lifecycle failure precisely for the first share:
- `16:42:00.800` BG share Activity RESUMED;
- `16:42:01.113` keep-alive service CREATED;
- `16:42:01.204` Activity PAUSED;
- `16:42:01.422` Activity STOPPED;
- `16:42:01.529` tab already changed to QUEUED with `BG preparation host was destroyed; direct recovery queued`;
- `16:42:01.546` tech `BG_HOST_DESTROYED_RECOVERY_QUEUED`;
- `16:42:01.569` tech `WORKER_ENQUEUED`;
- `16:42:01.599` Activity DESTROYED;
- `16:42:01.831` the Worker had restarted direct resolution.

Therefore:
1. The foreground service successfully kept the **process** important/alive, but it did not keep a **stopped Activity/WebView instance** alive.
2. The phone destroyed the stopped V2 Activity almost immediately after it went behind Vivaldi.
3. #205 still contained the old Worker enqueue inside V2 `onDestroy()`. The application callback attempted to cancel it too late; the log proves WorkManager had already taken over.
4. The multi-minute Browser Step outcome was again the old recovery architecture, not V2's intended browser stage.
5. The operations-log feature itself is a PASS and should be retained.

#205 is no longer a QA target.

## Architectural decision after #205
Do not attempt another variation of “move a WebView Activity behind Vivaldi and hope Android keeps the stopped Activity.” Device evidence shows this is unreliable on the test phone.

Also do not put WebView directly in a foreground Service. Android's WebView API guidance expects WebView to be created with an Activity context.

New supported model:
`BG share handoff on physical display -> foreground process lease -> private app-owned virtual display -> real preparation Activity/WebView on that secondary display -> READY/ERROR -> release display/service`

Rationale:
- the exported share Activity can safely finish immediately because it owns no resolver work;
- foreground service protects process importance;
- WebView still receives a genuine Activity context;
- the preparation Activity is launched on an app-private secondary display rather than becoming a stopped Activity behind Vivaldi on the primary display;
- Vivaldi remains on the phone's physical display;
- no PlayerActivity or ExoPlayer is created by BG preparation.

If the device does not support activities on secondary displays, the new path must fail quickly and explicitly as ERROR rather than silently reverting to WorkManager/Browser Step.

## Build #212 implementation — private virtual-display preparation
Current app-code head: `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`.

Relevant app commits:
- `cab9656c77609c8f4373288ae3ae13d8229ec1e4` — make `BackgroundShareActivityV2` a short handoff only;
- `cb01c96a8652fa5e1a257affd3e768bc0782e100` — add `BackgroundVirtualDisplayRegistry`;
- `e9fd2d63c47ba41c3fe030c4d2862399129ef8ac` — add `BackgroundVirtualPreparationActivity`;
- `55e661f66553917ac80f880c32f7bfb46e7f83b6` — register virtual preparation Activity in manifest;
- `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe` — prevent lifecycle callbacks from reviving virtual BG sessions through legacy Worker paths.

### Share handoff
`BackgroundShareActivityV2` now:
1. extracts shared URL and creates the persistent pending tab;
2. marks `VIRTUAL_DISPLAY_SESSION_REQUESTED`;
3. verifies Android secondary-display Activity capability;
4. starts the foreground preparation lease;
5. creates an app-private virtual display;
6. launches `BackgroundVirtualPreparationActivity` onto that display with `ActivityOptions.launchDisplayId`;
7. immediately finishes the exported share Activity.

It owns no yt-dlp coroutine and no WebView, so its destruction is now expected and harmless.

### Private virtual display
`BackgroundVirtualDisplayRegistry`:
- creates one private `OWN_CONTENT_ONLY | PRESENTATION` VirtualDisplay per BG session;
- uses an `ImageReader` only as the required Surface sink;
- drains and immediately closes frames without reading planes, copying pixels, saving images, classifying imagery, or exposing the display;
- releases the display/surface when the session ends.

### Real off-screen preparation Activity
`BackgroundVirtualPreparationActivity`:
- is non-exported and launched only on the private secondary display;
- creates a normally-sized real WebView with the Activity context;
- never starts PlayerActivity/ExoPlayer/background playback;
- runs direct yt-dlp first;
- preserves 12-second direct budget -> automatic browser fallback;
- serializes Service Worker browser ownership;
- preserves WebView/network/DOM/Performance/page-config discovery and conservative exact cookie/18+ handling;
- 30-second ordinary browser timeout -> ERROR;
- genuine detected protected/human interaction -> NEEDS_ATTENTION;
- unexpected virtual-preparation Activity destruction -> explicit ERROR `VIRTUAL_PREP_ACTIVITY_DESTROYED`, never Worker recovery;
- process restart of an unfinished virtual session -> explicit `PROCESS_RESTART_VIRTUAL_BG_ERROR` before `resumePending()`.

New operations-log markers include virtual display creation, virtual prep launch/activity creation, direct budget expiry, browser request/start, candidate counts/types/source/declared quality, READY/ERROR/NEEDS_ATTENTION, and unexpected destruction. No media URLs or image contents are logged in these new candidate lines.

## Build #212 CI / focused QA artifact
- GitHub Actions run **#212 PASS**.
- Run ID: `31841938130`.
- App-code head: `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`.
- Debug artifact ID: `9234596372`.
- Artifact ZIP size: `25,999,143` bytes.
- Artifact ZIP digest: `sha256:5ae996f3c443cbc54d72e240fe31e79824ee60de162979c6790229a935c9f734`.
- Extracted debug APK size: `35,487,666` bytes.
- Extracted APK SHA-256: `a350995bb2b4040f2571c3d8aebb92b0dbc0eb8f2a6fc77dc530a32537765125`.
- #212 is the new focused PH BG architecture QA target. CI proves compile/package integrity only; the device test must prove the private-display Activity actually launches/stays alive on this phone.

## Current quality status / later fix
Protected policy remains exact 720 -> otherwise 1080 -> otherwise best below 1080. Current PH runtime violates it by initially choosing 1080 when 720 exists.

Known current quality findings:
- manual 240 works;
- manual 480 fails;
- initial Auto may pick 1080 despite 720 being available.

Do not mix speculative quality changes into #212. Once PH automatic BG preparation passes, fix/verify strict initial family quality selection and 480 switching before HH regression testing.

## Current UI observations / backlog
- long-press tab move/reorder WORKS from #192;
- closing tabs WORKS from #192;
- resume position WORKS from #192;
- tested Back flow after manual Browser Step WORKS from #192;
- Settings needs explicit app-language selector;
- BG absence from Android Recents PASS and preferred;
- exportable operations log PASS/useful from #205.

## Future logo / launcher direction
Still required after the BG blocker:
- preserve white-E / purple identity and recognizable family;
- make it less square/boxy and more stylized/refined;
- make purple portions more prominent.
The user explicitly reminded us after #202 that the icon is unchanged. Do not mix this visual change into the focused BG architecture QA.

## Current development priority
1. Device-test build #212 with PH only; no HH yet.
2. Start with **one PH BG share first**. Keep Vivaldi on the physical display and do not open/click the card.
3. Wait about 45–60 seconds, then open ExternalPlayer once and inspect the card.
4. Strong first milestone: tech/log must show `VIRTUAL_DISPLAY_CREATED` and `VIRTUAL_PREP_ACTIVITY_CREATED`; it must not show `BG_HOST_DESTROYED_RECOVERY_QUEUED` or `WORKER_ENQUEUED` for the normal share.
5. Target outcome: READY without Browser Step. If private-display activity support is unavailable, expect a quick explicit ERROR such as `VIRTUAL_DISPLAY_UNSUPPORTED`/launch failure, not minutes of Preparing.
6. If one-share test passes, repeat with 2–3 PH shares to test browser-slot serialization.
7. If anything fails, export `Settings -> Share operations log` before pressing Browser Step.
8. After PH BG passes, fix strict 720 preference + 480 switching, then test HH separately.
9. Add explicit app-language choice after BG blocker.
10. Later perform recorded launcher/logo refinement.

## QA format
Whenever asking user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
