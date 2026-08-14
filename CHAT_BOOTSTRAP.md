# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` completely before substantive work. Keep both state files current.

## Communication / workflow
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Use connected GitHub tools directly whenever possible. Source should contain abundant English comments.
- Never restart from scratch or repeat old PASS QA without a regression reason.

## Safety boundary
PH and HH are real technical playback targets tested by the user. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed. Do not inspect/describe media content or thumbnail imagery. Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials. Never ask user for PH/HH titles or thumbnails. Only clearly identified age/18+ and cookie prompts may be auto-handled.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise best below 1080p. Protect Vivaldi share; yt-dlp first/browser fallback; automatic best/manual fallback; video+audio; adaptive/sibling quality switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate ranking/order protections; no imagery-based resolver decisions; one ExoPlayer playback session.

Previously verified: Bitmovin/PH/HH core baseline, #62 follow-up, #74 clean loading. Signing activation is deliberately deferred; debug APK QA continues.

## #162 device findings — must remember
User found:
- foreground `ExternalPlayer` share did not visibly raise app;
- BG tabs stayed `En cola`; actual preparation only began after manually opening app / clicking tab or `Paso del navegador`;
- required BG semantics: Vivaldi remains visible while ExternalPlayer actually runs behind it and immediately prepares the tab; later dashboard should already show identifiable/READY if success; never just store URL and wait;
- safe browser fallback should be automatic after ordinary direct-resolver failure;
- swipe close failed;
- Back from Player exposed `Elegir video`; normal Back must never do that;
- PH quality switch worked, HH did not;
- buffering felt slow;
- failed/queued tabs were hard to identify;
- arrows for reorder were uncomfortable; use long-press drag like Vivaldi Mobile;
- pseudo-random thumbnail was not useful/representative.

## #187 regression pass
Designated app-code head: `5b71129faa0f9815189b515e5e87bdd166c52216`.

Implemented:
- new exported `ForegroundShareActivity` trampoline for reliable visible `ExternalPlayer` share -> MainActivity;
- `BG - External Player` creates tab immediately, establishes hidden `BackgroundPreparationActivity` first, then leaves Vivaldi visible while prep runs; no ExoPlayer/background playback;
- each explicit BG share gets its own excluded document task (`documentLaunchMode=always`), not one `singleTask`;
- explicit BG shares may prepare independently; automatic next-tab preload remains serialized;
- coordinator tracks launching vs real active hidden prep, with 2s launch watchdog and WorkManager fallback;
- unexpectedly destroyed RESOLVING hidden prep returns to QUEUED and schedules recovery;
- direct challenge/login/captcha-style yt-dlp failure is normalized into safe browser fallback, while DRM/paywall/subscription/purchase/geo remain protected terminal errors; browser stage still never auto-bypasses challenge/login/payment/DRM/geo;
- READY hidden browser prep saves local page title;
- pending generic cards display source host until better title exists;
- BG READY completion starts best-effort local thumbnail extraction before dashboard opens; foreground warm-up retries if needed;
- thumbnail timestamp is stable: saved/current position, else ~35% known duration, else 15s; no image-content analysis;
- RecyclerView + ItemTouchHelper dashboard: long-press drag reorder, sideways swipe close, × still available, arrows removed; live state refresh pauses during gesture;
- `PlayerNavigationRuntime`: normal Back from Player -> dashboard with CLEAR_TOP/SINGLE_TOP, never chooser; explicit recovery may still open candidates;
- HH quality reinforcement: numeric requested sibling quality re-applied to actual adaptive Media3 group, Actual confirmed only by `VideoSize`, bounded 3 re-applies;
- quality verification retries no longer repeatedly re-seek, reducing avoidable rebuffering.

No global Media3 LoadControl threshold change in #187. If actual media buffering remains slow after corrected prep/quality behavior, tune that separately based on device result.

## CI
- #179 failed only because moved Worker called unqualified `preloadNext`; fixed by `4ab2c978f6e632e5cc65f4bc28533168daccccee`.
- #182 build/upload passed after compile fix.
- #185 PASS with final BG document-task model.
- **#187 PASS through Android build + debug APK upload**.
- Run `31771897702`.
- Artifact `9208455395`.
- Artifact ZIP digest `sha256:09488226ed025f3b22f5540e8b7740d8dff0424cf03936e9ae4d9877bd7293b9`.
- APK SHA-256 `f3915a70a5b97f22bc54a6e736155b966b8f157e54fa650b4a962dbef21f98f1`.
- Build #187 is designated device QA. Later state-only commits do not supersede it.

## Next focused QA
Test only the current regressions:
1. `ExternalPlayer` visibly opens/raises app.
2. BG keeps Vivaldi visible while prep actually runs before opening ExternalPlayer.
3. Multiple BG shares progress independently beyond QUEUED.
4. HH automatic direct -> safe browser fallback, title/READY and thumbnail behavior before manual tab click.
5. No background playback.
6. Long-press drag reorder + swipe close.
7. Back from Player -> dashboard, never `Elegir video`.
8. HH actual manual quality switching + quick PH regression check.
9. Report whether actual playback buffering is still slow.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
