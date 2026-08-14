# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` before substantive work. Keep both state files current whenever QA, architecture, failures, priorities, or decisions change.

## Communication / safety
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Use connected GitHub tools directly. Source should contain abundant English comments.
- PH and HH are real technical targets. URLs/manifests/codecs/resolutions/request metadata/candidate ranking/states/errors/local titles are allowed. Never inspect/describe/classify media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise best below 1080p. Preserve yt-dlp first/browser fallback; automatic best/manual fallback; adaptive/sibling quality handling; double-tap ±10s; seek preview; rotation; bilingual UI; 80 stored/20 manual candidate limits; first-seen HLS/DASH order; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no imagery-based resolver/ranking; one actual ExoPlayer playback session.

## Authoritative PH BG history
### #192
Self-owned share Activity proved preparation starts before tab/card clicks, but PH automatic completion took ~244–270s and ended Browser Step. Manual Browser Step worked ~5–10s.

### #202
V2 added 12s direct budget, bounded yt-dlp, normal-size hidden WebView, Service Worker + DOM/Performance/page-config discovery. PH still took minutes and ended NEEDS_ATTENTION; manual Browser Step ~5–7s. Recents absence PASS/preferred.

Current quality regressions from #202: 240p works, 480p fails, Auto can choose 1080 despite 720 existing. Icon unchanged.

### #205 — decisive lifecycle log
Foreground keep-alive protected the process but not the stopped Activity. Device log showed the BG Activity STOPPED at 16:42:01.422, destroyed-host recovery began ~107ms later, `WORKER_ENQUEUED` at 16:42:01.569, Activity destroyed 16:42:01.599. Operations log is a PASS/useful feature.

Conclusion: do not move the WebView Activity behind Vivaldi and depend on Android preserving that stopped Activity.

## Build #212 — private virtual-display experiment, authoritative FAIL
App-code head `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`; CI #212 PASS.

User followed the one-PH test correctly: cleaned tabs, Home, Vivaldi, PH, share via BG, waited 45s, opened ExternalPlayer, captured ERROR, then exported log.

Result: `Error • tech VIRTUAL_PREP_LAUNCH_FAILED +0s`.

Log proved:
- BG handoff started;
- private virtual display was created successfully as display 2, 1080x2180 @ 440 dpi;
- Android immediately denied launching `BackgroundVirtualPreparationActivity` with `launchDisplayId=2`;
- tab was already ERROR when keep-alive service snapshot ran.

AOSP diagnosis:
- untrusted virtual-display Activity launch first requires the Activity to opt into embedding;
- if the caller UID has no existing Activity on that display, caller also needs `android.permission.ACTIVITY_EMBEDDING`;
- AOSP defines that permission `signature|privileged`;
- `DisplayContent.isUidPresent()` checks an existing ActivityRecord, so a normal app cannot bootstrap its first Activity there merely because it owns the display.

Do not retry with privileged/system permissions and do not ship a simple `allowEmbedded=true` build which would hit the next restriction.

#212 is no longer a QA target.

## Current app architecture — transparent RESUMED preparation on default display
Current app-code commit: `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`.
CI is pending at this bootstrap update; do not designate its APK until CI passes.

Normal BG path:
`BG share -> create tab immediately -> foreground lease -> launch preparation Activity in same excluded task on DEFAULT display -> keep it RESUMED with nearly-transparent NOT_TOUCHABLE window -> READY/ERROR/NEEDS_ATTENTION -> remove task`.

Important changes:
- no `moveTaskToBack()`;
- no `ActivityOptions.launchDisplayId`;
- no normal virtual-display dependency;
- share handoff uses `finish()`, not `finishAndRemoveTask()`, so it does not kill the preparer task;
- historical class `BackgroundVirtualPreparationActivity` is reused as the actual resolver host on the primary/default display;
- `TabbedPlayerApplication` sets its window alpha to 0.01, transparent background and `FLAG_NOT_TOUCHABLE`;
- it intentionally leaves focus enabled for browser equivalence;
- lifecycle log now records `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED/STARTED/RESUMED/PAUSED/STOPPED/DESTROYED_CALLBACK`;
- existing direct/browser resolver logic remains: direct first, 12s browser budget, Service Worker serialization, browser network/DOM/Performance/page-config discovery, 30s timeout, conservative cookie/18+ handling;
- normal BG path must never silently return to `WORKER_ENQUEUED`;
- no PlayerActivity/Media3/ExoPlayer/background playback is created by preparation.

## Required code-path check before next QA
Confirm committed `main` shows:
- share target creates tab and starts foreground lease at share time;
- share target directly launches the preparation Activity;
- preparation Activity `onCreate()` marks host/resolving, creates/configures WebView, starts direct resolver and schedules 12s browser fallback;
- MainActivity/dashboard/card open is not part of preparation startup.

## Current quality/UI backlog
After PH BG passes:
- fix strict initial 720 preference;
- fix/verify manual 480 switching;
- test HH separately;
- add app-language selector;
- later refine icon while preserving white-E/purple identity, making it less boxy/more refined and purple more prominent.

## Current priority
1. Wait for CI on `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`.
2. Re-inspect committed share-time path.
3. If CI passes, one PH link only for first transparent-overlay lifecycle test.
4. Key proof: preparation Activity reaches `PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED` and does not immediately PAUSE/STOP simply because Vivaldi is visually underneath.
5. Target READY without opening ExternalPlayer/card or pressing Browser Step.
6. If failure, export operations log before Browser Step.
7. If one link passes, then test 2–3 PH links for browser-slot serialization.
8. No HH yet.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
