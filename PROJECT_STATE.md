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

Interpretation:
- Candidate 4 fixed the main visibility/exposure problem: recovery actions are now visible and Refresh from inside Player works.
- Candidate 4 did **not** fully fix Retry playback.
- Candidate 4 did **not** fix the Revive All foreground-player blinking/unwatchable regression.

## Candidate 5 build
App-code head: `d8d3dbd86696b84f1ac1c508d22a0dbd814331da`.
- Actions `32663782445` / run #370
- job `97253871521`
- signed release artifact `9499473114`
- signed artifact digest `sha256:349ebb8dc16dc449e6c3f31cfcd7c620ed361adf9aa366745268e2031efb303f`
- build/sign/package/alignment PASS

Candidate 5 changes after Candidate 4:
- unreliable Retry playback removed/downgraded from the failed-player recovery actions;
- in-player **Refresh source** remains the supported recovery path;
- Revive All foreground isolation strengthened to suspend/requeue active coordinator-created `revive-*` private-display sessions when PlayerActivity resumes.

## Candidate 5 device QA — mixed result
User installed Candidate 5 directly from APK through **Material Files**.
- direct APK install through Material Files: **PASS**
- install/data: **PASS**
- failed-player recovery UI: **PASS**
- user notes recovery error/buttons UI should be improved later
- in-player Refresh source / Revive: **PASS**
- user notes they would not expect Refresh source to exit to dashboard
- Revive All while watching another video: **FAIL**

Interpretation:
- Direct APK install is now confirmed PASS via Material Files.
- Failed-player recovery and in-player Refresh are accepted functionally.
- Retry is no longer a blocker because it was deliberately removed/downgraded.
- Remaining merge blocker is Revive All disturbing foreground playback.
- UX backlog: improve failed-player error/buttons presentation and reconsider whether Refresh source should stay in-player or show a clearer transition instead of unexpectedly exiting to dashboard.

## Direct APK installation — resolved as app/signing blocker
Direct APK installation is **not an app/package/signing blocker**.

User discovered the failure was specific to **Files by Google**. Installing the same APK by tapping it through **Material Files** works correctly. ADB in-place install also works, and CI signing/package/alignment checks pass.

Track any remaining direct-tap issue as a **Files by Google / device installer routing quirk**, not a blocker for 0.3.2 and not evidence of a bad APK. `INSTALLER_LOG_CAPTURE.md` can remain for future diagnostics only.

## Current blockers before merge
1. Fix **Revive All + watching another video** foreground disturbance. Candidate 5 active-session suspension was insufficient.

## UX backlog
- Improve failed-player error/buttons UI.
- Make Refresh source behavior clearer; user did not expect Player to exit to dashboard after tapping Refresh.

## 0.3.2 features already device-PASS
- ADB in-place update; existing data retained
- direct tap APK install works via Material Files
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
- Retry recovery deliberately removed/downgraded
- general player/dashboard regression spot-check

Implementation/refactor retained: `TabMaintenanceController` central revival policy, `SystemAuthGate` shared auth, `AppPrivacyController`, `DashboardMenu`, thumbnail decoder contention isolation, PR CI checks.

## Merge/release gate
Do not merge PR #2 until Revive All can run while another video is playing without blinking/disturbing foreground playback.

Direct tap installation is no longer a release blocker because Material Files installs the APK successfully. Failed-player recovery is functionally acceptable, with UI polish deferred.

## Deferred
- Report log on GitHub shortcut postponed; never embed reusable GitHub credentials.
- Return-to-Vivaldi stays unchanged.
- Continue disciplined cleanup/refactoring; delete historical paths only after proving unused.

## QA request format
Whenever explicitly asking the user to test an APK, provide exactly one detailed steps/EXPECTED/RESULT code block, then one separate compact-answer code block. No extra code blocks.
