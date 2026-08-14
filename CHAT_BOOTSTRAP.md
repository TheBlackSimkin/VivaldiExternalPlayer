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

## #162 / #187 BG device finding — must remember
The core device failure is stronger than simply “app had to be opened”:
- BG share created/saved a tab but did not really prepare behind Vivaldi;
- manually opening ExternalPlayer was not enough to prepare all pending tabs;
- **each individual pending tab had to be clicked/opened before its own preparation began**.

Build #187 passed CI but failed this runtime requirement. Never treat #187 as a successful BG fix or ask the user to re-explain that result.

Other #162/#187 items remain known but BG is first: foreground share visibility, swipe close, Back -> dashboard, PH manual quality worked / HH manual quality failed, buffering felt slow, distinguish pending cards, long-press drag reorder, representative thumbnail timing.

## Root cause found after #187
The old BG path was still coupled to UI/lifecycle in two places:
- `BackgroundShareActivity` created the tab, launched a **second** hidden `BackgroundPreparationActivity`, then finished. The user's device showed that hand-off was not reliable enough.
- `MainActivity.performPrimaryAction()` explicitly called `UnifiedPreparationCoordinator.prepareNow()` for a non-RESOLVING card, so clicking an individual queued tab became a reliable preparation trigger.
- WorkManager could do direct resolution without UI, but on an ordinary direct miss it handed browser continuation to `browserStageNeeded()`, which needed a usable Activity lifecycle.

## Build #192 focused BG architecture
App-code commit: `a85f03a30ffdcda6d3f3c1c130ee799fd523e3f0`.

Normal `BG - External Player` no longer depends on the second-Activity hand-off:
- exported `BackgroundShareActivity` creates the persistent tab immediately;
- records PREPARATION_REQUESTED / preparation host creation and moves the tab to RESOLVING immediately;
- moves **its own already-created transparent document task** behind Vivaldi and stays alive;
- starts direct/yt-dlp resolution from that same share Activity;
- on an ordinary direct miss, automatically starts the safe hidden-WebView browser discovery stage in that same Activity;
- local browser title is saved on success;
- READY starts best-effort local thumbnail extraction;
- each explicit BG share retains its own document task, allowing several shares to prepare independently;
- `BackgroundPreparationActivity` remains for retry/preload/process-recovery only;
- `android:noHistory` was removed from the BG share Activity because it conflicted with keeping that host alive behind Vivaldi;
- no ExoPlayer/background playback is added.

Dashboard decoupling in #192:
- READY -> Play/Continue;
- NEEDS_ATTENTION -> explicit Browser Step for genuine interaction;
- ERROR -> explicit recovery retry;
- QUEUED/RESOLVING -> disabled/inert; clicking the card no longer calls `prepareNow()`.

This is an intentional QA safeguard: a card click cannot mask a BG lifecycle failure by starting normal preparation.

## #192 expected preparation state path
Normal direct success:
`TAB_CREATED -> PREPARATION_REQUESTED -> RESOLVING -> DIRECT_STARTED -> DIRECT_FINISHED -> READY`

Ordinary direct miss needing safe browser discovery:
`... -> DIRECT_FINISHED -> BROWSER_REQUESTED -> BROWSER_DISCOVERY_STARTED -> READY / NEEDS_ATTENTION / ERROR`

Protected browser interaction is never bypassed. Genuine challenge/login/payment/DRM/region interaction may stop at NEEDS_ATTENTION/ERROR.

## Local technical diagnostics
`VideoTabStore` now persists lifecycle-only timestamps/stages for:
- creation;
- preparation requested;
- preparation host created;
- direct resolver started/finished;
- browser stage requested;
- browser WebView created;
- browser discovery started;
- READY;
- last technical stage + timestamp.

Dashboard shows a compact local marker such as `tech DIRECT_STARTED +1s`. It contains no media imagery/content, credentials or page text.

## Recovery caveat
If Android unexpectedly destroys the self-owned BG Activity while still RESOLVING, the tab returns to QUEUED, records `BG_HOST_DESTROYED_RECOVERY_QUEUED`, and WorkManager direct recovery is scheduled. WorkManager can run direct resolution without UI; browser continuation after a recovery direct miss still needs a valid Activity lifecycle. The normal fresh BG-share path avoids that dependency because its own share Activity already owns the WebView.

## CI
- #187 app-code head `5b71129faa0f9815189b515e5e87bdd166c52216`; run `31771897702`; artifact `9208455395`; APK SHA-256 `f3915a70a5b97f22bc54a6e736155b966b8f157e54fa650b4a962dbef21f98f1`. **Device BG failed despite CI PASS.**
- #192 app-code commit `a85f03a30ffdcda6d3f3c1c130ee799fd523e3f0`.
- GitHub Actions **run #192 PASS**, run ID `31820367544`.
- Debug artifact ID `9226733915`.
- Artifact ZIP digest `sha256:76b1d2ff8c9d929d995c91d563bdb4d833996f4d50253cc29adee1dc65763a04`.
- Extracted APK SHA-256 `89cafe287d0f3bcf6a63b545efaa9a89ae936e0a42ee50eb2b6f6d6a7a997960`.
- Build #192 is designated for **focused BG lifecycle device QA only**. CI is not proof that BG runtime behavior passed.
- A later state-only commit recording #192 does not supersede the #192 app-code APK.

## Share-target entry requirement — explicit verification before #192 QA
This is a high-priority recurring failure mode and must be checked whenever share architecture changes:
- `ExternalPlayer` must cause Android to launch `ForegroundShareActivity`, which explicitly starts/raises `MainActivity` with the shared URL. `MainActivity.acceptSharedUrl()` must immediately call the normal foreground `resolveAndPlay()` flow. The user should visibly see ExternalPlayer come to the foreground.
- `BG - External Player` must cause Android to launch `BackgroundShareActivity`. That Activity itself must create the persistent tab and begin preparation, then move its own transparent task **into the background** so Vivaldi stays visible while preparation continues.
- Do not describe a CI pass or a successful `startActivity()` call as device proof. Actual foreground raising and actual continued background execution still require device QA.

English wording note: Portuguese `segundo plano` is naturally **“the background”** in this context, e.g. “the app keeps preparing the video in the background.”

## Future launcher/logo direction — NOT part of #192
On the next visual iteration, preserve the current logo identity/colors and letter concept, but refine it so it is:
- less square / less boxy;
- more stylized and fluid;
- still clearly recognizable as the current logo family;
- with the purple portions more noticeable/prominent.
Do not change build #192 for this visual request.

## Current priority
1. Test #192 for both share-entry semantics first: `ExternalPlayer` visibly raises/opens the app and begins the foreground flow; `BG - External Player` leaves Vivaldi visible while the app's BG Activity actually starts preparation.
2. For BG specifically, verify tabs prepare before ExternalPlayer or any individual tab is manually opened.
3. Add multiple BG tabs and do not click their cards before observing their eventual states/technical stages.
4. If failure remains, report the exact visible `tech ...` marker for each tab; this should identify Activity/direct/browser lifecycle stopping points without media-content inspection.
5. Do not resume broad #187 QA until BG is confirmed, unless the user volunteers results.
6. After the BG lifecycle is solved, include the requested logo refinement in a later visual iteration, not in #192.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
