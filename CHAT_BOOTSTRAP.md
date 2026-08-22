# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
Released `main` remains authoritative; active 0.3.2 QA work is on `work/0.3.2-correctness-ux`, PR #2. Read `PROJECT_STATE.md` and this file before substantive work.

## Safety / protected architecture
- Android UI remains bilingual English/Spanish; source comments English.
- PH/HH are technical playback targets only: URLs/manifests/codecs/resolutions/request metadata/resolver ranking/playback state/errors/local titles. Never inspect or describe media content or thumbnail imagery.
- Never bypass DRM/paywalls/auth/geo/CAPTCHA or import browser credentials.
- Never add background playback or a second ExoPlayer.

Build #234 background preparation remains protected:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.

No preparation Activity on display 0. No PlayerActivity/Media3/ExoPlayer during BG preparation.

Preserve one ExoPlayer, yt-dlp first/browser fallback, automatic/manual quality, video+audio, adaptive/sibling switching, gestures, seek preview, rotation, candidate limits/order and no imagery-based ranking.
Automatic quality: exact 720p -> 1080p -> highest below 1080p -> >1080p rare fallback.

Build #278 remains accepted player/UI DEVICE PASS baseline. Build #249 palette remains protected.

## Released baseline: 0.3.1 / versionCode 4
0.3.1 implements **Vivaldi Private + Copy URL**: copy stored original page URL, open a genuine blank Vivaldi private tab, tell user to paste; never pretend normal ACTION_VIEW is private.

0.3.1 build:
- head `6a195bd0019b3335236375650f4123f11f3bc3fc`
- Actions `32552025984`, job `96980224294`
- signed artifact `9470356520`
- ZIP SHA-256 `c4660868785eb06b52ba274a14e4f0b9e3f34aa7ac452e7374be9c0b55a90606`
- APK SHA-256 `6e02fb3df1ea831a42d4d4c582a37c46f4b68772f9cd8f438ae821fc9fa0db51`

0.3.1 installed by ADB in-place update. Focused Vivaldi Private + Copy URL device QA is now **PASS**.

Permanent signer certificate SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

## Active candidate: 0.3.2 / versionCode 5
Branch `work/0.3.2-correctness-ux`, PR #2. Do NOT merge until device QA.

Final code gate before documentation-only commits:
- Actions run `32590746439` / run #331
- job `97074080536`
- SUCCESS: debug + signed release, zipalign/package sanity, signer verification, artifacts
- signed artifact `9480279353`
- ZIP SHA-256 `9081a7851b272ad9f1ccb039c77fe5ccea32137fb73c5444d92a5f9ab896af6b`
- APK SHA-256 `bc5b854980faa214ee0b9d7ef5a7676923ffc2c95e3cac4029e47f79a3f77799`

### Implemented in 0.3.2
- single/bulk stale-tab recovery centralized in `TabMaintenanceController`
- READY + health NEEDS_REFRESH card says **Revive** and revives that same tab; ERROR uses same path
- status-check redraw callbacks only while MainActivity RESUMED
- stronger blinking/codec-contention fix: no dashboard thumbnail warm-up when PlayerActivity resumes; background FrameExtractor work serialized and suspended/cancelled during foreground playback
- Media3 same-ExoPlayer decoder fallback enabled (`DefaultRenderersFactory.setEnableDecoderFallback(true)`)
- decoder-init-specific explanation/recovery after fallback exhaustion; no stream metadata rewriting
- Recently Closed browser-like history cap raised 12 -> 100; Close All archives via same history
- main global operations moved into gear-menu sections Tabs / Library / Privacy / App
- manually activated **Hide & lock app**; app starts unlocked; full privacy curtain + FLAG_SECURE; **Reveal ExternalPlayer** uses biometric/device credential
- incoming shared URL while hidden is deferred until successful reveal
- shared `SystemAuthGate` now serves Private Favorites and app privacy curtain
- tab-card text X replaced by a proper close icon
- CI now validates PRs and checks zip alignment/package metadata/signing

Private Favorites crypto caveat remains: AES-GCM encrypted local blob, but key is not currently hardware/user-auth-bound. UI auth gating remains; do not overclaim.

### Reported decoder case
A PH HLS playback reached Media3 but failed with `ERROR_CODE_DECODER_INIT_FAILED`, Qualcomm `c2.qti.avc.decoder`, `format_supported=NO_EXCEEDS_CAPABILITIES`, 1280x720 AVC and an implausible reported frame rate around `12857.142` fps.

0.3.2 first lets Media3 try another compatible decoder in the SAME ExoPlayer. If all decoders fail, recovery offers safe existing actions. Do not forge frame-rate/codec metadata or rewrite manifests without reproducible proof.

### Direct APK install problem
Historical tap install shows generic `App not installed`, while ADB update works. 0.3.2 CI proves package/sign/alignment sanity. Test the standalone extracted APK as a normal `.apk` file, not from inside the GitHub artifact ZIP/archive view. If it still fails, capture the actual PackageInstaller/PackageManager reason from that failed attempt; do not guess and do not uninstall the working signed app merely to test.

## Owed device QA for 0.3.2
1. standalone APK tap update over 0.3.1
2. individual stale-tab Revive
3. Check status -> open video midway: stable Player, no blink/reload race
4. decoder case if reproducible: fallback plays OR graceful decoder-specific recovery
5. Close All/Recently Closed >12 tabs when practical
6. gear-menu UX + new close icon
7. Hide & lock app / Reveal ExternalPlayer; share while hidden remains deferred
8. protected player regression spot-check

Nothing in this 0.3.2 QA list is PASS yet.

## Deferred
- Report log on GitHub shortcut postponed.
- Return-to-Vivaldi stays unchanged.
- Continue disciplined cleanup/refactoring, but only delete historical paths after proving unused.

## QA format
Whenever explicitly asking the user to test an APK, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.

Do not add extra code blocks to that QA request.
