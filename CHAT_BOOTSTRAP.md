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

## Candidate 6 — DEVICE QA RESULT
App-code head `9a7f1efe8ba46e9696222b0a191a3383a32802ab`.
- Actions `32669641573` / run #402
- job `97268348395`
- signed artifact `9501042542`
- signed artifact digest `sha256:3ccf681f36d61f1e85f43ef769cd5aefa901fc8cc479ff6299c15118cee0f624`
- debug artifact `9501043203`
- build/sign/package/alignment PASS

Candidate 6 device QA:
1. Revive All + foreground watching while queue continues: **PASS**.
2. Return to watched dashboard tab/list anchor: **PASS**.
3. Non-fullscreen Player title: **FAIL** — a bar/container appears outside fullscreen but no title text is visible.
4. Language-aware source/title behavior: **PASS**.
5. Failed-player recovery UI: **NOT TESTED**.
6. Refresh source stays in Player: **NOT TESTED**.
7. Multi-select active tabs: **PASS** (`pPASS` reported, treated as PASS).
8. Per-tab playback preferences: **PASS**.
9. Search/filter accordions: **PASS**.
10. Diagnostics/History: **PASS**.
11. Regression spot-check: **PASS**.

## Revive All blocker — resolved in Candidate 6
Candidate 5 exact repro: start Revive All, wait for some READY and some queued, enter an already READY/revived tab, Player immediately blinks/restarts/buffers until returning dashboard.

Root cause: `UnifiedPreparationCoordinator` accepted `PlayerActivity` as a hidden/default-display preparation host, and Player resume/preload could launch `BackgroundPreparationActivity` for queued work. Candidate 6 bars Player from that legacy path and lets only the protected service/private-display Revive All coordinator continue in true background.

The exact device scenario now PASSes. Do not disturb this architecture without regression evidence.

## Candidate 6 implemented UX/features
- dashboard anchor restore — PASS;
- non-fullscreen title overlay — FAIL because text is missing even though the bar/container is visible;
- language-aware resolving without translation/invention, original URL identity preserved — PASS;
- cleaner failed-player recovery panel — not yet tested in Candidate 6;
- Refresh stays in Player and reloads same ExoPlayer when refreshed source becomes READY — not yet tested in Candidate 6;
- multi-select Close / Revive / Favorite / Private Favorite — PASS;
- per-tab speed memory + existing manual-quality persistence — PASS;
- collapsed search/filter across active, closed, Favorites, authenticated Private Favorites — PASS;
- sanitized expandable Diagnostics/History across relevant collections — PASS;
- Private Favorites auth/FLAG_SECURE/relock boundary retained — PASS.

## Current next step
Fix the title overlay narrowly. Current `PlayerTitleProvider` relies on generic `activity.title` and window decor placement. Change it to obtain the current source-provided title from resolved-media/persistent-tab metadata and attach directly to the Player content `FrameLayout`. If no legitimate title exists, hide the overlay instead of showing an empty bar.

Then build a focused follow-up candidate and ask only for:
- title overlay verification;
- failed-player recovery UI (previous test 5);
- in-player Refresh source (previous test 6);
- short sanity check that Revive All foreground playback remains stable.

## Known recovery note
Historical `PlayerActivity` automatic diagnostics dialog on playback error still exists. Tests 5/6 were not done on Candidate 6, so whether it obstructs the new recovery panel remains unverified.

## Merge gate
**Do not merge PR #2 yet.** Revive All blocker is device-PASS. Remaining acceptance: fix title overlay and device-test recovery UI + in-player Refresh.

## QA format
When asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
