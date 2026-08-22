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
Branch `work/0.3.2-correctness-ux`, PR #2. Do NOT merge until the second focused QA pass.

First candidate code gate: Actions `32590746439` / run #331, job `97074080536`, artifact `9480279353`, APK SHA-256 `bc5b854980faa214ee0b9d7ef5a7676923ffc2c95e3cac4029e47f79a3f77799`. Debug/release, zipalign/package and signer checks PASS.

Second candidate code head: `ac44109d97fe115310c7c31ed2c7d6418d77b1a1`.
- Actions run `32595557947` / run #342
- job `97085776140`
- signed release artifact `9481470902`
- artifact ZIP SHA-256 `b64f8842f5412f36ce6d314331e7df9edc7d760486c479ac0bdd4528d98029de`
- release APK SHA-256 `e756e65670f06e7c2be1e4aa58022fed5c71696c4df3178e051196b34a50c01c`
- debug + signed release build PASS
- upload/signing/package/alignment validation PASS

### First-candidate device QA
- ADB install over 0.3.1: **PASS**; existing data remained.
- gear menu / close icon: **PASS**.
- individual Revive from dashboard: **PASS**.
- Revive/Refresh source from inside Player: **FAIL**; reproduced old behavior/error.
- Check status -> open Player midway: **PASS**; prior blink/race no longer reproduced.
- reported decoder-init case: **PASS**.
- Recently Closed / Close All with 25 tabs: **PASS**.
- privacy feature: **SEMI-PASS**; auth/cover worked, but the curtain conspicuously announced that content was locked and successful reveal minimized the app.
- protected player regression: **PASS**.

### Second-candidate fixes now implemented
- In-player Refresh source no longer has a parallel preparation implementation. `TabMaintenanceController.reviveFromPlayer(...)` now persists playback position/state and queues the same persistent tab through the exact protected revival path already proven from the dashboard. Explicit player recovery may revive even when a health probe has not yet marked the tab stale.
- The stale `PlayerActivity.currentResolved` payload is still cleared before navigation so its onPause persistence cannot race the tab back to READY with the dead payload.
- Privacy curtain presentation is now intentionally neutral: `External Video Player` / `Ready to open a video` / `Open`. It does not display words such as locked, hidden, private, tabs, history, or reveal on the covered surface.
- Privacy authentication now reveals **in place**. The Activity is no longer restarted/finished after successful auth, addressing the observed minimize behavior.
- Deferred shares remain blocked while hidden and are consumed only after successful in-place reveal; the latest reveal callback is retained even when the curtain was already on screen.

### Existing 0.3.2 changes retained
- centralized stale/error-tab recovery and global Revive
- status/player lifecycle isolation
- no PlayerActivity-triggered dashboard thumbnail warm-up; background FrameExtractor work serialized/suspended during playback
- Media3 same-ExoPlayer decoder fallback; decoder-specific graceful recovery; no metadata forging
- Recently Closed cap 100 and browser-like Close All recovery
- consolidated dashboard gear menu
- shared `SystemAuthGate` for Private Favorites/privacy UI
- proper close icon
- PR CI validation

## Decoder case
Reported HLS playback failed on first candidate input with `ERROR_CODE_DECODER_INIT_FAILED`, Qualcomm `c2.qti.avc.decoder`, `NO_EXCEEDS_CAPABILITIES`, and implausible ~12857 fps metadata. First-candidate device retest is **PASS** with documented same-ExoPlayer decoder fallback. Do not rewrite/forge stream metadata without reproducible proof.

## Direct APK installation — confirmed blocker
Standalone extracted APK normal tap update is **FAIL**. Device reports `La aplicación no se ha instalado`; Google Play Protect then shows `Aplicación bloqueada para proteger tu dispositivo`, saying it does not know another app from this developer / it may be unsafe. Tapping visible `Instalar de todas formas` does not continue.

CI proves package/sign/alignment sanity and ADB in-place update proves package/signature continuity. Treat this as a Play Protect / installer-flow blocker, not a signing failure. Next investigation should capture PackageInstaller/PackageManager/Play Protect logs/reason codes during a failed tap. Do not uninstall the working app merely to test.

## Owed second-candidate QA
Only the changed areas need focused retest before merge:
- in-player Refresh/Revive: must queue same tab and recover like dashboard Revive
- neutral privacy curtain: must be inconspicuous at a glance
- successful auth must reveal in place without minimizing
- share received while hidden must remain deferred until reveal
- short player/dashboard regression spot-check

Direct tap installation remains separately unresolved; functional QA may use ADB in-place update.

## Deferred
- Report log on GitHub shortcut postponed; never embed reusable GitHub credentials.
- Return-to-Vivaldi stays unchanged.
- Continue disciplined cleanup/refactoring; delete historical paths only after proving unused.

## QA request format
Whenever explicitly asking the user to test an APK, provide exactly one detailed code block with steps/EXPECTED/RESULT, then one separate compact-answer code block. No extra code blocks.
