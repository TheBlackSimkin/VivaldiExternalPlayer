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

## Build #215 — transparent primary-display RESUMED preparation
App-code commit: `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`.

Normal BG path is now:
`BG share -> create persistent tab at share time -> foreground process lease -> launch real preparation Activity in the same excluded task on DEFAULT display -> keep preparation Activity RESUMED with nearly-transparent NOT_TOUCHABLE window -> READY/ERROR/NEEDS_ATTENTION -> finish/remove task`.

Key design points:
- no `moveTaskToBack()`;
- no `ActivityOptions.launchDisplayId`;
- no normal virtual-display dependency;
- the short exported share Activity owns no resolver work and uses `finish()`, not `finishAndRemoveTask()`, after starting the preparer;
- the existing resolver Activity (historical class name `BackgroundVirtualPreparationActivity`) retains its direct/browser logic;
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

### Build #215 post-CI code-path inspection — PASS
Inspection of committed app-code commit `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7` confirms normal preparation is started from the BG share itself:
- `BackgroundShareActivityV2.onCreate()` creates the persistent pending tab, marks preparation requested, starts the foreground lease, and directly launches `BackgroundVirtualPreparationActivity` in the same task on the default display;
- the share handoff does not call `moveTaskToBack()`, does not use `launchDisplayId`, and does not involve MainActivity/dashboard/card selection;
- `BackgroundVirtualPreparationActivity.onCreate()` marks preparation host/resolving, creates/configures its full-size WebView, calls `attemptDirectFirst()`, and schedules the 12-second direct-browser fallback timer;
- `TabbedPlayerApplication` applies the transparent/NOT_TOUCHABLE window behavior and journals CREATED/STARTED/RESUMED/PAUSED/STOPPED lifecycle transitions;
- the manifest keeps both BG Activities excluded from Recents and gives the preparation host the transparent theme.

### Build #215 CI / focused QA artifact
- GitHub Actions run **#215 PASS**.
- Run ID: `31843858363`.
- App-code commit: `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`.
- Debug artifact ID: `9235240761`.
- Artifact ZIP size: `25,999,191` bytes.
- Artifact ZIP digest: `sha256:8a942d8dc4ee50c00e2448ed4bbd54147fedd9a0432c5b920328dee29fc70e6f`.
- Extracted debug APK size: `35,488,506` bytes.
- Extracted APK SHA-256: `7aea335b8a2f941898ec5737804a89ddb719deb301e156f555408da15d57133e`.
- #215 is the designated focused **one-PH transparent-overlay lifecycle QA APK**.
- Later state-only commits do not supersede #215 app code.
- CI proves compile/package integrity only; device QA is still required for lifecycle/visibility/touch behavior and automatic PH completion.

## UX findings reported before #215 device QA
These were explicitly raised before proceeding with the #215 test and must not be lost.

### Persistent tabs wording / restore behavior
Current implementation persists the **currently open** `VideoTabStore` list in local SharedPreferences and loads it automatically when the app initializes. `MainActivity` displays `VideoTabStore.allTabs()`, so there is no separate hidden saved-tabs archive and no manual reload step in the intended design. Closing/swiping a tab calls `VideoTabStore.close()` and permanently removes that tab from the persisted open-tab list.

User feedback: Settings wording such as `saved tabs` / `pestañas guardadas` strongly suggests a separate archive of previous/closed tabs, but none exists. The user has therefore reasonably expected a `reload stored tabs` control and reports never seeing such stored history.

Required UI correction for the next non-resolver UI build:
- make it explicit that **current/open tabs are restored automatically** after app/process restart;
- rename `Clear saved tabs` / `Borrar pestañas guardadas` to wording such as `Clear all tabs` / `Borrar todas las pestañas` so it does not imply a hidden library;
- explain that swiping/closing a tab forgets it;
- do not add a meaningless manual reload button merely to reload the same in-memory persistent list;
- consider a genuine `Recently closed` / `Restore closed tab` archive as a separate useful feature if closed-tab recovery is desired.

If open tabs fail to reappear after a true process/app restart without having been closed/cleared, treat that as a persistence bug and investigate it separately.

### Explicit app-language selector — required
The promised language control is still missing. Current Settings has the four preference switches, tab clearing, operations-log sharing and About, but no language control.

Required next UI build: add a persistent app-wide selector in Settings for at least:
- `System default`;
- `English`;
- `Español`.

It must affect the app UI consistently and survive restart. Do not rely solely on the phone language once this selector exists.

### Secure GitHub operations-log reporting
Current `Share operations log` uses Android's ordinary text share sheet. User requested a more convenient way to send QA logs directly to this GitHub repository.

Security requirement: never embed a GitHub PAT, repository write token, OAuth client secret or other reusable credential in the APK. Creating GitHub issues through the REST API requires authenticated Issues write permission.

Preferred first implementation for a later UI build:
- retain the existing full `Share operations log` action;
- add a separate `Report log on GitHub` / `Reportar registro en GitHub` action;
- open this repository's GitHub `new issue` page in the browser with build/version information and a bounded recent portion of the sanitized operations log pre-filled in the title/body;
- user can review and press GitHub Submit, so no GitHub credential is stored by ExternalPlayer;
- cap the prefilled log body so the issue URL cannot grow without bound; keep ordinary Share for the complete long log.

A future true one-tap API submission is possible only with an explicit GitHub App/OAuth authorization design and secure token handling; do not add that complexity or credential risk during the current resolver/lifecycle blocker.

### Sequencing decision
Do **not** rebuild or modify #215 before its focused one-PH lifecycle test. #215 is intentionally a clean architecture experiment.

After the one-link #215 result is captured, the next build must bundle the persistent-tabs UX correction, explicit app-language selector, and launcher/icon refresh. The secure GitHub-report shortcut is approved but deferred until after those three required items.

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
- next build after #215 result MUST add persistent-open-tab clarity plus genuine Recently closed / Restore tab recovery;
- next build after #215 result MUST add System default / English / Español selector;
- next build after #215 result MUST refresh launcher/logo while preserving white-E/purple identity, making it less boxy/more refined and purple more prominent;
- secure `Report log on GitHub` remains approved but deferred until after those three items;
- BG absence from Android Recents PASS and preferred;
- exportable operations log PASS/useful from #205.

Do not request HH testing yet. PH automatic BG preparation remains the blocker.

## Current development priority
1. Device-test build #215 with **one PH link only** before changing its code.
2. Before the real share, clean old tabs, leave ExternalPlayer with Home/switching, and return to Vivaldi; do not force-stop or swipe ExternalPlayer from Recents.
3. Share one PH URL via `BG - External Player` and keep looking at Vivaldi for about 45–60 seconds.
4. Verify Vivaldi remains visually on screen and perform one harmless normal scroll/touch to confirm the transparent `NOT_TOUCHABLE` preparation window does not block browser interaction.
5. Key lifecycle proof: operations log should show `PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED` and should not show an immediate PAUSED/STOPPED before preparation reaches READY/ERROR/NEEDS_ATTENTION.
6. Target: tab is READY before opening ExternalPlayer/card, with no Browser Step.
7. If not READY, export operations log before Browser Step. `WORKER_ENQUEUED`, `BG_HOST_DESTROYED_RECOVERY_QUEUED`, or `VIRTUAL_PREP_LAUNCH_FAILED` are not expected in the normal #215 path.
8. After the one-link #215 result, the **very next build** must implement: (a) persistent-tab clarity + genuine Recently closed/Restore tab, (b) System/English/Español selector, and (c) launcher/icon refresh. Keep resolver architecture unchanged unless #215 itself proves a lifecycle/resolver fix is required.
9. If one-link PH passes, test 2–3 PH shares for browser-slot serialization on #215 or a later build whose resolver code is verified unchanged.
10. After PH BG passes, fix strict 720 preference + 480 switching, then test HH separately.
11. Add secure GitHub log-report shortcut after the required three-item UI bundle.

## QA format
Whenever asking user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.