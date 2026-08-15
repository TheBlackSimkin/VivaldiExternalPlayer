# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Always read `PROJECT_STATE.md` and this file before substantive work. Keep both state files current whenever QA, architecture, failures, priorities, or decisions change.

## Safety / communication
- Explain plainly; user is not an advanced developer. Use connected GitHub directly. Keep source well-commented in English.
- PH/HH are technical playback targets. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/states/errors/local titles are allowed.
- Never inspect/describe/classify PH/HH media content or thumbnail imagery and never ask the user to provide it.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials.
- Never add background playback or a second ExoPlayer session.

## Protected playback/BG baseline
#234 private-display service architecture is protected:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.
No preparation Activity on display 0 and no PlayerActivity/Media3/ExoPlayer during preparation.

#236 app code `d6c1328823ce2027beecab7970b02420d1cffc7b` is the protected playback baseline and passed PH BG/Vivaldi responsiveness, Auto 720-first, manual quality including 480p, PH playback, HH smoke, Recently Closed and language persistence. Do not change BG/quality architecture without a concrete regression.

## Accepted UI / palette
#242 structure/device PASS: thumbnail grid 2 portrait / 3 landscape, no technical card text, Recently Closed grid with Recover all/Delete all, grouped Settings/About, collapsible manual URL.

#249 palette/device PASS; user: **“colors are perfect, love them”**. Keep unchanged unless requested:
- purple `#B05CFF`;
- charcoal `#17191F` family;
- white primary content;
- green/amber/red semantic only.

## Player specification
- Keep Media3 controller/timeline/play-pause/fullscreen/end replay.
- No visible ±10s buttons; double-tap left/right remains ±10s.
- Exact lower-right order: `[tab count] [gear] [fullscreen]`.
- One gear contains **Video quality, Audio, Volume / mute, Playback speed, Diagnostics**.
- Audio, Volume/Mute and speed use the same Media3 Player; never create a second ExoPlayer.
- Volume/Mute is app/player-relative and must not change Android global media volume.
- Tabs/gear are actual children of Media3 controller and auto-hide with it.
- Preserve Media3 natural end replay; no custom permanent restart button.

## Build #264 — CI PASS / DEVICE QA MOSTLY PASS
App-code commit `5b1906f1d43643a46458a77e2de67691c1f299c0`.
Actions #264 run `31900203463`; APK SHA-256 `d110c4257f9d44c47820ac15627c7a577a0d54cc3f7a2a52dcf42f65e56784e0`.

#264 implemented Recents privacy, working fullscreen, compact Audio/Volume/Speed popup submenus, app-level Volume/Mute, and stale-source `Refresh source` through the protected #234 private-display service path. Resolver ranking, 720-first policy, palette and one-player ownership were not changed.

## #264 real-device findings
User reports **almost all tests succeeded**.
- Brave Mobile as-is compatibility PASS using the existing generic Android share targets; no Brave-specific code is justified.
- Existing Vivaldi behavior remained healthy.
- Recents/fullscreen/player-control follow-ups and Audio/Volume/Speed/Diagnostics behavior were successful unless later regression evidence says otherwise.
- User also tried one unrelated extra site outside the PH/HH test scope and the generic flow worked; do not create a new site-specific architecture from that incidental success.

Remaining player-quality/UI issues:
1. **Video Quality still opens a centered window/dialog.** This is expected from current code: the gear still invokes `qualityButton.performClick()`, and PlayerActivity's browser + yt-dlp quality pickers are AlertDialogs.
2. **480p still does not show an obvious visible change.** Do not mark manual 480p as verified on #264. Current diagnostics list available qualities/declared size but do not report Media3's actually selected video-track height after a manual browser-track override. Next quality work should show requested vs actual/selected height and only claim 480p success when Media3 confirms it.
3. QoL: make the gear menu and submenus slightly shorter vertically/more compact while remaining touch-friendly, and move Video Quality into the same compact anchored-menu family.

## Rare HH edge case on #264
A very small set of apparently older HH sources can fail while normal HH playback remains healthy.
Observed technical diagnostic:
- browser resolver, single HLS source;
- master host `master-lengs.org`;
- Media3 `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED (2001)`;
- nested `UnknownHostException` for downstream host `eng-jaen.top`, `EAI_NODATA` / no address associated with hostname.

Interpretation: Media3 reached an HLS manifest which referenced another host, and Android DNS could not resolve that child host. This is before codec/decoder playback and is best treated as a rare source/host availability problem, not a general HH regression.

Do NOT add site-specific DNS substitution, guessed mirror/host rewriting, credential import or protected-access bypass. Safe app-side behavior is clearer `media host unavailable / DNS lookup failed` diagnostics plus existing Retry playback / Refresh source / legitimate alternate-candidate recovery when available. If every legitimate candidate ultimately points to the dead host, report it unavailable.

## Stored-for-later backlog — keep explicit
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials;
- safe stale/dead historical code cleanup only after proving paths unused;
- operations-log/diagnostics noise cleanup;
- About/version/build/README/release-note consistency;
- final distribution + permanent signing decision; never commit permanent signing material;
- revisit, rather than automatically implement, the old dedicated “Return to existing Vivaldi task/tab” idea.

Promoted/completed: app-level Volume/Mute is in #264; Brave compatibility passed without special code.

## Next priority
1. Focused player polish only: compact **Video Quality** menu, slightly tighter menu/submenu row height, and truthful requested-vs-actual manual-quality verification (especially 480p).
2. Preserve every successful #264 behavior, #234 BG architecture, one ExoPlayer, 720-first auto policy and #249 palette.
3. Treat the rare HH child-host DNS failure as an availability edge case; improve wording/recovery UX only, no host hacks.
4. After this small quality/menu polish, run final PH + HH technical regressions, both Vivaldi share-target regressions and a small Brave smoke, then hardening/release-readiness work.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
