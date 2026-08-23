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
Code head `ac44109d97fe115310c7c31ed2c7d6418d77b1a1`.
- Actions `32595557947` / run #342
- job `97085776140`
- signed artifact `9481470902`
- release APK SHA-256 `e756e65670f06e7c2be1e4aa58022fed5c71696c4df3178e051196b34a50c01c`

### Candidate 3 build
Branch head `b7e78ecff435ed1c4857ea837a0b57a516fea95b`.
- recovery-dialog code commit `f23660479a0177b30ddeb16d030095058d79bfda`
- Actions `32649668990` / run #351
- job `97219184742`
- signed artifact `9495855376`
- build/sign/package/alignment PASS

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

## Candidate 3 device QA — definitive result
User installed Candidate 3 by ADB over the existing signed build.
- ADB update: **PASS**
- existing data retained: **PASS**
- in-player recovery UI fix: **FAIL**

Observed behavior is materially unchanged from Candidate 2 for the entire failed-player session:
- Player shows `Playback failed. Tap Playback error to view or copy the technical details.`
- tapping **Recovery options** shows title `Recovery options`
- explanatory text remains visible
- only `CANCEL` is visible/actionable
- no visible Retry playback
- no visible Refresh source / Revive
- therefore Retry and Refresh cannot be executed from the failed Player UI

This disproves the earlier diagnosis that simply replacing `.setMessage(...) + .setItems(...)` with a custom vertical body would solve the real device issue. The branch file does contain the custom-button implementation, yet the installed Candidate 3 still presents the old user-visible behavior. Treat this as evidence that the actual runtime recovery surface/path is not yet understood. Do not keep patching the dialog by assumption.

### Required recovery investigation
Trace the exact runtime source of the visible `Recovery options` surface on-device:
1. prove which class creates the dialog the user sees;
2. prove whether `PlayerRecoveryProvider`/`PlayerRecoveryController` is active in that PlayerActivity instance;
3. prove whether the installed signed APK actually contains and executes the Candidate 3 custom-button code;
4. search for any second/legacy recovery dialog or resource/layout path;
5. only after runtime proof, make player-side Refresh/Revive call the already-authoritative `TabMaintenanceController -> TabRevivalCoordinator -> protected service/private-display` path.

## New Candidate 3 regression: Revive All disturbs foreground playback
User reports a new device bug:
**Revive All running + watching another video during revival = repeated blinking / effectively unwatchable video.**

This matches the user-visible symptom of the previously fixed **Check Status + watching a video** race, but occurs specifically while bulk revival is running.

Important facts:
- `TabMaintenanceController.reviveAll(...)` queues stale/error tabs through `TabRevivalCoordinator`.
- `TabRevivalCoordinator` serializes them and calls `BackgroundPreparationKeepAliveService.acquire(...)` one at a time.
- protected service code is intended to use only app-private virtual display + Presentation/WebView and explicitly not launch PlayerActivity/ExoPlayer.
- `ForegroundPlaybackGuardProvider` currently pauses a PlayerActivity after pause/stop unless that same PlayerActivity has resumed within 200 ms.

Do not assume root cause yet. Compare revive-time lifecycle/display/service behavior against the already-fixed Check Status foreground-isolation behavior and capture the exact lifecycle/runtime disturbance before patching. The correct fix must preserve uninterrupted foreground playback while Revive All runs and must not weaken the protected #234 preparation architecture.

## Direct APK installation — unresolved separate blocker
Standalone extracted APK normal tap update is **FAIL** due Play Protect / installer flow. CI and successful ADB in-place update prove package/sign/alignment/signature continuity. Do not uninstall the working app merely to test.

## Merge/release gate
Do not merge PR #2 until BOTH are device-PASS:
1. failed-player Recovery options exposes working Retry and Refresh/Revive actions;
2. Revive All can run while another video is playing without blinking/disturbing foreground playback.
Then refresh both state files with final build IDs/results.

## Deferred
- Report log on GitHub shortcut postponed; never embed reusable GitHub credentials.
- Return-to-Vivaldi stays unchanged.
- Continue disciplined cleanup/refactoring; delete historical paths only after proving unused.

## QA request format
Whenever explicitly asking the user to test an APK, provide exactly one detailed steps/EXPECTED/RESULT code block, then one separate compact-answer code block. No extra code blocks.
