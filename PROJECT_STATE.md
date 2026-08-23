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

## Candidate 3 device QA — definitive result
User installed Candidate 3 by ADB over the existing signed build.
- ADB update: **PASS**
- existing data retained: **PASS**
- in-player recovery UI fix: **FAIL**

Observed behavior stayed unchanged for the entire failed-player session:
- Player shows `Playback failed. Tap Playback error to view or copy the technical details.`
- tapping **Recovery options** shows title `Recovery options`
- explanatory text remains visible
- only `CANCEL` is visible/actionable
- no visible Retry playback
- no visible Refresh source / Revive

Candidate 3 also exposed:
**Revive All running + watching another video during revival = repeated blinking / effectively unwatchable video.**

## Candidate 4 build
App-code head: `9fdc16f2da7191b23c8043add636c4a5a3ad6cd4`.
- Actions `32654832188` / run #356
- job `97231847880`
- signed release artifact `9497197410`
- signed artifact digest `sha256:5207ba499b909f0ae506cfc38c600c31c92d77f31450cff7f55731d6e883b7cb`
- build/sign/package/alignment PASS

Candidate 4 changes after Candidate 3:
- direct failed-player overlay buttons for **Retry playback**, **Refresh source**, and **Recovery options**;
- `ForegroundPlaybackState` process-local foreground-player signal;
- queued Revive All work defers starting additional private-display revival sessions while a PlayerActivity is foreground;
- `INSTALLER_LOG_CAPTURE.md` added for PackageInstaller / Play Protect log capture.

## Candidate 4 device QA — mixed result
User installed Candidate 4 by ADB.
- install/data: **PASS**
- Recovery UI visibility: **PASS**
- Retry playback from failed-player overlay: **FAIL**
- Refresh source / Revive from failed-player overlay: **PASS**
- Revive All while watching another video: **FAIL**
- installer-log MD was not provided to the user during QA, so direct tap logging was not performed.

Interpretation:
- Candidate 4 fixed the main visibility/exposure problem: recovery actions are now visible and Refresh from inside Player works.
- Candidate 4 did **not** fully fix Retry playback.
- Candidate 4 did **not** fix the Revive All foreground-player blinking/unwatchable regression.

## Current blockers before merge
1. Fix or intentionally downgrade **Retry playback** behavior. Refresh source is now the working recovery path, but Retry must either work correctly or be removed/renamed/disabled when it cannot help.
2. Fix **Revive All + watching another video** foreground disturbance. Candidate 4 deferral was insufficient.
3. Optional/separate: collect direct-tap installer logs via `INSTALLER_LOG_CAPTURE.md` to diagnose the Play Protect / installer-flow block.

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
- direct player recovery actions visible
- in-player Refresh source / Revive works
- general player/dashboard regression spot-check

Implementation/refactor retained: `TabMaintenanceController` central revival policy, `SystemAuthGate` shared auth, `AppPrivacyController`, `DashboardMenu`, thumbnail decoder contention isolation, PR CI checks.

## Direct APK installation — unresolved separate blocker
Standalone extracted APK normal tap update is **FAIL** due Play Protect / installer flow. CI and successful ADB in-place update prove package/sign/alignment/signature continuity. Do not uninstall the working app merely to test.

`INSTALLER_LOG_CAPTURE.md` contains the logcat capture steps needed to gather PackageInstaller / Play Protect reason codes during the failed tap flow.

## Merge/release gate
Do not merge PR #2 until:
1. failed-player recovery has acceptable final behavior: Refresh/Revive stays PASS and Retry is either PASS or deliberately removed/disabled; and
2. Revive All can run while another video is playing without blinking/disturbing foreground playback.

Direct tap installation remains a separate known blocker unless logs identify an app-side package issue.

## Deferred
- Report log on GitHub shortcut postponed; never embed reusable GitHub credentials.
- Return-to-Vivaldi stays unchanged.
- Continue disciplined cleanup/refactoring; delete historical paths only after proving unused.

## QA request format
Whenever explicitly asking the user to test an APK, provide exactly one detailed steps/EXPECTED/RESULT code block, then one separate compact-answer code block. No extra code blocks.
