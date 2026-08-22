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

## ONE REMAINING FUNCTIONAL BUG: player-side recovery
Candidate 2 in-player Revive/Refresh is **FAIL**.

Observed failed Player text:
`Playback failed. Tap Playback error to view or copy the technical details.`

Tapping **Recovery options** opens a dialog with title `Recovery options`, explanatory text about normal recovery paths, and only `CANCEL`. There are **no recovery actions at all** — no Retry playback, Refresh source/Revive, alternate detected video, or browser method.

This means the centralized `TabMaintenanceController.reviveFromPlayer(...)` path is still not being reached from the actual failed-player recovery UI.

### NEXT SESSION: START HERE
Trace the actual runtime recovery dialog before changing architecture.
- Find the exact class/string producing the observed dialog.
- Verify whether `PlayerRecoveryController.showRecoveryDialog()` is really the dialog being shown.
- Verify attachment to active `PlayerView.player` after error.
- Verify `TabbedPlayerApplication.EXTRA_TAB_ID` exists for this failed Player launch and `currentPersistentTab()` resolves the tab.
- Search for any second/legacy `Recovery options` implementation.
- Explain why a dialog that should add Retry unconditionally is rendering with zero action rows.
- Then make player-side Refresh/Revive call the SAME `TabMaintenanceController -> TabRevivalCoordinator -> protected service/private-display` path that dashboard Revive already passes.
- Do not create another preparation implementation.

## Direct installer — separate unresolved issue
Normal standalone APK tap update FAILS. Android reports `La aplicación no se ha instalado`, then Google Play Protect shows `Aplicación bloqueada para proteger tu dispositivo`; tapping `Instalar de todas formas` does not continue.

CI and successful ADB in-place update prove package/sign/alignment/signature continuity. Treat as Play Protect / installer-flow blocker, NOT a signing failure. Future investigation: capture PackageInstaller/PackageManager/Play Protect logs/reason codes during failed tap. Do not uninstall the working app merely to test.

## Merge gate
Do not merge PR #2 until player-side recovery is fixed and device-PASS. Then refresh both state files.

Report log on GitHub remains postponed. Return-to-Vivaldi unchanged. Continue disciplined cleanup only; remove old paths only when proven unused.

## QA format
When explicitly asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
