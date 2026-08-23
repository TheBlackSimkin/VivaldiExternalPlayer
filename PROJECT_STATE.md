# Vivaldi External Player — Project State

Released `main` remains authoritative. Active 0.3.2 work is on `work/0.3.2-correctness-ux`, PR #2. **Do not merge yet.** Read this file and `CHAT_BOOTSTRAP.md` before substantive work.

## Protected architecture / safety
- Android UI bilingual English/Spanish; comments English.
- PH/HH are technical playback targets only: URLs, manifests, codecs, resolutions, request metadata, resolver/candidate ranking, playback states/errors, local titles. Never inspect/describe media content or thumbnail imagery.
- Never bypass DRM/paywalls/auth/geo/CAPTCHA, import browser credentials, add background playback, or add a second ExoPlayer.
- Protected Build #234 preparation path:
  `short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.
- Preserve one ExoPlayer and current resolver/quality policy. Build #278 is accepted player/UI baseline; Build #249 palette protected.

Permanent signer certificate SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

## Released baseline
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

### Candidate 6 Revive All investigation/fix
Root cause identified: `TabbedPlayerApplication`/`UnifiedPreparationCoordinator` previously treated `PlayerActivity` as a foreground preparation host and could launch the legacy/default-display `BackgroundPreparationActivity` or preload queued work when Player resumed. That matched the user's exact trigger and explained why Candidate 4/5 private-display suspension did not solve the issue.

Candidate 6:
- bars `PlayerActivity` from `UnifiedPreparationCoordinator` hidden/default-display preparation;
- bars Player-triggered `preloadNext`/`prepareNow` through that legacy path;
- removes Candidate 5 foreground cancellation/requeue churn from Revive All;
- lets the protected service/private-display `TabRevivalCoordinator` continue in true background while Player is foreground.

**Device QA result: PASS.** The exact Revive All + foreground playback scenario passed, including the requested true-background behavior. The previous blinking/restarts/interference blocker is considered resolved unless later regression evidence contradicts this.

### Candidate 6 device QA matrix
User reported:
1. Revive All + watching another READY/revived video while queue continues: **PASS**.
2. Return to watched dashboard tab/list position: **PASS**.
3. Non-fullscreen Player title: **FAIL**. A bar appears only outside fullscreen, but the title text is not visible.
4. Language-aware title/source behavior: **PASS**.
5. Failed-player recovery UI polish: **NOT TESTED**.
6. Refresh source staying in Player: **NOT TESTED**.
7. Active-tab multi-select: **PASS** (user wrote `pPASS`, treated as PASS).
8. Per-tab playback preference memory: **PASS**.
9. Search/filter accordions: **PASS**.
10. Diagnostics/History access: **PASS**.
11. Short regression suite: **PASS**.

Candidate 6 is therefore a near-pass. Remaining acceptance work is the title overlay bug plus focused verification of recovery tests 5 and 6.

### Candidate 6 approved UX/features implemented
- dashboard return anchor: leaving Player remembers persistent tab ID and returns toward the watched tab position rather than top/start — device PASS;
- non-fullscreen Player title overlay — device FAIL because container/bar appears without visible title text;
- language-aware source preference without translation/invention: exact original URL remains persistent identity, while known legitimate language-host variants are preferred for resolution; current narrow PH mapping is Spanish -> `es.pornhub.com`, English -> `www.pornhub.com` — device PASS;
- language policy applied to manual resolution, background shares, Revive, browser fallback, and Favorite launches;
- redesigned failed-player recovery panel with concise message, **Refresh source** primary, **Technical details** secondary, and additional recovery options — Candidate 6 not yet device-tested;
- Refresh source stays in Player: same persistent tab is revived, Player polls its state, then reloads the refreshed source into the same ExoPlayer at preserved position/play state when READY; dashboard is offered only if refresh cannot finish there — Candidate 6 not yet device-tested;
- active-tab multi-select via long-press, with bulk Close / Revive / Favorite / Private Favorite; normal drag/swipe remains outside selection mode — device PASS;
- per-tab playback preference memory: manual quality was already persisted/restored by `AdaptiveQualityRuntime`; Candidate 6 adds persistent playback speed per tab without changing auto-quality policy — device PASS;
- collapsed search/filter accordion on Active Tabs; collapsed search accordion on Recently Closed, Favorites, and authenticated Private Favorites — device PASS;
- sanitized per-tab Diagnostics/History reader from the existing OperationLog; Recently Closed and both Favorites views expose expandable Diagnostics/History where matching technical history exists — device PASS;
- Private Favorites search/history remains unavailable until system authentication succeeds; FLAG_SECURE/relock behavior retained — device PASS.

### Known Candidate 6 recovery note
`PlayerActivity` still contains its historical automatic full diagnostics dialog on playback error. The new recovery panel is implemented, but Candidate 6 recovery tests 5 and 6 were not performed, so whether the historical popup materially obstructs the new panel remains unverified.

### Language/title rule
Never machine-translate, infer, rewrite, or invent titles. Prefer legitimate source-provided metadata from the app-selected language source/site variant where available; otherwise preserve original metadata/title.

## Current next work
Create a focused follow-up candidate that fixes the non-fullscreen title overlay by sourcing text directly from the actual resolved-media/persistent-tab metadata and attaching it to the Player content overlay rather than relying on generic Activity/window title behavior. Do not change the now-device-PASS Revive All background architecture.

## Merge gate
**Do not merge PR #2 yet.** The original Revive All merge blocker is device-PASS in Candidate 6. Before merge/release, fix and device-verify the non-fullscreen title regression and perform focused Candidate 6/next-candidate recovery checks for failed-player recovery UI and in-player Refresh source.

## QA format
When asking user to test an APK: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
