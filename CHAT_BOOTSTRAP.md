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

## Candidate 6 — START HERE
App-code head `9a7f1efe8ba46e9696222b0a191a3383a32802ab`.
- Actions `32669641573` / run #402
- job `97268348395`
- signed artifact `9501042542`
- signed artifact digest `sha256:3ccf681f36d61f1e85f43ef769cd5aefa901fc8cc479ff6299c15118cee0f624`
- debug artifact `9501043203`
- build/sign/package/alignment PASS

## Candidate 6 core Revive All change
Candidate 5 exact repro: start Revive All, wait for some READY and some queued, enter an already READY/revived tab, Player immediately blinks/restarts/buffers until returning dashboard.

Root-cause lead found: `UnifiedPreparationCoordinator` accepted `PlayerActivity` as a hidden/default-display preparation host, and Player resume/preload could launch `BackgroundPreparationActivity` for queued work. Candidate 6 bars Player from that legacy path and lets only the protected service/private-display Revive All coordinator continue in true background.

QA must prove:
1. Player stays stable while Revive All still has pending tabs; AND
2. pending tabs actually continue advancing/reviving while Player is open.
If true-background private-display revival still disturbs Player, fallback is full pause of bulk Revive All while Player foreground, resume on dashboard.

## Candidate 6 implemented UX/features
- restore dashboard toward watched tab/list anchor after leaving Player;
- show current title in non-fullscreen Player, hide in fullscreen/system-bars-hidden state;
- no machine translation/invented titles; exact original URL stays persistent identity, while known legitimate language-host variant is used for resolving metadata (narrow current PH mapping: Spanish `es.pornhub.com`, English `www.pornhub.com`);
- language-aware resolving applied to manual open, background share, Revive, browser fallback, Favorites;
- cleaner failed-player recovery panel: Refresh primary, Technical details secondary;
- Refresh stays in Player, revives same tab, polls it, and reloads same ExoPlayer at preserved position/play state when READY;
- active-tab multi-select with bulk Close / Revive / Favorite / Private Favorite;
- per-tab speed memory; existing manual-quality persistence retained; auto-quality unchanged when no manual override;
- collapsed search/filter on active tabs; collapsed search on Recently Closed, Favorites, authenticated Private Favorites;
- existing sanitized OperationLog now supports per-tab history; Recently Closed and both Favorites expose expandable Diagnostics/History;
- Private Favorites search/history remains auth-gated, FLAG_SECURE, relock-on-leave.

## Known Candidate 6 note
Historical `PlayerActivity` automatic diagnostics dialog on playback error still exists. Device QA should report whether it appears before/obstructs the new recovery panel. It was not removed after the full Candidate 6 branch passed CI because changing it requires broad replacement of the large protected PlayerActivity file.

## Candidate 5 tested status
Material Files install/data PASS; recovery functionally PASS; Refresh PASS but exited dashboard unexpectedly; Revive All foreground playback FAIL. Retry deliberately removed/downgraded.

## Merge gate
Do not merge PR #2 until Candidate 6 device QA passes the exact Revive All foreground playback scenario. If it fails, implement pause-while-watching fallback.

## QA format
When asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
