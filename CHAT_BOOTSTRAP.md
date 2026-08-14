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

## Build #192 architecture
App-code commit: `a85f03a30ffdcda6d3f3c1c130ee799fd523e3f0`.

`BG - External Player` now launches a self-owned `BackgroundShareActivity` which creates the tab, marks preparation requested/RESOLVING, moves its own transparent task behind Vivaldi, starts direct yt-dlp, and on ordinary direct miss starts its own WebView browser discovery. Normal BG preparation no longer depends on clicking a dashboard card; QUEUED/RESOLVING cards are inert.

BG host is intentionally `excludeFromRecents=true`, so absence from Android Recents is expected and is not proof of failure. `ExternalPlayer` foreground chooser uses `ForegroundShareActivity` to explicitly raise `MainActivity`.

CI #192 PASS: run `31820367544`, artifact `9226733915`, APK SHA-256 `89cafe287d0f3bcf6a63b545efaa9a89ae936e0a42ee50eb2b6f6d6a7a997960`. CI is compile/package proof only.

## #192 PH device QA — authoritative
User shared three PH links with `Compartir enlace` -> `BG - External Player`.

Improvement:
- when ExternalPlayer was opened later, all three tabs were already in `Preparando` without clicking each individual tab first. This materially fixes the old #187 tab-click-to-start coupling.

Failure:
- all three automatic preparations eventually failed after roughly **244–270 seconds**;
- final state: `Falta paso del navegador` / `NEEDS_ATTENTION` plus Browser Step;
- manually clicking `Paso del navegador` succeeded for each PH link in about **5–10 seconds**;
- Back then returned correctly to the dashboard with tab information present.

Code clue:
- BG browser timeout itself is only 22s, so most of the 244–270s is almost certainly being spent before that, in the currently unbounded direct/yt-dlp call;
- visible `BrowserResolverActivity` observes Service Worker requests while #192 BG copy does not;
- #192 BG WebView is INVISIBLE and 1x1 while the successful Browser Step uses a normal visible WebView.

## Browser-fallback decision
Do not literally fake a press of the `Paso del navegador` UI button.

Instead, ordinary fallback should automatically run the **same technical browser-discovery engine/signals as the successful Browser Step behind Vivaldi**, while the dashboard/tab continues to mean `Preparando`.

Required semantics:
- direct yt-dlp remains first but must have a bounded BG deadline so it cannot stall for ~4 minutes;
- ordinary direct miss/deadline automatically enters the shared browser-discovery path;
- BG success stores READY metadata only and never launches Player/ExoPlayer/background playback;
- NEEDS_ATTENTION / Browser Step is reserved for genuine human interaction such as CAPTCHA/challenge/login/payment/DRM/region restrictions;
- an ordinary automatic browser timeout must not be mislabeled as human interaction required;
- Service Worker handling for multiple concurrent BG browser stages must avoid cross-tab request attribution, likely by safe serialization or equivalent routing.

## Other #192 PH observations
- tab long-press move/reorder WORKS;
- closing tabs WORKS;
- resume from previous position WORKS;
- Back flow worked in the tested manual-browser path;
- PH quality change DOES NOT WORK in #192 and is a current regression, superseding older PH quality PASS for current-build status;
- Settings needs an explicit app-language selector;
- all tests in this round were PH only.

Do **not** ask for HH testing yet. PH already proves the BG automatic fallback blocker. Fix PH BG first; then use HH separately, especially for quality switching.

## Future logo direction — remember
Next visual iteration, not the current BG fix:
- keep current white-E / purple identity and letter concept;
- keep it recognizable as the same family;
- make it less square/boxy and more stylized/refined;
- make the purple portions more prominent.

## Current priority
1. Fix PH automatic BG browser fallback; no HH test yet.
2. Bound the direct stage so ordinary failure reaches browser fallback promptly.
3. Unify/mirror successful visible Browser Step discovery in BG, including Service Worker and normal WebView initialization behavior, without background playback.
4. Keep NEEDS_ATTENTION only for real protected/user-interaction cases.
5. CI/code-path inspect, then give one focused PH BG QA APK.
6. After PH BG passes, revisit current PH/HH quality switching regression.
7. Add app-language selection in Settings in a later settings/UI pass.
8. Later apply the recorded logo refinement.
