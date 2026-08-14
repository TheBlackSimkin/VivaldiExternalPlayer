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

## #192 PH device QA — authoritative
The user shared three PH links with `Compartir enlace` -> `BG - External Player`.

#192 materially fixed the old #187 coupling: when ExternalPlayer was opened later, all three tabs were already in `Preparando` without clicking each individual tab first.

But automatic preparation still failed:
- each tab took about 244–270 seconds;
- all ended `Falta paso del navegador` / `NEEDS_ATTENTION`;
- manually pressing `Paso del navegador` then succeeded in roughly 5–10 seconds for each PH URL;
- Back returned correctly to dashboard with tab information present.

Other authoritative #192 PH observations:
- tab long-press move/reorder WORKS;
- closing tabs WORKS;
- resume position WORKS;
- tested Back flow WORKS;
- PH quality change DOES NOT WORK and is a current regression;
- Settings needs an explicit app-language selector;
- this round was PH only.

Do not request HH testing yet.

## Browser-fallback design decision
Do not fake a literal UI click on `Paso del navegador`.

The desired behavior is:
`BG share -> direct/yt-dlp first -> automatic hidden browser fallback behind Vivaldi -> READY`

`Paso del navegador` / NEEDS_ATTENTION should be reserved for genuine human/protected interaction such as CAPTCHA/challenge/login/payment/DRM/region restrictions. An ordinary no-candidate browser timeout is a technical ERROR, not proof that the user must interact.

## Build #202 implementation — current app-code target
App-code head: `b02561ed0c154b7f8abe66bd4e3212ba780b7fdf`.

Commits:
- `b970a60e4ab639feb550695ad69815f85aa06a02` — new `BackgroundShareActivityV2`;
- `08a8729b87498f68b6edce68bdd75cda93799dd7` — manifest routes `BG - External Player` to V2;
- `b02561ed0c154b7f8abe66bd4e3212ba780b7fdf` — bound yt-dlp socket/retry behavior.

Current BG share behavior:
- exported `BackgroundShareActivityV2` creates the persistent tab immediately and marks preparation requested/RESOLVING;
- it moves its own transparent document task behind Vivaldi;
- direct yt-dlp starts first;
- after a 12-second BG budget, automatic browser fallback starts even if the blocking direct extractor has not yet returned;
- resolver.py now uses `socket_timeout=12`, `retries=1`, `extractor_retries=1`, `fragment_retries=1` to avoid the prior multi-minute retry chain;
- the hidden WebView is technically VISIBLE and normally laid out at Activity size rather than INVISIBLE/1x1, while the Activity itself remains behind Vivaldi;
- `mediaPlaybackRequiresUserGesture=true`; BG success stores READY only and never starts PlayerActivity/ExoPlayer/background playback;
- discovery observes WebView requests, Service Worker requests, DOM VIDEO/SOURCE, Performance resources, and page-config URLs/declared qualities;
- candidate ranking/safety stays protected: 80 cap, first-seen HLS/DASH, page-config family IDs, ad demotion, soft child audio/video demotion, no imagery, quality 720 -> 1080 -> best below 1080;
- exact cookie/18+ prompts may be handled conservatively; CAPTCHA/challenge/login/payment/subscription/DRM/region controls are never automated.

Multiple simultaneous BG shares:
- direct attempts remain independent;
- hidden browser discovery is serialized because Android ServiceWorkerController is process-wide;
- a waiting tab may show `tech BROWSER_WAITING_FOR_SLOT` and should automatically continue when the previous tab releases the browser slot;
- this avoids cross-tab Service Worker request attribution.

State rules:
- browser success -> READY;
- detected genuine protected/human challenge -> NEEDS_ATTENTION;
- ordinary automatic browser timeout/no candidate -> ERROR;
- QUEUED/RESOLVING dashboard clicks remain inert and are not normal preparation triggers.

## Build #202 CI / artifact
- GitHub Actions run #202 PASS; run ID `31830708434`.
- Debug artifact ID `9230598176`.
- Artifact ZIP digest `sha256:6b08abecf71c650b8844d44ba4bc664642127d986bee009fe1b2318fee95302a`.
- Artifact ZIP size `25,979,380` bytes.
- Extracted debug APK size `35,466,650` bytes.
- Extracted APK SHA-256 `dab7f1a312d838e966eb552867679b696f887cf6a71a94c2d0c354fd99458114`.
- #202 is the designated focused PH BG QA APK. Later state-only commits do not supersede its app code.
- CI is compile/package proof only; device QA must confirm PH automatic BG success.

## Share-entry semantics
- `ExternalPlayer` remains the foreground chooser target via `ForegroundShareActivity`, which explicitly raises `MainActivity` with the shared URL.
- `BG - External Player` now points to exported `BackgroundShareActivityV2`.
- BG is intentionally `excludeFromRecents=true`; absence from Android Recents is expected and is not a failure criterion.

## Future logo direction — remember
Next visual iteration, not this BG fix:
- keep the current white-E / purple identity and letter concept;
- keep it recognizable as the same family;
- make it less square/boxy and more stylized/refined;
- make the purple portions more prominent.

## Current priority
1. Test build #202 with PH only; no HH yet.
2. Add 2–3 PH links through `BG - External Player` without opening/clicking their cards first.
3. Vivaldi should stay foreground; ExternalPlayer does not need to appear in Recents.
4. Ordinary direct misses should reach automatic browser fallback around the 12-second BG budget rather than ~4 minutes.
5. Automatic PH browser success should become READY without pressing `Paso del navegador`.
6. `BROWSER_WAITING_FOR_SLOT` is acceptable temporarily for simultaneous BG tabs; it should advance automatically later.
7. NEEDS_ATTENTION should appear only for a genuine detected human/protected interaction; ordinary timeout should be ERROR.
8. If PH #202 passes, update both state files, then test HH separately and revisit quality switching.
9. Add app-language selection in Settings after the BG blocker.
10. Later apply the recorded logo refinement.

## QA format
Whenever asking user to test, always provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only compact answer format.
