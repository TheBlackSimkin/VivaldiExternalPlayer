# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA, failures, decisions, or priorities change.

## Working rules
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Source should contain abundant English comments.
- Use connected GitHub tools directly whenever possible.
- PH and HH are real technical playback targets tested by the user. URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed.
- Do **not** inspect, describe, classify, summarize, or request PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected playback baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p; >1080 only rare fallback.

Preserve:
- Vivaldi share targets;
- yt-dlp first + browser fallback;
- automatic best + manual fallback;
- video+audio handling;
- adaptive and sibling quality switching;
- double-tap ±10s;
- seek preview;
- rotation;
- bilingual UI;
- 80 stored / 20 manual candidate limits;
- first-seen HLS/DASH ordering;
- no generic playlist bonus;
- soft child audio/video demotion;
- page-config family IDs;
- no imagery-based resolver/ranking;
- exactly one actual ExoPlayer playback session.

Previously verified core baseline includes Bitmovin/PH/HH playback, build #62 follow-up, build #74 clean loading, and #109 install/About/Settings/no-background-playback/local browser title. Do not repeat old PASS items without a regression reason.

Permanent release signing remains deferred. Debug GitHub Actions APKs are the QA path; never commit a permanent signing key.

## BG preparation history
### #192
Self-owned share Activity proved preparation could begin before tab/card clicks, but three PH tabs took ~244–270s and ended at Browser Step. Manual Browser Step worked ~5–10s.

### #202
Added V2 12s direct budget, bounded yt-dlp, normal-size hidden WebView, Service Worker + DOM/Performance/page-config discovery and serialized browser ownership. Device QA still took minutes and ended `NEEDS_ATTENTION`; manual Browser Step worked ~5–7s. Recents absence PASS/preferred.

Quality regressions first documented here:
- manual 240p works;
- manual 480p does not;
- Auto can choose 1080 despite 720 existing;
- icon still unchanged at that point.

### #205 — decisive lifecycle log
Foreground keep-alive protected the process but not the stopped Activity. Device log showed:
- Activity STOPPED at 16:42:01.422;
- destroyed-host recovery began ~107ms later;
- `WORKER_ENQUEUED` at 16:42:01.569;
- Activity DESTROYED at 16:42:01.599.

Conclusion: do not put the WebView Activity behind Vivaldi and depend on Android preserving a STOPPED Activity. Exportable operations log itself is a strong PASS/useful feature.

### #212 — private virtual-display experiment, authoritative FAIL
App-code head `2052aaa7af4dc02e59b7f90acefca0368d2fd0fe`; CI #212 PASS; APK SHA-256 `a350995bb2b4040f2571c3d8aebb92b0dbc0eb8f2a6fc77dc530a32537765125`.

One-PH device result: immediate `VIRTUAL_PREP_LAUNCH_FAILED +0s`. Log proved the private virtual display was created but Android denied launching the first normal app Activity onto it with `launchDisplayId=2`.

AOSP diagnosis: bootstrapping an Activity on that untrusted display would require embedding conditions including privileged `ACTIVITY_EMBEDDING`. Do not request privileged/system permissions and do not retry the dead-end virtual-display Activity design.

## Build #215 — first real automatic PH BG completion
App-code commit `b4d3b5eba4a3428a74f7cfccf924bd254bcee5f7`.
GitHub Actions #215 PASS, run ID `31843858363`.
APK SHA-256 `7aea335b8a2f941898ec5737804a89ddb719deb301e156f555408da15d57133e`.

Architecture:
`BG share -> create tab immediately -> foreground lease -> launch preparation Activity on DEFAULT display -> keep Activity RESUMED with transparent/NOT_TOUCHABLE window -> direct/browser resolver -> READY/ERROR/NEEDS_ATTENTION`.

Post-CI code inspection confirmed preparation is scheduled/started from the BG share path itself. MainActivity/dashboard/card opening is not part of startup.

### #215 one-PH device QA — core BG PASS, overlay UX FAIL
User followed the requested flow: cleaned tabs, Home, opened Vivaldi, shared one PH URL via `BG - External Player`, did not open/click ExternalPlayer during the wait, then inspected the dashboard and exported the log.

Authoritative observations:
- Vivaldi remained visually present after the BG share.
- Vivaldi did **not** respond to scroll/touch for about 3–5 seconds after share.
- Then there was a brief ~0.5 second visible preparation/video-frame flash on the physical display. Do not inspect or describe that frame/content.
- When ExternalPlayer was manually opened after the wait, the tab was already `READY +9s`.
- Dashboard reported automatic actual quality 720p in this run.
- No card click was needed to start preparation.

Important log sequence:
- `19:16:17.962 BG_SHARE_OVERLAY_HANDOFF_STARTED`;
- `19:16:17.984 PRIMARY_OVERLAY_PREP_LAUNCH_REQUESTED`;
- `19:16:18.087 PRIMARY_OVERLAY_PREP_ACTIVITY_CREATED display=0 alpha=0.01`;
- `19:16:18.434 DIRECT_STARTED`;
- `19:16:18.449 PRIMARY_OVERLAY_PREP_ACTIVITY_RESUMED`;
- `19:16:20.138 DIRECT_FINISHED`;
- `19:16:20.140 BROWSER_REQUESTED`;
- automatic browser fallback then continued and the dashboard later proved READY at ~9s.

Conclusion:
- **Core requirement PASS:** automatic PH preparation can now complete before ExternalPlayer or its card is opened.
- **UX requirement FAIL:** alpha `0.01` + `FLAG_NOT_TOUCHABLE` still interferes with touches to Vivaldi and leaks a brief visible frame.
- One 720p result is encouraging but does **not** yet close the previously observed 1080-vs-720 regression globally.

## Current architecture change after #215
Android 12+ official window behavior says pass-through touches under an Activity window using `FLAG_NOT_TOUCHABLE` are allowed when that window is **fully transparent** (`LayoutParams.alpha == 0`). A merely translucent Activity window such as #215's 0.01 is not the same exception.

Current code change therefore keeps the successful #215 RESUMED preparation architecture but changes only the preparation window compositor alpha:
- `BG_PREPARATION_WINDOW_ALPHA`: `0.01f -> 0.0f`;
- keep `FLAG_NOT_TOUCHABLE`;
- keep focus enabled because browser equivalence may depend on focus;
- keep preparation Activity RESUMED;
- no `moveTaskToBack()`;
- no virtual display;
- no legacy Worker fallback for normal BG preparation.

Goal: preserve #215's ~9s automatic READY behavior while eliminating the 3–5s touch block and visible flash.

## Required UX bundle — being implemented in the next app-code build
The user explicitly required these items in the very next build after #215; they must not be postponed again.

### 1. Persistent/open tabs clarity + real Recently closed restore
Current/open tabs already persist in local SharedPreferences and reload automatically after app/process restart. There is no manual reload requirement.

New implementation:
- Settings wording explicitly says open tabs restore automatically;
- `Clear saved tabs` becomes `Clear all tabs` / `Borrar todas las pestañas`;
- swiping/closing one tab archives a bounded snapshot in a real `Recently closed` list;
- Settings shows `Recently closed (N)` / `Cerradas recientemente (N)`;
- user can pick one closed tab to restore its source/resolved payload, position and quality state;
- user can clear Recently closed history;
- bulk `Clear all tabs` does not flood Recently closed with every cleared tab;
- Recently closed history is persisted locally with a limit of 12 entries.

If an OPEN tab disappears after a real restart without being closed/cleared, treat that separately as a persistence bug.

### 2. Explicit app-language selector
New Settings selector:
- System default;
- English;
- Español.

Implementation uses AndroidX `AppCompatDelegate.setApplicationLocales`, declares `en`/`es` in `res/xml/locales_config.xml`, and enables AndroidX locale auto-storage for API 24–32. Android 13+ can also synchronize the app locale with system per-app language settings.

### 3. Launcher/header icon refresh
Required visual direction:
- preserve white-E + purple identity;
- less boxy / more refined;
- purple more prominent.

New vector mark uses a larger purple diamond, dark cutout, rounded compact white E, and purple center accent. Both adaptive foreground and pre-adaptive launcher fallback use the refreshed mark. Main dashboard header already uses the same foreground drawable, so it will update too.

### Secure GitHub log reporting
Approved idea but deferred until after the three required items above. Keep ordinary Share operations log. Never embed a PAT/repository token/OAuth client secret in the APK.

## Quality status
Protected policy remains exact 720 -> otherwise 1080 -> otherwise best below 1080.

Current known findings:
- #215 one-PH automatic result reported actual 720p;
- earlier PH runs could initially choose 1080 despite 720 existing, so more than one result is needed before marking fixed;
- manual 240 works;
- manual 480 still needs repair/verification.

Do not mix a speculative 480-quality rewrite into the current touch/UX build unless a clearly isolated safe fix is found. After BG touch/visibility is clean, verify 720 policy and 480 switching before HH regression testing.

## Current UI / backlog
- long-press tab move/reorder WORKS from #192;
- closing tabs WORKS;
- resume position WORKS;
- tested Back flow after manual Browser Step WORKS;
- BG absence from Android Recents PASS/preferred;
- exportable operations log PASS/useful;
- required next-build Recently closed / language / icon work is implemented in current pending app-code changes;
- secure `Report log on GitHub` shortcut comes later.

## Current development priority
1. Commit the post-#215 app-code bundle: alpha 0.0 touch/flash fix + Recently closed restore + app-language selector + icon refresh.
2. Run CI and do not designate an APK merely because local edits exist.
3. Re-inspect committed share-time path after CI: tab/lease/preparation Activity/direct resolver/browser fallback must still start from BG share time, never dashboard/card opening.
4. Update both state files with exact CI run/artifact/hash.
5. Next focused device QA should use ONE PH link first and verify simultaneously:
   - automatic READY before app/card open remains working;
   - Vivaldi touch/scroll works immediately after BG share;
   - no visible black/preparation/video-frame flash occurs;
   - operation log shows preparation Activity still reaches RESUMED with `alpha=0.0`;
   - new Settings language selector works and persists;
   - Recently closed restore is visible/functional;
   - refreshed icon is actually visible.
6. If one-link result is clean, test 2–3 PH shares for browser-slot serialization.
7. Then fix/verify strict 720 preference + manual 480 switching.
8. No HH testing until PH BG/quality blockers are cleared.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
