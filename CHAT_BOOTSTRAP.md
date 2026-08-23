# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
Released `main` remains authoritative. Active 0.3.2 work is on `work/0.3.2-correctness-ux`, PR #2. **DO NOT MERGE YET.** Read `PROJECT_STATE.md` and this file first.

## Protected rules
Android UI bilingual English/Spanish; comments English. PH/HH are technical playback targets only; never inspect/describe media content or thumbnails. Never bypass DRM/paywalls/auth/geo/CAPTCHA, import credentials, add background playback, or add a second ExoPlayer.

Protected Build #234 prep:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.
Preserve one ExoPlayer, current resolver order and protected quality policy. Build #278 player/UI baseline; Build #249 palette protected.

## Released baseline
0.3.1 / versionCode 4. Permanent signer SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.
Material Files direct APK update works; Files by Google was the installer-specific failure.

## Candidate 6 — device QA baseline
App-code head `9a7f1efe8ba46e9696222b0a191a3383a32802ab`; Actions `32669641573` / #402; job `97268348395`; signed artifact `9501042542`.

Device QA:
1. Revive All + foreground watching/continued queue: **PASS**.
2. Dashboard anchor return: **PASS**.
3. Non-fullscreen Player title: **FAIL** — visible bar/container, no title text.
4. Language-aware source/title: **PASS**.
5. Failed-player recovery UI: **NOT TESTED**.
6. In-player Refresh source: **NOT TESTED**.
7. Multi-select: **PASS** (`pPASS` reported; treat as PASS).
8. Per-tab preferences: **PASS**.
9. Search/filter accordions: **PASS**.
10. Diagnostics/History: **PASS**.
11. Regression check: **PASS**.

## Revive All blocker — resolved in Candidate 6
Root cause: `UnifiedPreparationCoordinator` accepted `PlayerActivity` as a hidden/default-display preparation host, so Player resume/preload could launch `BackgroundPreparationActivity` for queued work. Candidate 6 bars Player from that legacy path and allows only the protected service/private-display revival coordinator to continue in true background.

The exact previously failing device sequence now PASSes, including continued revival while watching. Do not alter this path without regression evidence.

## Candidate 7 — START HERE
Focused title-fix app-code head `a1a0ed1dd53c6ec44ed76dd9307f100a6a9fae1b`.
- Actions `32670860768` / run #407
- job `97271395006`
- signed artifact `9501360981`
- signed digest `sha256:3291fa608b5d93577dba557ac3f84a8e5c74f490a34e27b9d2930ea4386cc53d`
- debug artifact `9501361426`
- build/sign/package/alignment PASS

Candidate 7 only changes `PlayerTitleProvider`; Candidate 6 Revive All architecture and other PASS areas are untouched.

### Candidate 7 title change
- source title first from exact `ResolvedMedia` JSON handed to Player;
- persistent-tab title fallback;
- generic Activity title last;
- view attaches directly to `activity_player` FrameLayout rather than window decor;
- empty/missing legitimate title hides the overlay instead of showing an empty bar;
- non-fullscreen/system-bars-visible only; fullscreen stays clean;
- no translation/inference/content inspection.

## Remaining focused QA
Only these are needed before merge consideration:
1. Candidate 7 non-fullscreen title fix.
2. Candidate 6/7 failed-player recovery UI (previous test 5).
3. Candidate 6/7 Refresh source stays in Player and reloads same persistent tab/ExoPlayer (previous test 6).
4. One short Revive All + foreground playback sanity check to ensure the Candidate 6 fix did not regress.

Historical `PlayerActivity` automatic diagnostics dialog still exists. Recovery QA must report whether it appears first/obstructs the new recovery panel.

## Merge gate
**DO NOT MERGE PR #2 YET.** Revive All blocker is device-PASS. Merge/release requires the four focused Candidate 7 checks above to pass or have explicitly accepted final behavior.

## QA format
When asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
