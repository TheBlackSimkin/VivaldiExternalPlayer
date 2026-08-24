# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
Released `main` remains authoritative. Active 0.3.2 work is on `work/0.3.2-correctness-ux`, PR #2. **CANDIDATE 7 DEVICE QA PASSED; PR #2 IS READY TO MERGE.** Read `PROJECT_STATE.md` and this file first.

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

## Candidate 7 — ACCEPTED
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

## Final Candidate 7 device QA
User reported all four final checks PASS:
1. Non-fullscreen Player title fix — **PASS**.
2. Failed-player recovery UI — **PASS**.
3. Refresh source remains in Player and reloads through the same persistent-tab/single-ExoPlayer path — **PASS**.
4. Revive All + foreground playback sanity check — **PASS**.

The historical diagnostics-dialog concern did not prevent acceptance of the recovery flow.

## Merge gate
**PR #2 IS READY TO MERGE.** After merge, verify the final `main` CI/build/sign/package/alignment result and record the final release run/artifact IDs before declaring 0.3.2 released.

## QA format
When asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
