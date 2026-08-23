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

Candidate 3:
- recovery code commit `f23660479a0177b30ddeb16d030095058d79bfda`
- branch/build head `b7e78ecff435ed1c4857ea837a0b57a516fea95b`
- Actions `32649668990` / run #351
- job `97219184742`
- signed artifact `9495855376`
- build/sign/package/alignment PASS

## Candidate 3 device result — START HERE
ADB in-place update PASS; existing data retained.

### Failure 1: player recovery UI still fully failed
Candidate 3 did NOT change the user-visible failed-player recovery behavior. For the entire failed-player session the user still sees:
- `Playback failed. Tap Playback error to view or copy the technical details.`
- tapping **Recovery options** shows the same `Recovery options` title + explanatory text
- only `CANCEL` is visible/actionable
- no Retry playback
- no Refresh source / Revive

Therefore Candidate 3 recovery is a full user-visible FAIL. The custom-button code is present in the branch file, so the earlier `setMessage + setItems` diagnosis is insufficient/wrong for the real device path. Do not keep changing dialog layout by assumption. First prove which runtime class/surface actually creates the visible dialog and whether the signed APK executes `PlayerRecoveryProvider`/`PlayerRecoveryController` at all.

### Failure 2: new Revive All + foreground playback blink
New device regression:
**Revive All running + watching another video during revival = repeated blinking / effectively unwatchable video.**

This resembles the old Check Status + foreground Player blink symptom, which was previously fixed, but now occurs while bulk revival is active.

Relevant code facts:
- `TabMaintenanceController.reviveAll()` -> `TabRevivalCoordinator`
- coordinator serializes revival and calls `BackgroundPreparationKeepAliveService.acquire()`
- protected path should remain service -> app-private virtual display -> Presentation/WebView; no visible prep Activity, no PlayerActivity, no second ExoPlayer
- `ForegroundPlaybackGuardProvider` currently schedules a pause 200 ms after PlayerActivity pause/stop unless the same player resumed

Do not assume the cause. Trace actual lifecycle/display/service events during Revive All while PlayerActivity is foreground, compare with the already-fixed Check Status isolation, and fix the disturbance without weakening protected #234 architecture.

## Already device-PASS in 0.3.2
- ADB update/data retention
- consolidated gear menu / proper close icon
- dashboard individual Revive
- Check Status -> Player race/blink fix
- decoder fallback case
- Recently Closed / Close All with 25 tabs; cap 100
- privacy appearance/auth/reveal/deferred share
- general regression spot-check

## Merge gate
Do not merge PR #2 until BOTH are device-PASS:
1. failed-player Recovery options shows working Retry and Refresh/Revive;
2. Revive All can run while another video plays with no blink/disturbance.
Then refresh both state files with final build metadata/results.

## Direct installer
Normal tap APK update remains blocked by Play Protect / installer flow. ADB in-place update is valid for QA. Do not uninstall the working app merely to test.

Report-log-on-GitHub remains postponed. Return-to-Vivaldi unchanged.

## QA format
When explicitly asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
