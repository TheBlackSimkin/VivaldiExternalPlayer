# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
Released `main` remains authoritative; active 0.3.2 QA work is on `work/0.3.2-correctness-ux`, PR #2. Read `PROJECT_STATE.md` and this file before substantive work.

## Safety / architecture
Android UI remains bilingual English/Spanish; source comments English. PH/HH are technical playback targets only. Never inspect or describe media content or thumbnails. Never bypass DRM/paywalls/auth/geo/CAPTCHA, import browser credentials, add background playback, or add a second ExoPlayer.

Protected Build #234 preparation path:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.

No preparation Activity on display 0. No PlayerActivity/Media3/ExoPlayer during BG preparation. Preserve one ExoPlayer and existing quality/resolver policy. Build #278 is accepted player/UI baseline; Build #249 palette remains protected.

## Released baseline: 0.3.1 / versionCode 4
0.3.1 **Vivaldi Private + Copy URL** device QA is PASS. ADB in-place install works. APK SHA-256 `6e02fb3df1ea831a42d4d4c582a37c46f4b68772f9cd8f438ae821fc9fa0db51`.

Permanent signer certificate SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

## Active candidate: 0.3.2 / versionCode 5
Branch `work/0.3.2-correctness-ux`, PR #2. Do NOT merge until device QA.

Final code gate: Actions `32590746439` / job `97074080536`, signed artifact `9480279353`, APK SHA-256 `bc5b854980faa214ee0b9d7ef5a7676923ffc2c95e3cac4029e47f79a3f77799`. Debug/release build, zipalign/package sanity and signing continuity all PASS.

Implemented: centralized single/bulk Revive, individual stale-tab Revive, status/player lifecycle isolation, serialized/suspended thumbnail FrameExtractor work during playback, same-ExoPlayer Media3 decoder fallback, decoder-specific graceful recovery, Recently Closed 100-entry cap, consolidated gear menu, proper close icon, manual Hide & lock app + authenticated Reveal ExternalPlayer, deferred shares while hidden, shared SystemAuthGate, PR CI validation.

Reported decoder case: `ERROR_CODE_DECODER_INIT_FAILED`, Qualcomm `c2.qti.avc.decoder`, `NO_EXCEEDS_CAPABILITIES`, implausible ~12857 fps metadata. 0.3.2 uses documented decoder fallback only; never rewrite/forge stream metadata without proof.

## Direct APK installation result — IMPORTANT
Standalone extracted 0.3.2 APK normal file-tap update was tested and **FAILED**. Device first reports `La aplicación no se ha instalado`; then Google Play Protect shows `Aplicación bloqueada para proteger tu dispositivo` and says it does not know another app from this developer / it may be unsafe. User tapped visible `Instalar de todas formas`, but nothing further happened.

This is now a reproducible Play Protect/install-flow blocker, not evidence of a signing/version mismatch. CI proves signing/alignment/package sanity and prior ADB `install -r` proves package/signature compatibility. Next install investigation should capture PackageInstaller/PackageManager/Play Protect reason/logs from the failed tap flow. Do NOT uninstall the working app just to test. Functional 0.3.2 QA can continue via known-good ADB in-place update.

## Owed 0.3.2 QA
- direct standalone tap install: **FAIL — Play Protect block**
- individual stale-tab Revive: NOT TESTED
- Check status -> open Player midway: NOT TESTED
- decoder case: NOT TESTED
- Close All / Recently Closed >12: NOT TESTED
- gear-menu UX + close icon: NOT TESTED
- Hide & lock / Reveal + deferred share: NOT TESTED
- player regression: NOT TESTED

## QA format
Whenever explicitly asking the user to test an APK, provide exactly one detailed code block with steps/EXPECTED/RESULT, then one separate compact-answer code block. No extra code blocks.
