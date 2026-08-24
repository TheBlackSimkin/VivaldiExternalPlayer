# Vivaldi External Player — Project State

`main` is authoritative. PR #2 (`0.3.2 correctness, privacy and dashboard cleanup`) merged successfully at `601d32e11355dc9452d01b2f9d4877b1355e1082` after Candidate 7 device QA and the latest PR-head CI both passed. Read this file and `CHAT_BOOTSTRAP.md` before substantive work.

## Protected architecture / safety
- Android UI bilingual English/Spanish; comments English.
- PH/HH are technical playback targets only: URLs, manifests, codecs, resolutions, request metadata, resolver/candidate ranking, playback states/errors, local titles. Never inspect/describe media content or thumbnail imagery.
- Never bypass DRM/paywalls/auth/geo/CAPTCHA, import browser credentials, add background playback, or add a second ExoPlayer.
- Protected Build #234 preparation path:
  `short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.
- Preserve one ExoPlayer and current resolver/quality policy. Build #278 is accepted player/UI baseline; Build #249 palette protected.

Permanent signer certificate SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

## Previous released baseline
0.3.1 / versionCode 4. Direct APK update is not an app/signing blocker: Material Files installs successfully; Files by Google was the failing installer path.

## Candidate 5 — tested baseline
App-code head `d8d3dbd86696b84f1ac1c508d22a0dbd814331da`; Actions #370 / `32663782445`; job `97253871521`; signed artifact `9499473114`; digest `sha256:349ebb8dc16dc449e6c3f31cfcd7c620ed361adf9aa366745268e2031efb303f`.

Device QA: Material Files install/data PASS; recovery functionally PASS; in-player Refresh PASS but user expected it to remain in Player; Revive All + watching another READY/revived video FAIL.

Exact Revive All repro: start Revive All, wait until some tabs are READY and others still queued/checking, enter an already READY tab, then Player immediately blinks/restarts/buffers until returning to dashboard. Player/tab-count UI can alternate between progress-style and plain-count values. Candidate 5 active-session suspension was insufficient.

## Candidate 6 — device QA completed
App-code head: `9a7f1efe8ba46e9696222b0a191a3383a32802ab`.
- Actions run `32669641573` / run #402
- job `97268348395`
- signed release artifact `9501042542`
- signed artifact digest `sha256:3ccf681f36d61f1e85f43ef769cd5aefa901fc8cc479ff6299c15118cee0f624`
- debug artifact `9501043203`
- build/sign/package/alignment: **PASS**

### Candidate 6 Revive All fix — device PASS
Root cause identified: `TabbedPlayerApplication`/`UnifiedPreparationCoordinator` previously treated `PlayerActivity` as a foreground preparation host and could launch the legacy/default-display `BackgroundPreparationActivity` or preload queued work when Player resumed. Candidate 6 bars Player from that hidden/default-display path and leaves only the protected service/private-display `TabRevivalCoordinator` running in true background.

The exact Revive All + foreground playback scenario passed on device, including continued background revival while watching. The previous blinking/restarts/interference blocker is considered resolved unless later regression evidence contradicts this.

### Candidate 6 device QA matrix
User reported:
1. Revive All + watching another READY/revived video while queue continues: **PASS**.
2. Return to watched dashboard tab/list position: **PASS**.
3. Non-fullscreen Player title: **FAIL**. A bar appears only outside fullscreen, but title text is not visible.
4. Language-aware source/title behavior: **PASS**.
5. Failed-player recovery UI polish: **NOT TESTED**.
6. Refresh source staying in Player: **NOT TESTED**.
7. Active-tab multi-select: **PASS** (user wrote `pPASS`, treated as PASS).
8. Per-tab playback preference memory: **PASS**.
9. Search/filter accordions: **PASS**.
10. Diagnostics/History access: **PASS**.
11. Short regression suite: **PASS**.

## Candidate 7 — accepted
App-code head: `a1a0ed1dd53c6ec44ed76dd9307f100a6a9fae1b`.
- Actions run `32670860768` / run #407
- job `97271395006`
- signed release artifact `9501360981`
- signed artifact digest `sha256:3291fa608b5d93577dba557ac3f84a8e5c74f490a34e27b9d2930ea4386cc53d`
- debug artifact `9501361426`
- build/sign/package/alignment: **PASS**

Candidate 7 only changes `PlayerTitleProvider`; Candidate 6 Revive All architecture and other device-PASS features are untouched.

### Candidate 7 title fix
- title text is sourced first from the exact `ResolvedMedia` JSON passed to Player;
- persistent tab title is the fallback;
- generic `Activity.title` is last-resort compatibility only;
- title view attaches directly to the actual `activity_player` `FrameLayout`, not window decor;
- if no legitimate title exists, the overlay is hidden instead of showing an empty decorative bar;
- fullscreen/system-bars-hidden behavior remains clean.

No machine translation, title inference, media-content inspection, resolver-order changes, background-playback changes, or extra ExoPlayer were introduced.

### Candidate 7 final device QA — PASS
User reported all four final checks PASS:
1. Non-fullscreen Player title fix: **PASS**.
2. Failed-player recovery UI: **PASS**.
3. In-player Refresh source remains in Player and reloads the same persistent tab/ExoPlayer path: **PASS**.
4. Revive All + foreground playback sanity/regression check: **PASS**.

The historical automatic diagnostics-dialog concern did not block acceptance in the reported recovery test.

## Final PR-head validation and merge
Latest PR-head docs commit: `aa81c55c1d7bd1b60283f9721c029cd62bf17d4f`.
- Actions run `32679562829` / run #411
- job `97293721788`
- signed release artifact `9503753613`
- signed artifact digest `sha256:de9cdf59d5e2572b5ce925294d3e51da538942b630ba95a321ddf3e18fb62225`
- debug artifact `9503754068`
- full workflow including build, signing, upload, package/alignment checks: **PASS**

PR #2 merged into `main` at `601d32e11355dc9452d01b2f9d4877b1355e1082` on 2026-08-24 UTC.

## Release status
0.3.2 / versionCode 5 is **merged and device-accepted**, but do not call the release provenance fully closed until the push-triggered `main` workflow artifact is identified and its build/sign/package/alignment result is recorded here. The workflow is configured to run on pushes to `main`.

Explicitly postponed: `Report log on GitHub` shortcut.
Return-to-Vivaldi behavior remains unchanged.

### Language/title rule
Never machine-translate, infer, rewrite, or invent titles. Prefer legitimate source-provided metadata from the app-selected language source/site variant where available; otherwise preserve original metadata/title.

## QA format
When asking user to test an APK: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
