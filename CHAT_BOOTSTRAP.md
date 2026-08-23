# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
Released `main` remains authoritative. Active 0.3.2 work is on `work/0.3.2-correctness-ux`, PR #2. Read `PROJECT_STATE.md` and this file before substantive work.

## Protected rules
Android UI bilingual English/Spanish; comments English. PH/HH are technical playback targets only; never inspect/describe media content or thumbnails. Never bypass DRM/paywalls/auth/geo/CAPTCHA, import browser credentials, add background playback, or add a second ExoPlayer.

Protected Build #234 preparation:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.
Preserve one ExoPlayer and current resolver/quality policy. Build #278 accepted player/UI baseline; Build #249 palette protected.

## Baseline
0.3.1 / versionCode 4 is released. Permanent signer SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

## Candidate 5 — latest tested build
App-code head `d8d3dbd86696b84f1ac1c508d22a0dbd814331da`.
- Actions `32663782445` / run #370
- job `97253871521`
- signed release artifact `9499473114`
- artifact digest `sha256:349ebb8dc16dc449e6c3f31cfcd7c620ed361adf9aa366745268e2031efb303f`
- CI build/sign/package/alignment PASS

Device QA:
- direct APK install through Material Files PASS
- install/data PASS
- failed-player recovery functionally PASS; UI needs polish
- in-player Refresh PASS; user expects it to remain in Player when feasible
- Revive All + watching another READY/revived video FAIL

## Exact Revive All failure model
Sequence:
1. Start Revive All.
2. Let some tabs revive while others remain queued/checking.
3. Open an already READY/revived tab while bulk queue still exists.
4. Player disturbance starts immediately.
5. Returning to dashboard stops the disturbance.

Symptoms: whole Player/UI blinks at mixed rhythms; tab-count/status widget can alternate between progress-style and plain-count values; playback repeatedly buffers/tries to start, may show a fraction of video, then restarts. Dashboard/Recents is not visibly flashed. Candidate 5 active-session suspension was insufficient.

This is the only merge blocker.

## Candidate 6 — approved work
First investigate true-background Revive All that continues while Player is foreground with **zero** Player/UI/display interference and no weakening of Build #234. If that is complicated/risky/unreliable, bulk Revive All must pause completely while PlayerActivity is foreground and resume on dashboard.

Approved UX/features:
- restore dashboard to watched tab/list anchor when leaving Player;
- show video title at top when Player is not fullscreen;
- language-aware source/title preference: never machine-translate/invent; prefer legitimate source/site language variants matching app language when available, otherwise preserve original title;
- cleaner failed-player recovery panel with Refresh source primary and Technical details secondary;
- keep Refresh source in Player where feasible with an in-player refreshing state;
- multi-select active tabs with appropriate bulk Close/Revive/Favorite/Private Favorite actions;
- per-tab playback preference memory, especially speed and explicit manual quality; preserve auto-quality when no manual override exists;
- collapsed-by-default accordion search/filter UI on Active Tabs, Recently Closed, Favorites, and Private Favorites;
- per-item status/history should live under Diagnostics/Logs or expandable diagnostics, not always-visible card clutter; offer analogous access on closed/favorite records where relevant data exists.

Not requested: separate Revive queue status screen/card, Pin tabs, Keep-this-tab protection.

## Language/title rule
Titles are technical/local metadata. Do not translate, infer, rewrite, or classify title content. Prefer source-provided metadata from the app-selected language source/site variant when legitimately available (e.g. language-specific host/path variant). If unavailable, keep original title.

## Already device-PASS in 0.3.2
ADB update/data retention; direct Material Files install; consolidated gear/proper close icon; individual Revive; Check Status -> Player race fix; decoder fallback case; Recently Closed/Close All 25 tabs and cap 100; privacy auth/reveal/deferred share; visible recovery controls; in-player Refresh; Retry deliberately removed/downgraded; general regression spot-check.

## Merge gate
**DO NOT MERGE PR #2** until Revive All can coexist with foreground playback without blinking/disturbance/restarts. Candidate 6 features may progress in parallel.

## QA format
When explicitly asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
