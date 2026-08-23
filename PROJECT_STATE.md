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

## Candidate 6 — build ready for device QA
App-code head: `9a7f1efe8ba46e9696222b0a191a3383a32802ab`.
- Actions run `32669641573` / run #402
- job `97268348395`
- signed release artifact `9501042542`
- signed artifact digest `sha256:3ccf681f36d61f1e85f43ef769cd5aefa901fc8cc479ff6299c15118cee0f624`
- debug artifact `9501043203`
- build/sign/package/alignment: **PASS**

### Candidate 6 Revive All investigation/fix
Root-cause lead: `TabbedPlayerApplication`/`UnifiedPreparationCoordinator` previously treated `PlayerActivity` as a foreground preparation host and could launch the legacy/default-display `BackgroundPreparationActivity` or preload queued work when Player resumed. That matched the user's exact trigger and explained why Candidate 4/5 private-display suspension did not solve the issue.

Candidate 6 now:
- bars `PlayerActivity` from `UnifiedPreparationCoordinator` hidden/default-display preparation;
- bars Player-triggered `preloadNext`/`prepareNow` through that legacy path;
- removes Candidate 5 foreground cancellation/requeue churn from Revive All;
- lets the protected service/private-display `TabRevivalCoordinator` continue in true background while Player is foreground.

Device QA must verify BOTH: foreground Player is stable, and pending Revive All tabs actually continue advancing while watching. If private-display revival itself still disturbs Player, fallback is to pause bulk Revive All completely while Player is foreground and resume on dashboard.

### Candidate 6 approved UX/features implemented
- dashboard return anchor: leaving Player remembers persistent tab ID and attempts to return to the watched tab position rather than top/start;
- non-fullscreen Player title overlay; hidden when system bars/fullscreen are hidden;
- language-aware source preference without translation/invention: exact original URL remains persistent identity, while known legitimate language-host variants are preferred for resolution; current narrow PH mapping is Spanish -> `es.pornhub.com`, English -> `www.pornhub.com`;
- language policy applied to manual resolution, background shares, Revive, browser fallback, and Favorite launches;
- redesigned failed-player recovery panel with concise message, **Refresh source** primary, **Technical details** secondary, and additional recovery options;
- Refresh source stays in Player: same persistent tab is revived, Player polls its state, then reloads the refreshed source into the same ExoPlayer at preserved position/play state when READY; dashboard is offered only if refresh cannot finish there;
- active-tab multi-select via long-press, with bulk Close / Revive / Favorite / Private Favorite; normal drag/swipe remains outside selection mode;
- per-tab playback preference memory: manual quality was already persisted/restored by `AdaptiveQualityRuntime`; Candidate 6 adds persistent playback speed per tab without changing auto-quality policy;
- collapsed search/filter accordion on Active Tabs; collapsed search accordion on Recently Closed, Favorites, and authenticated Private Favorites;
- sanitized per-tab Diagnostics/History reader from the existing OperationLog;
- Recently Closed and both Favorites views expose expandable Diagnostics/History where matching technical history exists;
- Private Favorites search/history remains unavailable until system authentication succeeds; FLAG_SECURE/relock behavior retained.

### Known Candidate 6 test note
`PlayerActivity` still contains its historical automatic full diagnostics dialog on playback error. The new recovery panel is implemented, but device QA should report whether that old automatic popup still appears first or materially obstructs the new panel. It was intentionally not removed in this build because doing so would require a broad replacement of the large protected PlayerActivity file after the full Candidate 6 branch had already passed CI.

### Language/title rule
Never machine-translate, infer, rewrite, or invent titles. Prefer legitimate source-provided metadata from the app-selected language source/site variant where available; otherwise preserve original metadata/title.

## Merge gate
**Do not merge PR #2.** Candidate 6 must pass focused device QA, especially the exact Revive All foreground-playback repro. If the true-background attempt fails, implement pause-while-watching fallback before merge.

## QA format
When asking user to test an APK: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
