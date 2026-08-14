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
- `BG - External Player` created a tab and attempted a second hidden `BackgroundPreparationActivity` task behind Vivaldi;
- one document task per BG share, launch watchdog, WorkManager fallback, interrupted-prep recovery;
- safe direct -> browser fallback without protected-access bypass;
- local title/source-host identification and BG thumbnail warm-up;
- RecyclerView long-press drag reorder + swipe close;
- Back from Player -> dashboard;
- HH exact Media3 requested-track reinforcement with Actual from `VideoSize`;
- quality verification retries no longer repeatedly re-seek.

No global Media3 LoadControl threshold change in #187.

## #187 device QA — critical BG failure
User tested build #187 and reported the core BG failure remains, with a stronger clarification:
- ExternalPlayer did not really prepare behind Vivaldi after `BG - External Player`;
- the persistent tab was created but stayed essentially inactive/`En cola`;
- manually opening ExternalPlayer was not enough to start every pending tab;
- **each individual tab had to be clicked/opened before that tab began preparation/resolution**.

This is a confirmed runtime failure despite CI PASS. Do not ask the user to repeat or re-explain it.

Root coupling found in code:
- BG share depended on a second hidden Activity hand-off;
- if that hand-off did not survive, the tab stayed QUEUED;
- MainActivity explicitly called `prepareNow()` when a queued card was selected;
- WorkManager could run the direct stage, but an ordinary miss handed browser continuation to a coordinator which needed an Activity lifecycle.

## Current focused architecture change
The next BG build is changing the normal share path so `BackgroundShareActivity` itself is the preparer:
- create the persistent tab immediately;
- persist PREPARATION_REQUESTED and move to RESOLVING immediately;
- keep this same transparent document Activity alive and move its own task behind Vivaldi;
- run yt-dlp/direct resolution in that Activity;
- on an ordinary direct miss, automatically run the safe hidden-WebView browser discovery stage in that **same Activity**;
- save local page title when available;
- mark READY and start best-effort local thumbnail extraction;
- no second hidden Activity hand-off is required for a normal BG share;
- `BackgroundPreparationActivity` remains only for retry/preload/process-recovery paths;
- `android:noHistory` is removed from `BackgroundShareActivity` because it conflicts with intentionally keeping the preparation host alive behind Vivaldi;
- each explicit share still gets its own document task, so several BG tabs can prepare independently;
- no ExoPlayer/background playback is added.

Dashboard decoupling for this focused build:
- READY card -> Play/Continue;
- NEEDS_ATTENTION -> Browser Step only when genuine interaction is required;
- ERROR -> explicit recovery retry remains;
- QUEUED/RESOLVING card -> disabled/inert, and clicking it no longer calls `prepareNow()`.

That makes the next device test meaningful: if a BG tab did not prepare before the dashboard was opened, clicking the card cannot hide the failure by starting normal preparation.

## Local technical BG diagnostics
`VideoTabStore` is being extended with non-content timestamps/stages:
- preparation requested;
- preparation host created;
- direct resolver started/finished;
- browser stage requested;
- browser WebView created;
- browser discovery started;
- READY;
- last technical stage + timestamp.

The dashboard shows a compact local `tech ... +Ns` marker. Diagnostics contain no media imagery/content, credentials or page text.

## CI
- #179 compile-only failure fixed in `4ab2c978f6e632e5cc65f4bc28533168daccccee`.
- #182 passed.
- #185 passed with document-task model.
- #187 passed build/upload on app-code head `5b71129faa0f9815189b515e5e87bdd166c52216`, run `31771897702`, artifact `9208455395`, APK SHA-256 `f3915a70a5b97f22bc54a6e736155b966b8f157e54fa650b4a962dbef21f98f1`.
- **CI PASS does not mean BG passed device QA. #187 BG is failed.**
- The self-owned-share successor must compile/pass CI before a new APK is designated.

## Next session priority
1. Read `PROJECT_STATE.md` + this file fully and treat GitHub `main` as authoritative.
2. Finish/verify the self-owned `BackgroundShareActivity` architecture.
3. Verify share-time state transition starts without MainActivity/card selection.
4. Verify automatic direct -> safe browser fallback remains protected.
5. Keep the technical lifecycle diagnostics local and non-content.
6. Run CI; then update BOTH state files with exact commit/run/artifact details.
7. Give one focused QA APK only for “BG prepares before app/tab is manually opened”.
8. Do not resume broad #187 QA until BG is solved unless the user volunteers results.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
