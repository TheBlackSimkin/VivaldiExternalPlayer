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
Branch `work/0.3.2-correctness-ux`, PR #2. **DO NOT MERGE YET.**

Candidate 2 APK code head `ac44109d97fe115310c7c31ed2c7d6418d77b1a1`:
- Actions `32595557947` / run #342
- job `97085776140`
- signed artifact `9481470902`
- artifact ZIP SHA-256 `b64f8842f5412f36ce6d314331e7df9edc7d760486c479ac0bdd4528d98029de`
- release APK SHA-256 `e756e65670f06e7c2be1e4aa58022fed5c71696c4df3178e051196b34a50c01c`
- build/package/alignment/signing PASS

## 0.3.2 features already device-PASS
- ADB in-place update; existing data retained
- consolidated gear menu and proper close icon
- dashboard individual Revive
- Check status -> Player race/blinking fix
- reported decoder-init case with same-ExoPlayer fallback
- Recently Closed / Close All tested with 25 tabs; history cap 100
- neutral/inconspicuous privacy screen
- privacy authentication reveals in place; no minimize
- share while covered stays deferred until auth
- general player/dashboard regression spot-check

Implementation/refactor retained: `TabMaintenanceController` central revival policy, `SystemAuthGate` shared auth, `AppPrivacyController`, `DashboardMenu`, thumbnail decoder contention isolation, PR CI checks.

## Candidate 3 code checkpoint — START HERE
Candidate 2 in-player Revive/Refresh failed because **Recovery options** showed the explanation and only `CANCEL`.

Root cause is now identified: `PlayerRecoveryController.showRecoveryDialog()` did construct actions (Retry was unconditional), but Android `AlertDialog` was configured with both `.setMessage(...)` and `.setItems(...)`. In that layout path the message content remains visible while the item list is not inserted, producing exactly the observed dialog.

Fix commit: `f23660479a0177b30ddeb16d030095058d79bfda` (`fix: render player recovery actions`).

The fix changes only dialog presentation:
- explanation + action rows now share one explicit custom vertical dialog body;
- actions are visible buttons;
- Retry playback behavior is unchanged;
- Refresh source still calls `TabMaintenanceController.reviveFromPlayer(...)` and therefore the same `TabMaintenanceController -> TabRevivalCoordinator -> protected service/private-display` path that dashboard Revive already passes;
- no second player, resolver-policy change, parallel preparation path, or architecture change.

`PROJECT_STATE.md` was refreshed after this finding. Current documentation commit after that refresh: `9a7919577d18dc144a4a7e615d125c28a73eac7b`.

## Next required work
1. Record CI/build metadata for the latest branch head containing the Candidate 3 recovery-dialog fix.
2. Install the resulting signed 0.3.2 APK by ADB in-place.
3. Focused device QA: failed Player -> Recovery options must visibly show Retry; a persistent tab must also show Refresh source; Refresh source must queue the same tab through the protected centralized revival path; short regression check.
4. If PASS, refresh both state files and only then consider merging PR #2.

## Direct installer — separate unresolved issue
Normal standalone APK tap update FAILS. Android reports `La aplicación no se ha instalado`, then Google Play Protect shows `Aplicación bloqueada para proteger tu dispositivo`; tapping `Instalar de todas formas` does not continue.

CI and successful ADB in-place update prove package/sign/alignment/signature continuity. Treat as Play Protect / installer-flow blocker, NOT a signing failure. Future investigation: capture PackageInstaller/PackageManager/Play Protect logs/reason codes during failed tap. Do not uninstall the working app merely to test.

Report log on GitHub remains postponed. Return-to-Vivaldi unchanged. Continue disciplined cleanup only; remove old paths only when proven unused.

## QA format
When explicitly asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
