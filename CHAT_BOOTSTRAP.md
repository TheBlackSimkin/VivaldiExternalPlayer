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
App-code head: `5b71129faa0f9815189b515e5e87bdd166c52216`.

Implemented before device test:
- `ForegroundShareActivity` trampoline for visible foreground share;
- `BG - External Player` creates tab immediately and attempts a second hidden `BackgroundPreparationActivity` task behind Vivaldi;
- one document task per BG share, launch watchdog, WorkManager fallback, interrupted-prep recovery;
- safe direct -> browser fallback without protected-access bypass;
- local title/source-host identification and BG thumbnail warm-up;
- RecyclerView long-press drag reorder + swipe close;
- Back from Player -> dashboard;
- HH exact Media3 requested-track reinforcement with Actual from `VideoSize`;
- quality verification retries no longer repeatedly re-seek.

No global Media3 LoadControl threshold change in #187.

## #187 device QA — critical BG failure
User tested build #187 and reported **the same BG failure remains**:
- ExternalPlayer did not actually open/run in the background after choosing `BG - External Player`;
- the tab was created/saved, but it did not prepare while Vivaldi remained visible;
- preparation only started after the user manually opened ExternalPlayer.

This is a confirmed runtime failure of the current second-hidden-Activity/task handoff model despite GitHub Actions compiling successfully. Do not ask the user to repeat or re-explain this #187 result.

Required next direction:
- fix BG lifecycle/execution before broad QA;
- do not treat `startActivity(BackgroundPreparationActivity)` + `moveTaskToBack()` as proof that the preparer really runs on-device;
- strongly consider collapsing the flow so exported `BackgroundShareActivity` itself becomes the transparent preparer and remains alive after moving its own task behind Vivaldi, instead of launching/destroying a second hidden Activity;
- otherwise use an Android-supported execution mechanism that demonstrably starts direct preparation while the user stays in Vivaldi; browser/WebView stage still needs a valid lifecycle;
- persisted tab state must leave QUEUED automatically without manual ExternalPlayer open;
- add technical lifecycle/stage timestamps if useful so device QA can distinguish Activity-never-created vs destroyed vs resolver-not-started vs browser-stage-not-started;
- preserve no background playback and all safety/ranking protections.

## CI
- #179 compile-only failure fixed in `4ab2c978f6e632e5cc65f4bc28533168daccccee`.
- #182 passed.
- #185 passed with document-task model.
- #187 passed build/upload on app-code head `5b71129faa0f9815189b515e5e87bdd166c52216`, run `31771897702`, artifact `9208455395`, APK SHA-256 `f3915a70a5b97f22bc54a6e736155b966b8f157e54fa650b4a962dbef21f98f1`.
- **CI PASS does not mean BG passed device QA. #187 BG is failed.**

## Next session priority
1. Verify GitHub access and read `PROJECT_STATE.md` + this file fully.
2. Treat #187 BG result as authoritative device evidence.
3. Inspect `BackgroundShareActivity`, `BackgroundPreparationActivity`, `UnifiedPreparationCoordinator`, manifest task flags, WorkManager fallback, and persisted state transitions.
4. Redesign BG so the preparation engine genuinely starts immediately after share while Vivaldi remains foreground.
5. Build a focused successor APK only after compile passes.
6. Do not ask for the rest of #187 QA until BG is fixed, unless the user volunteers results.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
