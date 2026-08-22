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
Branch `work/0.3.2-correctness-ux`, PR #2. **Do NOT merge yet.** One functional bug remains in player-side recovery and direct tap installation is still unresolved.

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
- Media3 same-ExoPlayer decoder fallback enabled; reported decoder-init case now device PASS; no stream metadata forging
- Recently Closed browser-like history cap raised 12 -> 100; Close All with 25 tabs device PASS
- dashboard operations consolidated under gear menu; close text X replaced with proper icon
- `SystemAuthGate` shared by Private Favorites and app privacy UI
- app privacy is manually activated, starts unlocked, uses a neutral covered surface, `FLAG_SECURE`, biometric/device credential, in-place reveal, and deferred incoming shares
- PR CI validation, package/alignment/signing checks

## Candidate 1 device QA summary
- ADB update over 0.3.1: PASS; existing data retained
- gear menu / close icon: PASS
- dashboard individual Revive: PASS
- in-player Revive/Refresh: FAIL
- Check status -> Player: PASS
- decoder case: PASS
- Recently Closed / Close All with 25 tabs: PASS
- privacy: SEMI-PASS; function worked but old curtain advertised locking and reveal minimized app
- protected player regression: PASS
- direct tap install: FAIL; Play Protect block

## Candidate 2 device QA — definitive state
User installed Candidate 2 by ADB and reported:
- ADB update: **PASS**
- privacy appearance: **PASS**; neutral/inconspicuous presentation accepted
- privacy authentication/reveal: **PASS**; no minimize problem
- share while covered: **PASS**; deferred correctly until reveal
- short regression check: **PASS**
- in-player Revive/Refresh: **FAIL**

### Exact remaining in-player recovery failure
When playback is failed, the Player shows:
`Playback failed. Tap Playback error to view or copy the technical details.`

Tapping **Recovery options** opens an unattractive dialog containing only:
- title: `Recovery options`
- text: `Playback failed. These recovery actions retry normal playback paths only; they do not bypass protected access.`
- `CANCEL`

There are **no actionable recovery entries** such as Retry playback, Refresh source/Revive, alternate detected video, or browser method. Therefore `TabMaintenanceController.reviveFromPlayer(...)` is not being reached from this failed-player state despite the underlying centralized revival path existing and dashboard Revive already passing.

### Next-session first task
Inspect why `PlayerRecoveryController.showRecoveryDialog()` builds an empty `actions` list in the actual failed Player context. The code currently always intends to add Retry first, then conditionally Refresh/alternate actions, so an empty dialog suggests the recovery controller is attaching to a state/player instance where expected action construction or player/tab association is not valid, or the displayed dialog is coming from another/older recovery surface. Trace the actual runtime path before adding more logic.

Specifically verify:
1. which class/dialog instance produces the observed exact text;
2. whether `PlayerRecoveryController.attach()` is attached to the active `PlayerView.player` after failure;
3. whether `currentPersistentTab()` sees `TabbedPlayerApplication.EXTRA_TAB_ID` in this launch path;
4. whether the failed player was opened from a persistent dashboard tab or another launch path lacking tab ID;
5. whether another recovery dialog implementation exists and is being shown instead;
6. once identified, make player-side **Refresh source / Revive** call the same authoritative `TabMaintenanceController` path that already passes from the dashboard.

Do not reintroduce a parallel service/preparation implementation.

## Decoder case
Reported HLS playback previously failed with `ERROR_CODE_DECODER_INIT_FAILED`, Qualcomm `c2.qti.avc.decoder`, `NO_EXCEEDS_CAPABILITIES`, and implausible ~12857 fps metadata. 0.3.2 same-ExoPlayer decoder fallback device retest is **PASS**. Do not rewrite/forge stream metadata without reproducible proof.

## Direct APK installation — confirmed unresolved blocker
Standalone extracted APK normal tap update is **FAIL**. Device reports `La aplicación no se ha instalado`; Google Play Protect then shows `Aplicación bloqueada para proteger tu dispositivo`, saying it does not know another app from this developer / it may be unsafe. Tapping visible `Instalar de todas formas` does not continue.

CI proves package/sign/alignment sanity and ADB in-place update proves package/signature continuity. Treat this as a Play Protect / installer-flow blocker, not a signing failure. Future investigation should capture PackageInstaller/PackageManager/Play Protect logs/reason codes during a failed tap. Do not uninstall the working app merely to test.

## Merge/release gate
Do not merge PR #2 until:
1. in-player recovery exposes a working Refresh/Revive action and device QA passes;
2. state files are refreshed with that final result.

Direct tap installation remains a separate known blocker; ADB in-place functional QA is valid meanwhile.

## Deferred
- Report log on GitHub shortcut postponed; never embed reusable GitHub credentials.
- Return-to-Vivaldi stays unchanged.
- Continue disciplined cleanup/refactoring; delete historical paths only after proving unused.

## QA request format
Whenever explicitly asking the user to test an APK, provide exactly one detailed code block with steps/EXPECTED/RESULT, then one separate compact-answer code block. No extra code blocks.
