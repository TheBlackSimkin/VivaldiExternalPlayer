# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA, failures, decisions, or priorities change.

## Working rules
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Source should contain abundant English comments.
- Use connected GitHub tools directly whenever possible.
- PH and HH are real technical playback targets tested by the user. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed. Do not inspect/describe/classify media content or thumbnail imagery; never ask user for PH/HH media content or thumbnails.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p; >1080 only rare fallback.

Preserve: Vivaldi share; yt-dlp first/browser fallback; automatic best/manual fallback; video+audio; adaptive and sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; 80 stored/20 manual candidate limits; first-seen HLS/DASH order; no generic playlist bonus; soft child audio/video demotion; page-config family IDs; no imagery-based resolver/ranking; one actual ExoPlayer playback session.

Previously verified core baseline includes Bitmovin/PH/HH playback, build #62 follow-up, build #74 clean loading, and #109 install/About/Settings/no-background-playback/local browser title. Do not repeat old PASS items without a regression reason.

Permanent release signing remains deliberately deferred. Debug GitHub Actions APKs are the QA path; never commit the permanent key.

## Historical BG progression
### #187
A second hidden `BackgroundPreparationActivity` handoff was unreliable. Pending tabs still had to be clicked before preparation began.

### #192
Self-owned share Activity proved preparation starts before tab/card clicks, but three PH tabs took ~244–270s and ended Browser Step. Manual Browser Step worked in ~5–10s.

### #202
`BackgroundShareActivityV2` added a 12s direct budget, bounded yt-dlp retries, normal-size hidden WebView, Service Worker observation, DOM/Performance/page-config discovery, and serialized browser ownership.

PH device QA still took minutes and ended `NEEDS_ATTENTION`; manual Browser Step worked in ~5–7s. Recents absence PASS/preferred.

Current PH quality regressions discovered in #202:
- manual 240p works;
- manual 480p does not;
- Auto chooses 1080p even when 720p exists;
- launcher icon remained unchanged.

### #205 — foreground keep-alive + operations log
App-code commit `48605a4c1eb8972d6275478993db5ce7b104478e`; CI #205 PASS.

Added foreground process lease and Settings -> Share operations log. Device log proved the phone destroys the BG Activity almost immediately after it becomes STOPPED behind Vivaldi:
- RESUMED 16:42:00.800;
- service CREATED 16:42:01.113;
- Activity STOPPED 16:42:01.422;
- destroyed-host QUEUED by 16:42:01.529;
- `BG_HOST_DESTROYED_RECOVERY_QUEUED` 16:42:01.546;
- `WORKER_ENQUEUED` 16:42:01.569;
- Activity DESTROYED 16:42:01.599;
- Worker direct resolution restarted 16:42:01.831.

Conclusion: foreground service protected the process, not the stopped Activity/WebView. The operations log itself is a PASS and should be retained.

## Build #212 — private virtual-display experiment
App-code head `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`; CI #212 PASS; APK SHA-256 `a350995bb2b4040f2571c3d8aebb92b0dbc0eb8f2a6fc77dc530a32537765125`.

Architecture attempted:
`BG handoff -> foreground lease -> private OWN_CONTENT_ONLY/PRESENTATION virtual display -> real Activity/WebView on that display`.

### #212 PH device QA — authoritative immediate FAIL
User followed the intended one-PH flow precisely:
1. opened ExternalPlayer;
2. cleaned old tabs;
3. pressed Home without force-closing/swiping ExternalPlayer;
4. opened Vivaldi;
5. opened PH;
6. shared URL via `BG - External Player`;
7. waited 45 seconds;
8. opened ExternalPlayer and captured the ERROR;
9. exported the operations log.

Dashboard result: `Error • tech VIRTUAL_PREP_LAUNCH_FAILED +0s`.

Log:
- `BG_SHARE_HANDOFF_STARTED`;
- `VIRTUAL_DISPLAY_CREATED`, display 2, 1080x2180, 440 dpi;
- about 111 ms later `VIRTUAL_PREP_LAUNCH_FAILED` with Android `Permission Denial ... launchDisplayId=2`;
- keep-alive service then observed the tab already in ERROR.

So the display itself was created successfully; Android denied launching the first normal app Activity onto it.

### Platform-source diagnosis after #212
AOSP `ActivityTaskSupervisor.isCallerAllowedToLaunchOnDisplay()` explains the denial for untrusted virtual displays:
- Activity must opt into embedding (`ActivityInfo.FLAG_ALLOW_EMBEDDED` / manifest `android:allowEmbedded=true`);
- even then, if the caller UID is not already represented by an Activity on that display, the caller needs `android.permission.ACTIVITY_EMBEDDING`;
- AOSP defines `ACTIVITY_EMBEDDING` as `signature|privileged`, so a normal third-party app cannot legitimately obtain it;
- `DisplayContent.isUidPresent()` checks for an existing `ActivityRecord`, so merely owning the virtual display is not enough to bootstrap the first Activity.

Decision: do **not** ship a fake `allowEmbedded=true` retry that would simply fail at the next privileged-permission check. Do not request privileged/system permissions.

#212 is no longer a QA target.

## Current architecture after #212 — transparent primary-display RESUMED preparation
Current app-code commit: `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`.
CI for this commit is pending at the time of this state update; do not designate an APK until CI passes.

Normal BG path is now:
`BG share -> create persistent tab at share time -> foreground process lease -> launch real preparation Activity in the same excluded task on DEFAULT display -> keep preparation Activity RESUMED with nearly-transparent NOT_TOUCHABLE window -> READY/ERROR/NEEDS_ATTENTION -> finish/remove task`.

Key design points:
- no `moveTaskToBack()`;
- no `ActivityOptions.launchDisplayId`;
- no normal virtual-display dependency;
- the short exported share Activity owns no resolver work and uses `finish()`, not `finishAndRemoveTask()`, after starting the preparer;
- the existing resolver Activity (historical class name `BackgroundVirtualPreparationActivity`) retains its already-tested direct/browser logic;
- `TabbedPlayerApplication` makes that preparation window alpha 0.01 and `FLAG_NOT_TOUCHABLE`, with transparent background;
- it deliberately does not set `FLAG_NOT_FOCUSABLE`, because browser/page code may depend on focus;
- the Activity stays top/resumed instead of entering the STOPPED state which #205 proved this phone destroys;
- Vivaldi should remain what the user visually sees underneath the almost-transparent Activity;
- foreground service still protects process importance;
- no PlayerActivity, Media3/ExoPlayer or background playback is created by BG preparation;
- normal BG preparation still must never silently enter `WORKER_ENQUEUED`.

New operations-log lifecycle markers include:
- `BG_SHARE_OVERLAY_HANDOFF_STARTED`;
- `PRIMARY_OVERLAY_PREP_LAUNCH_REQUESTED`;
- `PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED`;
- `PRIMARY_OVERLAY_PREP_ACTIVITY_STARTED`;
- `PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED`;
- PAUSED/STOPPED/DESTROYED callback markers if Android changes its lifecycle.

The existing preparation host still records direct/browser markers such as `DIRECT_STARTED`, `DIRECT_BUDGET_EXPIRED`, `BROWSER_AUTO_REQUESTED`, `BROWSER_DISCOVERY_STARTED`, `BROWSER_CANDIDATE`, and `BG_PREPARATION_READY`.

### Code-path requirement before next QA designation
Before giving the next APK, verify committed `main` still does all preparation scheduling at BG-share time:
- share target creates the tab and starts the lease immediately;
- it launches the preparation Activity directly from the share flow;
- preparation Activity's `onCreate()` marks host/resolving, creates/configures WebView, calls direct resolver and schedules the 12s fallback;
- opening MainActivity/dashboard/card is not part of startup.

## Current quality status / later fix
Protected policy remains exact 720 -> otherwise 1080 -> otherwise best below 1080. Current PH runtime violates it by initially choosing 1080 when 720 exists.

Known current quality findings:
- manual 240 works;
- manual 480 fails;
- initial Auto may pick 1080 despite 720 being available.

Do not mix speculative quality changes into the BG lifecycle blocker. Once PH automatic BG preparation passes, fix/verify strict initial family quality selection and 480 switching before HH regression testing.

## Current UI / backlog
- long-press tab move/reorder WORKS from #192;
- closing tabs WORKS from #192;
- resume position WORKS from #192;
- tested Back flow after manual Browser Step WORKS from #192;
- Settings needs explicit app-language selector;
- BG absence from Android Recents PASS and preferred;
- exportable operations log PASS/useful from #205;
- launcher/logo refinement still required: preserve white-E/purple identity, less boxy/more refined, purple more prominent.

Do not request HH testing yet. PH automatic BG preparation remains the blocker.

## Current development priority
1. Wait for CI on app-code commit `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`.
2. Inspect committed share/preparation path after CI; confirm preparation starts at share time and there is no virtual-display/Worker dependency in the normal path.
3. If CI passes, designate one focused PH one-link QA APK for the transparent-primary-overlay lifecycle.
4. First proof should be lifecycle: `PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED` must appear and must not be immediately followed by PAUSED/STOPPED while the user simply leaves Vivaldi visible.
5. Target: READY without opening ExternalPlayer/card or pressing Browser Step.
6. If it fails, export operations log before Browser Step.
7. If one-link PH passes, test 2–3 PH shares for browser-slot serialization.
8. After PH BG passes, fix strict 720 preference + 480 switching, then test HH separately.
9. Add app-language selector after BG blocker.
10. Later perform launcher/logo refinement.

## QA format
Whenever asking user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
