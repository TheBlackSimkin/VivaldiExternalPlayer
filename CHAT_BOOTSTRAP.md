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

## Authoritative PH BG history
### #192
Self-owned share Activity proved preparation starts before tab/card clicks, but three PH tabs took ~244–270s and ended Browser Step. Manual Browser Step worked in ~5–10s.

### #202
Added V2 with 12s direct budget, normal-size hidden WebView and Service Worker observation. Device QA still took minutes and ended `NEEDS_ATTENTION`; manual Browser Step worked in ~5–7s. Recents absence PASS/preferred. Current quality regressions: 240p works, 480p fails, Auto can pick 1080 despite 720 existing. Icon unchanged.

### #205 — authoritative FAIL with decisive operations log
#205 added a foreground keep-alive service and exportable operations log.

Device log proved the exact failure:
- first BG Activity RESUMED at `16:42:00.800`;
- service CREATED `16:42:01.113`;
- Activity STOPPED `16:42:01.422`;
- by `16:42:01.529` tab was already QUEUED with destroyed-host recovery;
- `BG_HOST_DESTROYED_RECOVERY_QUEUED` at `16:42:01.546`;
- `WORKER_ENQUEUED` at `16:42:01.569`;
- Activity DESTROYED `16:42:01.599`;
- Worker direct resolution restarted `16:42:01.831`.

Screenshots at ~60 seconds showed all three still Preparing. Later two were `NEEDS_ATTENTION` around +149/+165s and one still `DIRECT_STARTED +161s`.

Conclusion: the foreground service kept the process alive but did not keep a stopped Activity/WebView alive. The phone destroys that stopped share Activity almost immediately. #205 also still had the old Worker enqueue inside V2 `onDestroy()`, which won the race before application-level cancellation. Operations log itself is a PASS and should be retained.

Do not request HH yet.

## Architectural decision after #205
Do not try another stopped-Activity-behind-Vivaldi variant.
Do not put WebView directly in a Service; WebView should retain a genuine Activity context.

Current model:
`BG chooser handoff -> foreground process lease -> app-private virtual display -> real preparation Activity/WebView on secondary display -> READY/ERROR -> release display/service`

Vivaldi stays on the physical display. The exported share Activity owns no resolver work and may finish immediately.

## Build #212 — current focused PH BG target
App-code head: `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`.

App commits:
- `cab9656c...` — `BackgroundShareActivityV2` becomes short handoff only;
- `cb01c96a...` — private `BackgroundVirtualDisplayRegistry`;
- `e9fd2d63...` — real `BackgroundVirtualPreparationActivity` on private display;
- `55e661f6...` — manifest registration;
- `2052aaa7...` — lifecycle callbacks cannot revive normal virtual BG sessions through legacy Worker.

### #212 behavior
`BackgroundShareActivityV2`:
- creates pending tab at share time;
- checks secondary-display Activity support;
- starts foreground preparation lease;
- creates private `OWN_CONTENT_ONLY | PRESENTATION` virtual display;
- launches `BackgroundVirtualPreparationActivity` there with `ActivityOptions.launchDisplayId`;
- finishes immediately.

`BackgroundVirtualPreparationActivity`:
- non-exported real Activity/WebView on the private secondary display;
- no PlayerActivity/ExoPlayer/background playback;
- direct yt-dlp first, 12s browser fallback budget;
- Service Worker browser slot serialized;
- WebView/network/DOM/Performance/page-config discovery retained;
- conservative exact cookie/18+ handling retained;
- ordinary 30s browser timeout -> ERROR;
- genuine protected/human interaction -> NEEDS_ATTENTION;
- unexpected destruction -> explicit `VIRTUAL_PREP_ACTIVITY_DESTROYED`, never Worker fallback;
- process restart -> `PROCESS_RESTART_VIRTUAL_BG_ERROR`, never silent Worker revival.

The virtual display uses ImageReader only as a Surface sink. Frames are drained/closed without reading pixel planes, saving, classifying or logging imagery.

### #212 CI / artifact
- GitHub Actions run #212 PASS; run ID `31841938130`.
- Debug artifact ID `9234596372`.
- ZIP size `25,999,143` bytes.
- ZIP digest `sha256:5ae996f3c443cbc54d72e240fe31e79824ee60de162979c6790229a935c9f734`.
- APK size `35,487,666` bytes.
- APK SHA-256 `a350995bb2b4040f2571c3d8aebb92b0dbc0eb8f2a6fc77dc530a32537765125`.
- #212 is the current focused PH architecture QA APK. CI proves build/package only; device must prove the private-display Activity launches and survives on this phone.

## Current quality / UI backlog
After PH BG passes:
- fix strict initial 720 preference (currently 1080 may win despite 720 existing);
- fix/verify manual 480 switching (240 works on tested PH path);
- test HH separately;
- add explicit app-language selector in Settings;
- later refine icon while preserving white-E/purple identity, making it less boxy/more refined and purple more prominent.

## Current priority
1. Test build #212 with PH only; no HH.
2. **Use one PH BG share first**, not three. Keep Vivaldi on the physical display; do not open/click the card.
3. Wait ~45–60 seconds, then open ExternalPlayer once.
4. Key lifecycle proof: log should contain `VIRTUAL_DISPLAY_CREATED` and `VIRTUAL_PREP_ACTIVITY_CREATED`. Normal share must not show `BG_HOST_DESTROYED_RECOVERY_QUEUED` or `WORKER_ENQUEUED`.
5. Target: READY without Browser Step.
6. If secondary-display Activity support is unavailable, expect quick explicit ERROR (`VIRTUAL_DISPLAY_UNSUPPORTED`, create/launch failure), not minutes of Preparing.
7. If one share passes, repeat later with 2–3 PH shares to test browser-slot serialization.
8. If anything fails, export Settings -> Share operations log before pressing Browser Step.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
