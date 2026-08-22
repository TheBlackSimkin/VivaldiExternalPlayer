# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
Released `main` remains authoritative. Active 0.3.2 work is on `work/0.3.2-correctness-ux`, PR #2. Read `PROJECT_STATE.md` and this file before substantive work.

## Protected rules
Android UI bilingual English/Spanish; comments English. PH/HH are technical playback targets only; never inspect/describe media content or thumbnails. Never bypass DRM/paywalls/auth/geo/CAPTCHA, import browser credentials, add background playback, or add a second ExoPlayer.

Protected Build #234 preparation:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.

Preserve one ExoPlayer and current resolver/quality policy. Build #278 is accepted player/UI baseline; Build #249 palette protected.

## Baseline
0.3.1 / versionCode 4 Vivaldi Private + Copy URL device QA PASS. Permanent signer SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

## Active candidate: 0.3.2 / versionCode 5
Branch `work/0.3.2-correctness-ux`, PR #2. Do NOT merge until second focused QA.

First candidate code gate: Actions `32590746439` / run #331, job `97074080536`, artifact `9480279353`, APK SHA-256 `bc5b854980faa214ee0b9d7ef5a7676923ffc2c95e3cac4029e47f79a3f77799`; build/package/alignment/signing PASS.

Second candidate code head `ac44109d97fe115310c7c31ed2c7d6418d77b1a1`:
- Actions `32595557947` / run #342
- job `97085776140`
- signed artifact `9481470902`
- artifact ZIP SHA-256 `b64f8842f5412f36ce6d314331e7df9edc7d760486c479ac0bdd4528d98029de`
- release APK SHA-256 `e756e65670f06e7c2be1e4aa58022fed5c71696c4df3178e051196b34a50c01c`
- debug/release build and upload/sign/package/alignment checks PASS

First-candidate device results:
- ADB update PASS; existing data retained
- menu/close icon PASS
- dashboard individual Revive PASS
- in-player Revive/Refresh FAIL, old behavior reproduced
- Check status -> Player PASS
- decoder case PASS
- Recently Closed with 25 tabs PASS
- privacy SEMI-PASS: cover/auth worked but curtain advertised locking and reveal minimized app
- player regression PASS
- direct tap install FAIL: Play Protect block; `Install anyway` did not continue

Second candidate changes:
- player Refresh now calls `TabMaintenanceController.reviveFromPlayer`, the same protected persistent-tab revival path as dashboard Revive; no parallel service sequence
- position/play state persisted; stale PlayerActivity payload cleared before pause persistence can restore it
- neutral covered surface: `External Video Player` / `Ready to open a video` / `Open`; no locked/hidden/private wording
- successful auth reveals in place, no Activity restart/finish
- latest deferred share callback retained while curtain exists and consumed only after reveal

Retained 0.3.2 features: centralized revive, status/player lifecycle isolation, thumbnail codec-contention fix, same-ExoPlayer decoder fallback, Recently Closed 100, consolidated gear menu, shared SystemAuthGate, proper close icon, PR CI.

## Direct installer
Standalone tap install remains a separate reproducible Play Protect/install-flow FAIL. CI and ADB prove package/sign/alignment/signature continuity. Do not call it a signing failure and do not uninstall the working app. Future installer work should capture PackageInstaller/PackageManager/Play Protect reason logs during a failed tap.

## Owed second-candidate QA
- in-player Refresh/Revive
- neutral/inconspicuous privacy presentation
- auth reveal must remain in app, not minimize
- share while hidden must remain deferred until reveal
- short regression spot-check

Report log on GitHub remains postponed. Return-to-Vivaldi unchanged. Continue disciplined cleanup only; remove old paths only when proven unused.

## QA format
When explicitly asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
