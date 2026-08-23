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

## Candidate 5 — START HERE
Build/app-code head: `d8d3dbd86696b84f1ac1c508d22a0dbd814331da`.
- Actions `32663782445` / run #370
- job `97253871521`
- signed release artifact `9499473114`
- signed artifact digest `sha256:349ebb8dc16dc449e6c3f31cfcd7c620ed361adf9aa366745268e2031efb303f`
- build/sign/package/alignment PASS

Candidate 5 changes after Candidate 4:
- unreliable Retry playback removed/downgraded from failed-player recovery actions;
- in-player **Refresh source** remains the supported recovery path;
- Revive All foreground isolation strengthened to suspend/requeue active coordinator-created `revive-*` private-display sessions when PlayerActivity resumes.

Candidate 5 device QA result:
- direct APK install through Material Files: **PASS**
- install/data: **PASS**
- failed-player recovery UI: **PASS**
- user says recovery error/buttons UI should be improved later
- in-player Refresh source / Revive: **PASS**
- user notes they would not expect Refresh source to exit to dashboard
- Revive All while watching another video: **FAIL**

Additional Candidate 5 UX notes:
- Back/tab button from Player returns dashboard to beginning/top of tab list; expected return to same tab/list position.
- When Player is not fullscreen, user expects current video title/name visible at top.
- Video names/titles should load/display according to selected app language when source/resolver metadata supports localization; never fabricate translations.
- User wants all three UX items attempted in Candidate 6 because 0.3.2 has been stuck and needs visible progress.
- For localized title loading, do not machine-translate or invent. Prefer legitimate source/site language variants matching the app setting when technically available, e.g. Spanish versus English host/path variants, while staying within protected resolver rules.

Important interpretation:
- Direct APK install is now confirmed PASS via Material Files.
- Failed-player recovery and in-player Refresh are functionally accepted.
- Retry is no longer a blocker because it was deliberately removed/downgraded.
- Remaining merge blocker is Revive All disturbing foreground playback.
- Candidate 6 should target Revive All foreground safety plus the three UX improvements: return-to-same-tab dashboard anchor, non-fullscreen Player title, and app-language-aware source title/domain handling without translation.

## Direct APK install status
Direct APK installation is no longer an app/signing blocker. User discovered the failure was specific to **Files by Google**. Installing the same APK through **Material Files** works correctly. ADB in-place update works and CI package/sign/alignment checks pass.

Treat any remaining failure through Files by Google as a Files/device installer-routing quirk, not as evidence of an APK/signing problem and not as a 0.3.2 release blocker.

## Prior Candidate 4 result
Candidate 4 installed/data PASS, recovery UI visibility PASS, in-player Refresh PASS, Retry FAIL, and Revive All + watching another video FAIL. Candidate 4 deferral only delayed future revive sessions and was insufficient.

## Prior Candidate 3 result
Candidate 3 installed/data PASS but recovery UI stayed fully failed: failed Player showed only the old Recovery options dialog with explanation + `CANCEL`, no Retry and no Refresh. Candidate 3 also exposed the Revive All + watching another video blinking/unwatchable bug.

## Already device-PASS in 0.3.2
- ADB update/data retention
- direct tap install via Material Files
- consolidated gear menu / proper close icon
- dashboard individual Revive
- Check Status -> Player race/blink fix
- decoder fallback case
- Recently Closed / Close All with 25 tabs; cap 100
- privacy appearance/auth/reveal/deferred share
- direct failed-player recovery buttons visible
- in-player Refresh source / Revive
- Retry recovery deliberately removed/downgraded
- general regression spot-check

## Current blocker
Revive All + watching another video still causes repeated blinking/unwatchable playback. Candidate 5 active-session suspension was insufficient. This is the only remaining release/merge blocker.

## Candidate 6 targets
- Stronger Revive All foreground isolation, likely by never running/starting revive browser/private-display work while PlayerActivity is foreground and by making queued work visibly paused until returning to dashboard.
- Preserve dashboard scroll/anchor when returning from Player via Back or tab button.
- Show current video title/name at top of Player when not fullscreen.
- Make video title/name loading respect selected app language where source/resolver metadata or legitimate site-language variants support it; never fabricate translated titles.
- Improve failed-player error/buttons UI and make Refresh source behavior clearer if time/risk allows.

## Merge gate
Do not merge PR #2 until Revive All can run while another video plays with no blink/disturbance.

Direct tap APK install and failed-player recovery are no longer release blockers. Report-log-on-GitHub remains postponed. Return-to-Vivaldi unchanged.

## QA format
When explicitly asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
