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

## Candidate 4 — START HERE
Build/app-code head: `9fdc16f2da7191b23c8043add636c4a5a3ad6cd4`.
- Actions `32654832188` / run #356
- job `97231847880`
- signed release artifact `9497197410`
- signed artifact digest `sha256:5207ba499b909f0ae506cfc38c600c31c92d77f31450cff7f55731d6e883b7cb`
- build/sign/package/alignment PASS

Candidate 4 device QA result:
- install/data: **PASS**
- Recovery UI visibility: **PASS**
- Retry playback from failed-player overlay: **FAIL**
- Refresh source / Revive from failed-player overlay: **PASS**
- Revive All while watching another video: **FAIL**
- installer-log MD was not provided to the user during QA, so direct tap logging was not performed.

Important interpretation:
- Candidate 4 fixed the main visibility/exposure problem: recovery actions are visible.
- In-player Refresh/Revive now works and is device-PASS.
- Retry playback still fails; decide whether to fix it or remove/disable/rename it if it is not a reliable recovery action.
- Revive All still disturbs foreground playback; Candidate 4 deferral was insufficient.

## Prior Candidate 3 result
Candidate 3 installed/data PASS but recovery UI stayed fully failed: failed Player showed only the old Recovery options dialog with explanation + `CANCEL`, no Retry and no Refresh. Candidate 3 also exposed the Revive All + watching another video blinking/unwatchable bug.

## Already device-PASS in 0.3.2
- ADB update/data retention
- consolidated gear menu / proper close icon
- dashboard individual Revive
- Check Status -> Player race/blink fix
- decoder fallback case
- Recently Closed / Close All with 25 tabs; cap 100
- privacy appearance/auth/reveal/deferred share
- direct failed-player recovery buttons visible
- in-player Refresh source / Revive
- general regression spot-check

## Current blockers
1. Retry playback from failed-player overlay is FAIL. Refresh works; Retry must become PASS or be intentionally removed/disabled/renamed.
2. Revive All + watching another video still causes repeated blinking/unwatchable playback. Need stronger foreground-player isolation than Candidate 4.
3. Direct tap APK update remains a separate Play Protect / installer-flow blocker. Use `INSTALLER_LOG_CAPTURE.md` to collect PackageInstaller / Play Protect reason codes. Do not uninstall the working app merely to test.

## Merge gate
Do not merge PR #2 until:
1. failed-player recovery has acceptable final behavior: Refresh/Revive remains PASS and Retry is either PASS or deliberately removed/disabled; and
2. Revive All can run while another video plays with no blink/disturbance.

Report-log-on-GitHub remains postponed. Return-to-Vivaldi unchanged.

## QA format
When explicitly asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
