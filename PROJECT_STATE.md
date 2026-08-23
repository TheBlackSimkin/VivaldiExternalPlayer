# Vivaldi External Player — Project State

Released `main` remains authoritative. Active 0.3.2 work is on `work/0.3.2-correctness-ux`, PR #2. Read this file and `CHAT_BOOTSTRAP.md` before substantive work.

## Safety / protected architecture
- Android UI bilingual English/Spanish; source comments English.
- PH/HH are technical playback targets only: URLs, manifests, codecs, resolutions, request metadata, resolver/candidate ranking, playback state/errors, local titles. Never inspect/describe media content or thumbnail imagery.
- Never bypass DRM/paywalls/auth/geo/CAPTCHA, import browser credentials, add background playback, or add a second ExoPlayer.

Protected Build #234 preparation path:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.

Preserve one ExoPlayer and existing resolver/quality policy. Build #278 is accepted player/UI baseline; Build #249 palette remains protected.

## Permanent signing
Permanent signer certificate SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.
CI verifies signing, package metadata and APK alignment.

## Released baseline: 0.3.1 / versionCode 4
0.3.1 **Vivaldi Private + Copy URL** device QA is PASS. ADB in-place update works. APK SHA-256 `6e02fb3df1ea831a42d4d4c582a37c46f4b68772f9cd8f438ae821fc9fa0db51`.

## Active candidate: 0.3.2 / versionCode 5
Branch `work/0.3.2-correctness-ux`, PR #2. **Do NOT merge yet.**

### Candidate 2 build
Code head used for Candidate 2 APK: `ac44109d97fe115310c7c31ed2c7d6418d77b1a1`.
- Actions run `32595557947` / run #342
- job `97085776140`
- signed release artifact `9481470902`
- artifact ZIP SHA-256 `b64f8842f5412f36ce6d314331e7df9edc7d760486c479ac0bdd4528d98029de`
- release APK SHA-256 `e756e65670f06e7c2be1e4aa58022fed5c71696c4df3178e051196b34a50c01c`
- debug + signed release build PASS
- upload/signing/package/alignment validation PASS

## 0.3.2 implementation retained
- stale/error-tab recovery centralized in `TabMaintenanceController`
- dashboard individual Revive and global Revive All use the same controller/coordinator path
- status/player lifecycle isolation fixed the prior Check Status -> Player blinking race
- PlayerActivity no longer triggers dashboard thumbnail warm-up; background `FrameExtractor` work is serialized/suspended during foreground playback
- Media3 same-ExoPlayer decoder fallback enabled; reported decoder-init case device PASS; no stream metadata forging
- Recently Closed browser-like history cap raised 12 -> 100; Close All with 25 tabs device PASS
- dashboard operations consolidated under gear menu; close text X replaced with proper icon
- `SystemAuthGate` shared by Private Favorites and app privacy UI
- app privacy is manually activated, starts unlocked, uses a neutral covered surface, `FLAG_SECURE`, biometric/device credential, in-place reveal, and deferred incoming shares
- PR CI validation, package/alignment/signing checks

## Candidate 2 device QA — definitive state
- ADB update: **PASS**
- existing data retained: **PASS**
- gear menu / close icon: **PASS**
- dashboard individual Revive: **PASS**
- Check status -> Player race: **PASS**
- decoder case: **PASS**
- Recently Closed / Close All with 25 tabs: **PASS**
- privacy appearance: **PASS**
- privacy authentication/reveal: **PASS**
- share while covered: **PASS**
- short regression check: **PASS**
- in-player Revive/Refresh: **FAIL**
- direct tap install: **FAIL**; Play Protect / installer-flow blocker

## Candidate 3 code checkpoint — recovery dialog fix
Current code head after the recovery-dialog fix: `f23660479a0177b30ddeb16d030095058d79bfda` (`fix: render player recovery actions`). This is a code checkpoint only; CI/device QA has not yet been recorded.

### Root cause found
Candidate 2 did **not** build an empty recovery action list. `PlayerRecoveryController.showRecoveryDialog()` always added **Retry playback** and conditionally added Refresh/alternate actions.

The observed dialog showed the explanation text and only `CANCEL` because Android `AlertDialog` was configured with both `.setMessage(...)` and `.setItems(...)`. In this layout path the message scroll container remains active and the list rows are not inserted into the visible dialog. That exactly explains the device result without requiring a second recovery implementation or missing player attachment.

### Candidate 3 fix
`PlayerRecoveryProvider.kt` now renders the explanation and action rows inside one explicit vertical custom dialog body. Each recovery action is a visible button. The underlying action construction and recovery behavior are unchanged:
- Retry playback still prepares the same current ExoPlayer/source.
- Refresh source still calls `TabMaintenanceController.reviveFromPlayer(...)`.
- The centralized path remains `TabMaintenanceController -> TabRevivalCoordinator -> protected service/private-display`.
- No parallel preparation implementation, second player, resolver policy change, or architecture change was introduced.

### Next required step
Wait for CI/build metadata for `f23660479a0177b30ddeb16d030095058d79bfda`, then install the resulting signed 0.3.2 APK by ADB in-place and perform a focused player-recovery device QA. The minimum acceptance is:
1. failed Player -> Recovery options visibly shows Retry playback;
2. persistent-tab failure also visibly shows Refresh source;
3. Refresh source queues the same tab through the authoritative revive path and returns to dashboard/preparation normally;
4. player/dashboard regression remains clean.

Do not merge PR #2 until this is device PASS and both state files are refreshed with final build IDs/results.

## Decoder case
Reported HLS playback previously failed with `ERROR_CODE_DECODER_INIT_FAILED`, Qualcomm `c2.qti.avc.decoder`, `NO_EXCEEDS_CAPABILITIES`, and implausible ~12857 fps metadata. 0.3.2 same-ExoPlayer decoder fallback device retest is **PASS**. Do not rewrite/forge stream metadata without reproducible proof.

## Direct APK installation — unresolved separate blocker
Standalone extracted APK normal tap update is **FAIL**. Device reports `La aplicación no se ha instalado`; Google Play Protect then shows `Aplicación bloqueada para proteger tu dispositivo`. Tapping visible `Instalar de todas formas` does not continue.

CI proves package/sign/alignment sanity and ADB in-place update proves package/signature continuity. Treat this as a Play Protect / installer-flow blocker, not a signing failure. Future investigation should capture PackageInstaller/PackageManager/Play Protect logs/reason codes during a failed tap. Do not uninstall the working app merely to test.

## Deferred
- Report log on GitHub shortcut postponed; never embed reusable GitHub credentials.
- Return-to-Vivaldi stays unchanged.
- Continue disciplined cleanup/refactoring; delete historical paths only after proving unused.

## QA request format
Whenever explicitly asking the user to test an APK, provide exactly one detailed code block with steps/EXPECTED/RESULT, then one separate compact-answer code block. No extra code blocks.
