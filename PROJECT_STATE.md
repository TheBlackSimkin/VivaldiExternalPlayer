# Vivaldi External Player — Project State

GitHub `main` is authoritative. Read this file and `CHAT_BOOTSTRAP.md` before substantive work. Keep both current whenever architecture, QA, failures, priorities, or decisions change.

## Working / safety rules
- Explain plainly; Android UI remains bilingual English/Spanish. Keep source comments in English.
- Use connected GitHub directly whenever possible.
- PH/HH are technical playback targets only: URLs, manifests, codecs, resolutions, request metadata, resolver/candidate ranking, playback state/errors, and local titles are allowed.
- Never inspect, describe, classify, summarize, request, or analyze PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywalls/subscriptions, authentication, regional restrictions, CAPTCHA/anti-bot, or import browser credentials.
- Never add background playback or a second ExoPlayer.

## Protected architecture
Build #234 background preparation architecture is protected:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.

Do not reintroduce a preparation Activity on display 0. Do not use PlayerActivity/Media3/ExoPlayer during BG preparation.

## Protected playback policy
Preserve exactly one ExoPlayer, yt-dlp first/browser fallback, automatic + manual quality, video + audio, adaptive/sibling switching, double-tap ±10 seconds, seek preview, rotation, bilingual UI, existing candidate limits/order, and no imagery-based ranking.

Automatic quality policy:
1. exact 720p
2. otherwise 1080p
3. otherwise highest below 1080p
4. >1080p only as rare fallback

## Protected UI baseline
Build #249 palette is approved: purple `#B05CFF`, charcoal `#17191F` family, white primary content; green/amber/red semantic only. Do not change unless explicitly requested.

Build #278 is the accepted player/UI DEVICE PASS baseline.
- App-code commit: `8b0566c68eb9082c0aed62e202edfc1a29232983`
- Actions run `31905713180`; build job `95063044270`; artifact `9252287185`
- APK SHA-256 `ee5893ef22a7a38758293ce9647ac133f09bea8527855c836c8dd65f13ba6043`

Accepted behavior includes compact gear and Video Quality menus, requested-vs-actual verification including 480p, Audio, app-level Volume/Mute, speed, Diagnostics, fullscreen, exact lower-right `[tab count] [gear] [fullscreen]`, tab dashboard, controller auto-hide, double-tap ±10s, no visible dedicated ±10 buttons, and approved colors.

Player-control/menu UI is settled unless regression evidence appears.

## Build #264 retained behavior
Build #264 established Recents privacy, stale-source `Refresh source`, Vivaldi health, and Brave Mobile compatibility using generic Android share targets. Do not add Brave-specific code without a concrete regression.

## Rare HH DNS edge case
A very small number of older HH HLS sources may fail because a downstream host genuinely does not resolve (`UnknownHostException` / `EAI_NODATA`). Treat this as source/upstream availability, not a general app regression.

Safe recovery only: Retry playback, Refresh source, or an already-detected legitimate alternate candidate. Never rewrite hostnames, invent mirrors, substitute DNS hosts, or bypass access controls.

## 0.3.0 feature baseline and device QA
Version `0.3.0` / versionCode `3` was the first permanently signed feature release after the large post-signing feature phase.

Final 0.3.0 build:
- head `4eea6d33c17bf2eeab029238ce837809d3debe6f`
- Actions run `32546673916`
- build job `96966228456`
- signed artifact `9468760721`
- signed artifact ZIP SHA-256 `5a3fc8b6c44484131afc110f0977cf59aec19418d55a43b1755129189943a863`
- release APK SHA-256 `eff2656754ae4ed1515218823cb8ba38205a058276d82aab005bb6f8ee0d4ace`
- `apksigner`: v2=true, one signer, certificate SHA-256 `8c87e1f6a7a4873f12cb25ba348bef665057159f16a65b9059e5e1d7c0b95e7c`

0.3.0 device QA result: every tested item passed. `Revive expired` was the only item NOT TESTED because there was no naturally expired tab available. Tested-pass areas included playback/player baseline, original-URL foundation, Update status, Close all, Recently Closed, Favorites, Private Favorites auth/privacy, private favorite launch/remove, English/Spanish UI, Brave regression, persistence, and player sanity checks.

Installation note: Android's normal file-tap installer has repeatedly shown `App not installed` for these release APKs for an unexplained installer/file-manager-path reason. ADB in-place update works normally. 0.3.0 installed successfully over the previous signed release using `adb install -r` without uninstalling, proving package/signing continuity.

## Permanent release signing — established
Permanent signing is established. No permanent key material is stored in the repository.

Signing-only fix commit: `cd92c51936cb34594cfb820de0e2c311b8b09253`. Gradle uses `VEP_KEYSTORE_PASSWORD` for both PKCS#12 store and private-key password.

Permanent certificate SHA-256 fingerprint:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

CI now runs `apksigner verify --verbose --print-certs`, so signer continuity can be checked directly from the Actions log. Do not change signing secrets casually.

## Current release: 0.3.1
Current `main` release is `0.3.1` / versionCode `4`.

Current head before this state-file update: `6a195bd0019b3335236375650f4123f11f3bc3fc`.

0.3.1 adds the explicit Vivaldi Private fallback after deeper ADB/APK investigation proved that Vivaldi does not expose a usable arbitrary-URL private launch route for third-party apps.

### Vivaldi Private investigation decision
Tested on Vivaldi Android `8.1.4099.123`, package `com.vivaldi.browser`:
- `org.chromium.chrome.browser.incognito.OPEN_PRIVATE_TAB` -> opens a genuine blank private tab.
- Adding `-d https://...` to that launcher -> URL is ignored; private tab remains blank.
- `LauncherShortcutActivity` private shortcut route -> blocked because the component is not exported.
- normal `ACTION_VIEW` with Chromium incognito extra -> no usable private navigation.
- private-tab launcher followed by normal `ACTION_VIEW` -> URL opens in a new normal tab.
- `chromium.shortcut.action.OPEN_NEW_INCOGNITO_WINDOW` -> no activity found; Vivaldi never opens.
- local APK/Dex string inspection found no safe exported arbitrary-URL private entry point.

Conclusion: never pretend normal `ACTION_VIEW` is private. The supported fallback is deliberately explicit.

### 0.3.1 behavior
Player gear now includes **Vivaldi Private + Copy URL**. It:
1. obtains the stored original webpage URL via the same permanent origin path used by Favorites;
2. copies the original URL to clipboard;
3. opens Vivaldi's genuine blank Private tab via the exported `IncognitoTabLauncher` action/component;
4. tells the user that the original URL was copied and should be pasted into the Private tab;
5. never sends the URL through normal `ACTION_VIEW` as part of this action.

Implementation is isolated in `VivaldiPrivateLauncher.kt`; player/resolver/background-preparation ownership and policies were not changed. English and Spanish strings are included.

0.3.1 build:
- Actions run `32552025984`
- build job `96980224294`
- head `6a195bd0019b3335236375650f4123f11f3bc3fc`
- debug build success
- release build success
- `apksigner`: v2=true, one signer
- signer certificate SHA-256 `8c87e1f6a7a4873f12cb25ba348bef665057159f16a65b9059e5e1d7c0b95e7c` — matches permanent certificate
- signed artifact ID `9470356520`
- signed artifact ZIP SHA-256 `c4660868785eb06b52ba274a14e4f0b9e3f34aa7ac452e7374be9c0b55a90606`
- release APK SHA-256 `6e02fb3df1ea831a42d4d4c582a37c46f4b68772f9cd8f438ae821fc9fa0db51`

The user installed 0.3.1 successfully with ADB as an in-place update. The focused device QA for **Vivaldi Private + Copy URL** has been requested but the user has NOT YET reported PASS/FAIL. Do not mark it passed until the result is received.

## Implemented feature set on current main
- permanent original-page URL via `TabOriginStore`, including exact V2 BG share capture
- conservative serialized Update status checks
- Revive expired tabs through `TabRevivalCoordinator` using the protected service/private-display architecture
- Close all tabs confirmation using Recently Closed safety path
- Favorites storing original page URL + title
- Private Favorites with encrypted local storage, Android biometric/device-auth UI gate, `FLAG_SECURE`, Recents exclusion, no thumbnails, relock on leaving
- Favorites launch fresh tabs through protected service/private-display preparation
- Settings exposes Favorites + Private Favorites
- player gear Add to Favorites + Add to Private Favorites
- player gear Vivaldi Private + Copy URL fallback
- bilingual English/Spanish strings

Private Favorites caveat: the encrypted blob uses Android Keystore AES-GCM, but the key itself is not currently configured as a hardware/user-auth-bound key. The UI requires system authentication before decrypt/render; do not overclaim stronger cryptographic binding.

## Remaining / next work
1. Receive focused device QA result for **Vivaldi Private + Copy URL**.
2. `Revive expired` still needs a real naturally expired-tab device test when available.
3. Conservative diagnostics / operations-log cleanup only; do not alter resolver ranking, playback policy, #234 architecture, or one-player ownership.
4. Inspect the Recently Closed retention limit before relying on Close All for very large tab sets; prior code inspection suggested a small limit around 12, while the app has demonstrated 31-tab use.
5. Refresh PROJECT_STATE/CHAT_BOOTSTRAP after any new QA result or release change.

## Stored-for-later backlog
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials in the APK
- safe dead/historical code-path cleanup only after proving paths unused
- old dedicated “Return to existing Vivaldi task/tab” idea: reconsider later, do not automatically implement
- broader failure/stress hardening only when real personal-use regressions justify it

## QA request format
Whenever explicitly asking the user to test an APK, use exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.

Do not add extra code blocks to that QA request.
