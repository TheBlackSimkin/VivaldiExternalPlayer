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

## Build #215 — current focused PH target
App-code commit: `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`.
GitHub Actions run #215 PASS; run ID `31843858363`.
Debug artifact ID `9235240761`; artifact ZIP digest `sha256:8a942d8dc4ee50c00e2448ed4bbd54147fedd9a0432c5b920328dee29fc70e6f`; ZIP size `25,999,191` bytes.
Extracted APK size `35,488,506` bytes; APK SHA-256 `7aea335b8a2f941898ec5737804a89ddb719deb301e156f555408da15d57133e`.

Normal BG path:
`BG share -> create tab immediately -> foreground lease -> launch preparation Activity in same excluded task on DEFAULT display -> keep it RESUMED with nearly-transparent NOT_TOUCHABLE window -> READY/ERROR/NEEDS_ATTENTION -> remove task`.

Important architecture:
- no `moveTaskToBack()`;
- no `ActivityOptions.launchDisplayId`;
- no normal virtual-display dependency;
- share handoff uses `finish()`, not `finishAndRemoveTask()`, so it does not kill the preparer task;
- historical class `BackgroundVirtualPreparationActivity` is reused as the actual resolver host on the primary/default display;
- `TabbedPlayerApplication` sets its window alpha to 0.01, transparent background and `FLAG_NOT_TOUCHABLE`;
- it intentionally leaves focus enabled for browser equivalence;
- lifecycle log records `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED/STARTED/RESUMED/PAUSED/STOPPED/DESTROYED_CALLBACK`;
- existing direct/browser resolver logic remains: direct first, 12s browser budget, Service Worker serialization, browser network/DOM/Performance/page-config discovery, 30s timeout, conservative cookie/18+ handling;
- normal BG path must never silently return to `WORKER_ENQUEUED`;
- no PlayerActivity/Media3/ExoPlayer/background playback is created by preparation.

## Build #215 post-CI code inspection — PASS
Committed app code was re-inspected after CI:
- `BackgroundShareActivityV2.onCreate()` creates the pending tab, marks preparation requested, starts the foreground lease and directly launches the preparation Activity at share time;
- it does not move the task behind Vivaldi and does not use a secondary display;
- `BackgroundVirtualPreparationActivity.onCreate()` marks host/resolving, creates/configures its full-size WebView, calls direct resolution and schedules the 12s browser fallback;
- MainActivity/dashboard/card open is not part of startup;
- manifest keeps BG task excluded from Recents and preparation Activity transparent.

Later state-file commits do not supersede #215 app code.

## New UX requirements recorded before #215 test
These were explicitly raised before proceeding and are now required for the next UI-oriented build after the one-link #215 lifecycle result.

### Persistent tabs / stored tabs clarity
Current code persists the **currently open** tab list automatically in `VideoTabStore` and `MainActivity` shows that list directly. There is no separate archive of previously closed tabs. Closing/swiping a tab removes it from the persisted list, and `Clear saved tabs` clears that same current list.

The current wording is misleading because `saved tabs` / `pestañas guardadas` sounds like a hidden history/library. Required next UI build:
- state clearly that currently open tabs are automatically restored after app/process restart;
- rename `Clear saved tabs` to `Clear all tabs` (Spanish equivalent too);
- explain that closing/swiping forgets a tab;
- do not add a redundant manual reload button for the same auto-loaded store;
- optionally add a real `Recently closed` / restore feature later if closed-tab recovery is wanted.

If an open tab disappears after a real restart without being closed/cleared, treat that separately as an actual persistence bug.

### App language selector
Promised but still not implemented. Required persistent Settings selector:
- System default;
- English;
- Español.
It should apply app-wide and survive restart.

### GitHub log reporting
Current operations-log button uses Android's normal share sheet. User wants a more convenient GitHub route.

Never embed a PAT/repository token/OAuth client secret in the APK. GitHub issue creation through the REST API requires authenticated Issues write permission.

Preferred secure first implementation:
- retain full `Share operations log`;
- add `Report log on GitHub` / `Reportar registro en GitHub`;
- open this repository's GitHub new-issue page with version/build plus a bounded recent sanitized log section pre-filled;
- user reviews and submits in GitHub, so ExternalPlayer stores no GitHub credential;
- keep the prefilled log bounded to avoid an oversized URL; full log remains available through Android Share.

True automatic one-tap API submission can be considered later only with an explicit GitHub App/OAuth authorization and safe token handling.

## Sequencing
Do not rebuild #215 with these UI changes before its first focused PH lifecycle test. #215 is a clean architecture experiment. Capture the one-link result first; then the next UI build can bundle the persistent-tab wording fix, language selector and secure GitHub-report shortcut while leaving resolver code unchanged.

## Current quality/UI backlog
After PH BG passes:
- fix strict initial 720 preference;
- fix/verify manual 480 switching;
- test HH separately;
- UI bundle immediately after #215 one-link result: persistent-tab wording, System/English/Español selector, secure GitHub log-report shortcut;
- optionally implement a genuine recently-closed-tab restore feature;
- later refine icon while preserving white-E/purple identity, making it less boxy/more refined and purple more prominent.

## Current priority
1. Test build #215 with ONE PH link only, before changing #215 app code.
2. Clean old tabs, leave ExternalPlayer by Home/switching, return to Vivaldi; do not force-stop/swipe ExternalPlayer.
3. Share one PH via `BG - External Player`, keep Vivaldi visually on screen for 45–60s.
4. Try one harmless normal Vivaldi scroll/touch after share; it must still respond despite the transparent preparation Activity being technically top/resumed.
5. Key log proof: `PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED` appears and is not immediately followed by PAUSED/STOPPED before completion.
6. Target READY before opening ExternalPlayer/card and without Browser Step.
7. If not READY, export operations log before Browser Step. Old markers `WORKER_ENQUEUED`, `BG_HOST_DESTROYED_RECOVERY_QUEUED`, and `VIRTUAL_PREP_LAUNCH_FAILED` are not expected.
8. After that one-link result, implement the three-item UI bundle above without changing the resolver architecture.
9. If one link passes, test 2–3 PH links for browser-slot serialization on #215 or a later build whose resolver code is verified unchanged.
10. No HH yet.

## Priority override — 2026-08-14
This section supersedes the older wording above about which UI items belong in the build immediately after #215.

After the one-link #215 lifecycle result is captured, the **very next build must include all three of these items** while keeping resolver architecture unchanged unless #215 itself proves a resolver/lifecycle fix is required:
1. persistent-tab UX correction **plus a genuine Recently closed / Restore tab feature** so closed-tab recovery is real rather than a redundant reload of the already-auto-restored open list;
2. persistent app-language selector: System default / English / Español;
3. launcher/logo refresh, preserving the white-E/purple identity while making the icon less boxy/more refined and purple more prominent.

The secure `Report log on GitHub` shortcut remains approved, but it is **deferred until after those three required items**. Keep the existing Share operations log action in the meantime.

#215 itself must remain unchanged for the current one-PH lifecycle test.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.