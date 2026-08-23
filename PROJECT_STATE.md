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

## Candidate 5 build
App-code head: `d8d3dbd86696b84f1ac1c508d22a0dbd814331da`.
- Actions `32663782445` / run #370
- job `97253871521`
- signed release artifact `9499473114`
- signed artifact digest `sha256:349ebb8dc16dc449e6c3f31cfcd7c620ed361adf9aa366745268e2031efb303f`
- build/sign/package/alignment PASS

Candidate 5 device QA:
- direct APK install through Material Files: **PASS**
- install/data: **PASS**
- failed-player recovery UI: **PASS** functionally; UI polish requested
- in-player Refresh source / Revive: **PASS**; user does not expect Refresh to exit immediately to dashboard
- Revive All while watching another video: **FAIL**

### Revive All failure sequence clarified by user
1. Tap **Revive All**.
2. Wait while some tabs revive and others remain queued/checking.
3. Open an already-READY/revived tab while the bulk queue still exists.
4. Player disturbance starts immediately.
5. Disturbance stops when returning to dashboard.

Observed symptoms:
- whole player/UI can blink, but different elements do not blink at exactly the same rhythm;
- tab-count/status box can alternate between a progress-like value (example `5/23 check`) and a simple count (example `24`), sometimes with the first number increasing;
- playback buffers/tries to start, may show a fraction of a second, then the disturbance makes startup restart;
- only Player is visible during this; dashboard/Recents is not visibly flashed;
- reproducible whenever a revived/READY tab is entered while Revive All still has tabs left;
- queued/revival states appeared roughly where they were when Player was entered, but advancement while watching was not verified.

Candidate 5 active-session suspension was insufficient. This remains the merge blocker.

## Direct APK installation — resolved
Direct APK installation is **not** an app/package/signing blocker. Material Files installs/updates the APK successfully. Failure was specific to Files by Google/device installer routing. ADB install and CI signing/package/alignment also pass.

## Candidate 6 — approved scope
User explicitly wants visible progress in Candidate 6 while the remaining blocker is investigated.

### Revive All foreground behavior
First investigate whether Revive All can continue in true background while Player is foreground **without any Player/UI/display interference** and without weakening protected Build #234 architecture. If that is complicated, unreliable, or requires risky architecture changes, use the safe fallback: bulk Revive All pauses completely while PlayerActivity is foreground and resumes after returning to dashboard. Individual in-player Refresh remains a separate direct user action.

### Approved UX improvements from the original proposal
- Preserve dashboard scroll/anchor so Back/tab button from Player returns to the watched tab position, not the top/start.
- Show current video title/name at the top of Player when not fullscreen; keep fullscreen clean.
- Language-aware source/title preference: no machine translation and no invented titles. When legitimate site/source language variants exist, prefer the variant matching the app language (for example Spanish versus English/default host/path variants) and use source-provided metadata only.
- Redesign failed-player recovery into a cleaner panel with concise error text, **Refresh source** as the primary recovery action, and **Technical details** as secondary.
- Keep **Refresh source** in Player where feasible: show an in-player refreshing state and continue/reload the same tab on success; only transition to dashboard when technically necessary or explicitly chosen.
- The previously proposed Revive queue status screen/card is **not needed**; current progress information is not considered confusing.

### Approved new features
- **Multi-select mode**: long-press/select multiple active tabs and apply appropriate bulk actions such as Close, Revive, Add to Favorites, and Add to Private Favorites, while preserving confirmation/privacy rules.
- **Per-tab playback preference memory**: remember user-chosen playback preferences for that persistent tab (especially playback speed and explicit manual quality choice; volume/mute only if safe/appropriate) and restore them when reopening the same tab. Preserve automatic quality policy when no manual override exists.
- **Accordion search/filter UI** across Active Tabs, Recently Closed, Favorites, and Private Favorites. Keep it collapsed by default so normal screens stay compact. Search/filter should operate on technical/local metadata already stored by the app and must not inspect media imagery/content.
- **Per-item status/history belongs in Diagnostics/Logs**, not as always-visible card clutter. Where useful, provide it through an expandable/accordion diagnostics view and include equivalent access for Recently Closed and both Favorites views when the stored record has relevant technical history.

Ignored from the new-feature proposal: Pin tabs and Keep-this-tab protection.

## Language/title rule
Titles are technical/local metadata only. Do not translate, infer, rewrite, or classify title content. Prefer legitimate source-provided title metadata from the app-selected language source/site variant when available. If no matching localized source metadata exists, preserve the original title.

## 0.3.2 features already device-PASS
- ADB in-place update; existing data retained
- direct tap APK install via Material Files
- consolidated gear menu / proper close icon
- dashboard individual Revive
- Check Status -> Player race/blink fix
- decoder fallback case
- Recently Closed / Close All with 25 tabs; history cap 100
- privacy appearance/auth/reveal/deferred share
- direct failed-player recovery controls visible
- in-player Refresh source / Revive
- Retry recovery deliberately removed/downgraded
- general player/dashboard regression spot-check

Implementation/refactor retained: `TabMaintenanceController` central revival policy, `SystemAuthGate` shared auth, `AppPrivacyController`, `DashboardMenu`, thumbnail decoder contention isolation, PR CI checks.

## Merge/release gate
Do not merge PR #2 until Revive All can coexist with foreground playback without blinking/disturbing/restarting Player. Candidate 6 UX/features may advance in parallel but must not weaken the protected architecture or hide the blocker.

## Deferred
- Report log on GitHub shortcut postponed; never embed reusable GitHub credentials.
- Return-to-Vivaldi stays unchanged.
- Continue disciplined cleanup/refactoring; delete historical paths only after proving unused.

## QA request format
Whenever explicitly asking the user to test an APK, provide exactly one detailed steps/EXPECTED/RESULT code block, then one separate compact-answer code block. No extra code blocks.
