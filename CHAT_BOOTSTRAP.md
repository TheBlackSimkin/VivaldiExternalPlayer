# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` and this file before substantive work. Keep both current whenever QA, architecture, failures, priorities, or decisions change.

## Safety / communication
- Explain plainly; user is not an advanced developer. Use connected GitHub directly. Keep source well commented in English.
- PH/HH are technical playback targets only. Allowed: URLs, manifests, codecs, resolutions, request metadata, resolver/candidate ranking, playback states/errors, local titles.
- Never inspect/describe/classify/summarize/request PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials.
- Never add background playback or a second ExoPlayer.

## Protected architecture / playback
Build #234 background preparation is protected:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.

No preparation Activity on display 0. No PlayerActivity/Media3/ExoPlayer during BG preparation.

Preserve exactly one ExoPlayer, yt-dlp first/browser fallback, automatic/manual quality, video+audio, adaptive/sibling switching, gestures, seek preview, rotation, bilingual UI, candidate limits/order, and no imagery-based ranking.

Automatic quality: exact 720p -> otherwise 1080p -> otherwise highest below 1080p -> >1080p rare fallback only.

## Accepted player/UI baseline
Build #278 is DEVICE PASS and is the accepted app-code baseline.
- App commit: `8b0566c68eb9082c0aed62e202edfc1a29232983`
- Actions run `31905713180`; build job `95063044270`; artifact `9252287185`
- APK SHA-256 `ee5893ef22a7a38758293ce9647ac133f09bea8527855c836c8dd65f13ba6043`

Accepted behavior: compact gear and Video Quality menus, requested-vs-actual quality verification including 480p path, Audio, app-level Volume/Mute, speed, Diagnostics, fullscreen, exact `[tab count] [gear] [fullscreen]`, tab dashboard, controller auto-hide, double-tap ±10s, no visible ±10 buttons.

Build #249 palette remains protected: purple `#B05CFF`, charcoal `#17191F` family, white primary; green/amber/red semantic only.

Build #264 retained behavior: Recents privacy, stale-source `Refresh source`, Vivaldi health, Brave-as-is compatibility with generic Android share targets. No Brave-specific code without a real regression.

## Rare HH DNS edge case
Some rare older HH HLS sources can fail because a downstream host genuinely does not resolve (`UnknownHostException` / `EAI_NODATA`). Treat as source availability. Safe recovery only: Retry, Refresh source, or an already-detected legitimate alternate candidate. Never rewrite hosts or invent mirrors.

## Latest QA / decisions
User reports broad PH/HH technical regression is complete and passed, except the previously known old/expired-tab revival limitation.

User repeated the same normal Vivaldi-style flows in Brave and reports they worked correctly. No Brave-specific work is needed.

User reports **31 tabs** could be opened/prepared in background without issue. Broader stress hardening is postponed/lower priority for this personal-use project unless a concrete regression appears.

## Permanent signing status
Permanent release signing is established and the upload pipeline now works.

Signing history:
- Build #284 original + retry failed at release packaging because the separate generated key-password value did not match the effective PKCS#12 private-key password.
- Local validation proved the backup keystore is healthy and that the store password is also the key password.
- Signing-only fix commit `cd92c51936cb34594cfb820de0e2c311b8b09253` makes Gradle use `VEP_KEYSTORE_PASSWORD` for both.
- Run `32543326847` then built and v2-verified release but artifact upload failed only because GitHub Actions storage quota was full.
- Run `32546091175`, job `96964652777`, head `74ff674e912e10d6b78fb1ba5fe8544d0325fdc1` succeeded completely: debug build, release build, `apksigner` v2 verification with one signer, debug upload and signed release upload.
- Signed artifact `9468570335`.
- Artifact ZIP SHA-256 `b4e70c64f9aa594b402a87ee819ad669ac3ea20aa8926dbed23dc99bf25e849e`.
- Contained release APK SHA-256 `1fe9f098aa202634c7a2a45f61ec8e1d40fecb6f15b9eb79e81e94a3a179f74d`.

Permanent certificate fingerprint recorded from the backup:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

Current workflow verifies signature validity but does not print the certificate fingerprint. Do not claim the downloaded feature-checkpoint APK fingerprint was independently matched unless actually verified.

Do not change signing secrets casually. Never commit permanent signing material.

Debug -> release migration warning remains: same application ID, different signatures, so Android may reject installing release over debug. Uninstall normally clears app-local state; do not casually tell the user to uninstall.

## Feature / cleanup phase now in progress
Compile-verified at run `32546091175`:
1. **Permanent original page URL** via `TabOriginStore`, including capture at the real V2 BG share handoff.
2. **Update status of tabs** with conservative serialized checks and separate health state.
3. **Revive expired tabs** from original page URLs through `TabRevivalCoordinator`, serialized through the protected foreground-service/private-display architecture. No preparation Activity, PlayerActivity, Media3 or second ExoPlayer.
4. **Close all tabs** main-screen confirmation, using the existing close/Recently Closed safety path and cancelling queued work where applicable.
5. **Favorites** storing original page URL + title.
6. **Private Favorites** storing encrypted title/URL data with Android Keystore AES-GCM; Android biometric/device authentication before decrypt/render; `FLAG_SECURE`; excluded from Recents; no thumbnails; locks again when leaving.
7. Favorites launch new tabs through the protected service/private-display preparation path.
8. Settings exposes Favorites and Private Favorites.
9. New feature copy is bilingual English/Spanish.
10. Android biometric dependency and all currently added feature code compile in both debug and release.

## Still to finish
- Player compact gear actions for **Add to Favorites** and **Add to Private Favorites**, without disturbing #278 player controls.
- **Open in Vivaldi Private** stays unimplemented unless a supported mechanism can guarantee private/incognito launch for an arbitrary URL. Never label ordinary `ACTION_VIEW` as guaranteed private.
- Conservative diagnostics/operations-log cleanup only; do not alter resolver ranking, playback policy, #234 architecture or one-player ownership.
- About/version/build/README/release consistency.
- Final signed feature build and focused QA.

## Current roadmap
1. Finish player Favorites controls, conservative diagnostics cleanup, and release consistency.
2. Build and verify final permanently signed feature APK.
3. Focused QA on changed areas + quick protected-baseline sanity checks.
4. Preserve signing continuity for all future updates.

## Stored backlog
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials
- safe proven-dead historical code cleanup only later
- old “Return to existing Vivaldi task/tab” idea: reconsider later, never auto-implement
- broader hardening only when personal-use evidence justifies it

## QA format
Whenever explicitly asking the user to test an APK, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.

Do not add extra code blocks to that QA request.
