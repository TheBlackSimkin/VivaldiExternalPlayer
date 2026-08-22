# Vivaldi External Player — Project State

GitHub `main` is authoritative for released code. Active 0.3.2 QA work is on `work/0.3.2-correctness-ux`, PR #2. Read this file and `CHAT_BOOTSTRAP.md` before substantive work.

## Safety / protected architecture
- Android UI remains bilingual English/Spanish; source comments English.
- PH/HH are technical playback targets only: URLs, manifests, codecs, resolutions, request metadata, resolver/candidate ranking, playback states/errors, local titles. Never inspect/describe media content or thumbnail imagery.
- Never bypass DRM/paywalls/auth/geo/CAPTCHA or import browser credentials.
- Never add background playback or a second ExoPlayer.

Build #234 background preparation remains protected:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.

No preparation Activity on display 0. No PlayerActivity/Media3/ExoPlayer during BG preparation.

Preserve one ExoPlayer, yt-dlp first/browser fallback, automatic/manual quality, video+audio, adaptive/sibling switching, gestures, seek preview, rotation, candidate limits/order and no imagery-based ranking.
Automatic quality: exact 720p -> 1080p -> highest below 1080p -> >1080p rare fallback.

Build #278 remains accepted player/UI DEVICE PASS baseline. Build #249 palette remains protected.

## Permanent signing
Permanent signing is established. No permanent key material is stored in the repository.
Certificate SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.
CI verifies signing; 0.3.2 also checks zip alignment and package metadata.

## Released baseline: 0.3.1 / versionCode 4
0.3.1 implements **Vivaldi Private + Copy URL**. It was installed successfully by ADB in-place update and its focused device QA is PASS.

0.3.1 build: head `6a195bd0019b3335236375650f4123f11f3bc3fc`, Actions `32552025984`, job `96980224294`, artifact `9470356520`, APK SHA-256 `6e02fb3df1ea831a42d4d4c582a37c46f4b68772f9cd8f438ae821fc9fa0db51`.

## Active candidate: 0.3.2 / versionCode 5
Branch `work/0.3.2-correctness-ux`, PR #2. Do NOT merge until device QA.

Final code gate:
- Actions run `32590746439` / run #331
- job `97074080536`
- signed artifact `9480279353`
- APK SHA-256 `bc5b854980faa214ee0b9d7ef5a7676923ffc2c95e3cac4029e47f79a3f77799`
- debug + signed release PASS
- zipalign/package sanity PASS
- signing verification PASS with permanent signer continuity

### Implemented in 0.3.2
- single/bulk stale-tab recovery centralized in `TabMaintenanceController`
- READY + `NEEDS_REFRESH` and ERROR tabs expose the same individual Revive path
- MainActivity suppresses status redraw/thumbnail callbacks while not RESUMED
- PlayerActivity no longer triggers dashboard thumbnail warm-up; background `FrameExtractor` work is serialized and suspended/cancelled during foreground playback
- Media3 same-ExoPlayer decoder fallback enabled with `DefaultRenderersFactory.setEnableDecoderFallback(true)`
- decoder-init-specific recovery after fallback exhaustion; no stream metadata rewriting
- Recently Closed cap raised 12 -> 100; Close All archives through the same history
- global dashboard operations moved into gear sections Tabs / Library / Privacy / App
- manually activated **Hide & lock app**, app starts unlocked, privacy curtain + `FLAG_SECURE`, **Reveal ExternalPlayer** requires biometric/device credential
- incoming share while hidden is deferred until successful reveal
- shared `SystemAuthGate` serves both Private Favorites and privacy curtain
- tab-card text X replaced with a proper close icon
- CI runs on PRs and validates alignment/package/signing

Private Favorites caveat: local AES-GCM storage remains encrypted, but its Keystore key is not currently hardware/user-auth-bound. Do not overclaim.

### Decoder case
Reported PH HLS playback reached Media3 but failed with `ERROR_CODE_DECODER_INIT_FAILED`, Qualcomm `c2.qti.avc.decoder`, `format_supported=NO_EXCEEDS_CAPABILITIES`, 1280x720 AVC and implausible reported frame rate ~12857.142 fps.

0.3.2 first lets Media3 try another compatible decoder inside the SAME ExoPlayer. If all fail, safe recovery remains. Do not forge codec/frame-rate metadata or rewrite manifests without reproducible proof.

### Direct APK install — confirmed blocker
Historical normal file-tap installation showed generic `App not installed` while `adb install -r` worked.

0.3.2 was tested as a standalone extracted APK (not from inside a GitHub artifact ZIP). Device result: **FAIL**. The installer reports `La aplicación no se ha instalado`, then Google Play Protect shows **`Aplicación bloqueada para proteger tu dispositivo`** and states that Play Protect does not know any other app from this developer / it may be unsafe. The visible **`Instalar de todas formas`** action was tapped but nothing further happened.

Interpretation: direct-tap installation is now reproducibly blocked in the Play Protect/install flow. CI already proves package metadata, alignment, APK signing and signer continuity, and prior ADB in-place updates prove package/signature compatibility. Do NOT call this a signing failure. The remaining investigation should capture PackageInstaller/PackageManager/Play Protect logs or reason codes during the failed tap flow. Do not uninstall the working signed app just to test.

For 0.3.2 functional QA, use the known-good ADB in-place update path unless a safer direct-tap workaround is proven.

## Owed 0.3.2 device QA
- direct standalone APK tap update: **FAIL — Play Protect block; Install anyway produced no continuation**
- individual stale-tab Revive: NOT TESTED
- Check status -> open Player midway: NOT TESTED
- decoder-failure source: NOT TESTED
- Close All / Recently Closed >12: NOT TESTED
- gear-menu UX + close icon: NOT TESTED
- Hide & lock / Reveal auth + deferred share: NOT TESTED
- protected player regression spot-check: NOT TESTED

Do not mark remaining items PASS until user reports them.

## Deferred
- Report log on GitHub shortcut postponed; never embed reusable GitHub credentials.
- Return-to-Vivaldi stays unchanged.
- Continue disciplined cleanup/refactoring; delete historical paths only after proving them unused.

## QA request format
Whenever explicitly asking the user to test an APK, provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
Do not add extra code blocks to that QA request.
