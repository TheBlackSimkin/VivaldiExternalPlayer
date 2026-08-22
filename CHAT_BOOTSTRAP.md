# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` and this file before substantive work. Keep both current whenever QA, architecture, failures, priorities, or decisions change.

## Safety / communication
- Explain plainly. Use connected GitHub directly. Android UI remains bilingual English/Spanish. Keep source comments in English.
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
Build #278 is DEVICE PASS and remains the protected player/UI baseline.
- app commit `8b0566c68eb9082c0aed62e202edfc1a29232983`
- Actions run `31905713180`
- APK SHA-256 `ee5893ef22a7a38758293ce9647ac133f09bea8527855c836c8dd65f13ba6043`

Accepted behavior: compact gear/Video Quality, requested-vs-actual verification including 480p, Audio, app-level Volume/Mute, speed, Diagnostics, fullscreen, exact `[tab count] [gear] [fullscreen]`, dashboard, controller auto-hide, double-tap ±10s, no visible ±10 buttons.

Build #249 palette remains protected: purple `#B05CFF`, charcoal `#17191F` family, white primary; green/amber/red semantic only.

Build #264 retained behavior: Recents privacy, stale-source `Refresh source`, Vivaldi health, Brave generic-share compatibility. Do not add Brave-specific code without a real regression.

## 0.3.0 device-QA checkpoint
0.3.0 / versionCode 3 was the first permanently signed post-feature release.
- head `4eea6d33c17bf2eeab029238ce837809d3debe6f`
- Actions `32546673916`, job `96966228456`
- signed artifact `9468760721`
- ZIP SHA-256 `5a3fc8b6c44484131afc110f0977cf59aec19418d55a43b1755129189943a863`
- APK SHA-256 `eff2656754ae4ed1515218823cb8ba38205a058276d82aab005bb6f8ee0d4ace`

0.3.0 focused QA: all tested items PASS. Only `Revive expired` was NOT TESTED because there was no naturally expired tab. Passed areas included player baseline, original URL, Update status, Close all, Recently Closed, Favorites, Private Favorites auth/privacy, private favorite launch/remove, bilingual UI, Brave regression, persistence, and player spot-check.

Normal Android file-tap installation still inexplicably reports `App not installed`; ADB `install -r` works. Do not infer a signing problem from the file-tap path. Do not uninstall a working signed install just to update it.

## Permanent signing
Signing is established. No permanent key material is in the repo.
- signing fix commit `cd92c51936cb34594cfb820de0e2c311b8b09253`
- permanent certificate SHA-256 `8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`
- CI uses `apksigner verify --verbose --print-certs`, so certificate continuity can now be independently checked in Actions logs.
- Do not change signing secrets casually.

## Current release: 0.3.1 / versionCode 4
0.3.1 adds the Vivaldi Private fallback after a deeper device/APK investigation showed no safe external arbitrary-URL private launch route.

Vivaldi Android tested: `8.1.4099.123`, package `com.vivaldi.browser`.

Private/incognito investigation results:
- `org.chromium.chrome.browser.incognito.OPEN_PRIVATE_TAB` opens a genuine blank private tab.
- adding a URL to that intent is ignored.
- `LauncherShortcutActivity` private shortcut route is non-exported and blocked to third-party callers.
- normal `ACTION_VIEW` + Chromium incognito extra did not produce private URL navigation.
- private launcher then normal `ACTION_VIEW` opens the URL in a normal tab.
- `chromium.shortcut.action.OPEN_NEW_INCOGNITO_WINDOW` resolves to no activity and does nothing.
- local APK/Dex string inspection found no safe exported arbitrary-URL private entry point.

Decision: never label ordinary `ACTION_VIEW` as private. The implemented fallback is **Vivaldi Private + Copy URL**.

Behavior:
1. read the stored original page URL via the permanent origin path;
2. copy that original URL to clipboard;
3. open Vivaldi's genuine blank Private tab through `IncognitoTabLauncher`;
4. explicitly tell the user to paste the copied original URL in the Private tab;
5. never pass the URL through normal `ACTION_VIEW` for this action.

Implementation is isolated in `VivaldiPrivateLauncher.kt` plus one player-gear row and bilingual strings. Resolver, player ownership, and protected BG-preparation architecture are untouched.

0.3.1 build:
- feature/release head `6a195bd0019b3335236375650f4123f11f3bc3fc`
- Actions run `32552025984`
- job `96980224294`
- build success: debug + release
- `apksigner`: v2=true, one signer
- signer cert SHA-256 `8c87e1f6a7a4873f12cb25ba348bef665057159f16a65b9059e5e1d7c0b95e7c` (matches permanent certificate)
- signed artifact `9470356520`
- ZIP SHA-256 `c4660868785eb06b52ba274a14e4f0b9e3f34aa7ac452e7374be9c0b55a90606`
- APK SHA-256 `6e02fb3df1ea831a42d4d4c582a37c46f4b68772f9cd8f438ae821fc9fa0db51`

User installed 0.3.1 successfully using ADB `install -r` over the existing signed release. The focused QA for **Vivaldi Private + Copy URL** has been requested but no PASS/FAIL result has been received yet. Do not mark it passed until the user reports the result.

## Current implemented feature set
- permanent original page URL via `TabOriginStore`
- conservative serialized Update status checks
- Revive expired via `TabRevivalCoordinator` through protected service/private-display preparation
- Close all confirmation using Recently Closed safety path
- Favorites and Private Favorites
- Private Favorites: encrypted local AES-GCM blob + system-auth UI gate, `FLAG_SECURE`, no Recents/no thumbnails/relock on leaving
- settings Favorites screens
- player gear Add to Favorites + Add to Private Favorites
- player gear Vivaldi Private + Copy URL
- English/Spanish strings

Private Favorites caveat: the Keystore AES-GCM key is not currently configured as a hardware/user-auth-bound key. The UI requires system authentication before decrypt/render, but do not overclaim stronger cryptographic binding.

## Remaining / next session
1. Get PASS/FAIL for the new Vivaldi Private + Copy URL device test.
2. Test `Revive expired` when a naturally expired tab is available.
3. Conservative diagnostics/operations-log cleanup only; no resolver ranking/playback/BG architecture changes.
4. Inspect Recently Closed retention before trusting Close All for large sets; prior inspection suggested a small limit around 12 while 31-tab stress use has passed.
5. Keep signing continuity and update both state files after new QA/release changes.

Current documentation head after `PROJECT_STATE.md` refresh and before this bootstrap write: `3710b73e5f73784c430c27dbb2827218c080e965`.

## Rare HH DNS edge
Some old HLS sources may fail because a downstream host genuinely does not resolve (`UnknownHostException` / `EAI_NODATA`). Treat as source availability. Safe recovery only: Retry, Refresh source, or an already-detected legitimate alternate. Never rewrite hostnames or invent mirrors.

## Stored backlog
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials
- safe proven-dead historical cleanup only later
- old “Return to existing Vivaldi task/tab” idea: reconsider later, never auto-implement
- broader hardening only when real personal-use evidence justifies it

## QA format
Whenever explicitly asking the user to test an APK, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.

Do not add extra code blocks to that QA request.
