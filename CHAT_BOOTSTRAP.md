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

## Candidate 3 device result
ADB in-place update PASS; existing data retained.

Failure 1: player recovery UI still fully failed. For the entire failed-player session the user still saw:
- `Playback failed. Tap Playback error to view or copy the technical details.`
- tapping **Recovery options** showed the same `Recovery options` title + explanatory text
- only `CANCEL` visible/actionable
- no Retry playback
- no Refresh source / Revive

Failure 2: new Revive All + foreground playback blink. Device report:
**Revive All running + watching another video during revival = repeated blinking / effectively unwatchable video.**

## Candidate 4 code checkpoint — START HERE
Latest app-code head: `9fdc16f2da7191b23c8043add636c4a5a3ad6cd4`.

Implemented after Candidate 3:
- `e2bbcd7ae95aa817a18a0238329aeb20d29c34e2` — direct failed-player overlay buttons for **Retry playback**, **Refresh source**, and **Recovery options**. This makes the recovery actions visible outside any dialog row rendering path. Refresh still calls `TabMaintenanceController.reviveFromPlayer(...)`.
- `03f859f068ec38d097e88a211ea7425e3ed14414` — process-local `ForegroundPlaybackState` signal; exposes only whether PlayerActivity is foreground, not media/player details.
- `9fdc16f2da7191b23c8043add636c4a5a3ad6cd4` — queued Revive All work defers starting additional private-display revival sessions while a PlayerActivity is foreground. Protected service/private-display architecture unchanged.
- `7b19a251c60f6ea4a0b3f657e6ba8d97e09c569e` — `INSTALLER_LOG_CAPTURE.md` with logcat steps for PackageInstaller / Play Protect tap-install failure reasons.

Build status at handoff: Actions run `32654832188` / run #356 was in progress for code head `9fdc16f2da7191b23c8043add636c4a5a3ad6cd4`. Do not issue QA until CI completes and signed artifact is known.

## Already device-PASS in 0.3.2
- ADB update/data retention
- consolidated gear menu / proper close icon
- dashboard individual Revive
- Check Status -> Player race/blink fix
- decoder fallback case
- Recently Closed / Close All with 25 tabs; cap 100
- privacy appearance/auth/reveal/deferred share
- general regression spot-check

## Direct installer
Normal tap APK update remains blocked by Play Protect / installer flow. ADB in-place update is valid for QA. Do not uninstall the working app merely to test. Use `INSTALLER_LOG_CAPTURE.md` to collect actual reason codes before trying app-side package changes.

## Merge gate
Do not merge PR #2 until BOTH are device-PASS:
1. failed-player recovery exposes working Retry and Refresh/Revive;
2. Revive All can run while another video plays with no blink/disturbance.

Report-log-on-GitHub remains postponed. Return-to-Vivaldi unchanged.

## QA format
When explicitly asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
