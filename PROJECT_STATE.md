# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA results, failures, decisions, or priorities change.

## Working / safety rules
- Conversation English; Android UI bilingual English/Spanish. Explain plainly; user is not an advanced developer.
- Use connected GitHub tools directly. Source should contain abundant English comments.
- PH and HH are technical playback targets. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed.
- Do **not** inspect, describe, classify, summarize, or request PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected playback baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p; >1080 only rare fallback.
Preserve Vivaldi share targets; yt-dlp first/browser fallback; automatic/manual quality; video+audio; adaptive/sibling switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate limits/order; page-config families; no imagery-based ranking; exactly one actual ExoPlayer playback session.
Permanent release signing remains deferred. Debug GitHub Actions APKs are the QA path; never commit a permanent signing key.

## Protected BG architecture
Build #234 established the service-owned private-display path:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.
No preparation Activity on display 0 and no PlayerActivity/Media3/ExoPlayer during preparation. Do not change this architecture without a concrete regression.

Build #236 app commit `d6c1328823ce2027beecab7970b02420d1cffc7b` remains the protected playback baseline. Device QA passed PH BG/Vivaldi responsiveness, Auto 720-first, manual quality including 480p at that stage, playback sanity, HH technical smoke, Recently Closed and language persistence.

## Accepted UI / protected palette
- Build #242 structure/device PASS: Vivaldi-inspired thumbnail tabs; 2 columns portrait / 3 landscape; no technical lifecycle text on normal cards; Recently Closed grid; grouped Settings/About; collapsible manual URL; player tab-count + gear concept.
- Build #249 palette/device PASS, user: **“colors are perfect, love them”**.
- Keep purple `#B05CFF`, charcoal `#17191F` family, white primary content; green/amber/red semantic only. Do not change unless user asks.

## Player-control specification
- Keep Media3 controller/timeline/play-pause/fullscreen/end replay.
- No visible ±10s buttons; GesturePlayerView double-tap left/right remains -10s/+10s.
- Exact lower-right order: `[tab count] [gear] [fullscreen]`; tab count opens dashboard.
- One gear contains **Video quality, Audio, Volume / mute, Playback speed, Diagnostics**.
- Audio, app-level Volume/Mute and Playback speed operate on the same Media3 Player; never create a second ExoPlayer.
- Volume/Mute changes only Player-relative volume, never Android global media volume.
- Tabs/gear are real Media3-controller children and auto-hide with it.
- Preserve Media3 natural end replay; no permanent custom restart button.
- Simple gear choices should use compact anchored menus. Diagnostics may remain a full dialog because it is long/selectable/copyable.

## Build #264 — CI PASS / DEVICE QA MOSTLY PASS
App commit `5b1906f1d43643a46458a77e2de67691c1f299c0`; Actions #264 run `31900203463`; artifact `9250881808`; APK SHA-256 `d110c4257f9d44c47820ac15627c7a577a0d54cc3f7a2a52dcf42f65e56784e0`.

#264 implemented Recents privacy, functional fullscreen, compact Audio/Volume/Speed popup submenus, app-level Volume/Mute, and stale-source `Refresh source` through the protected #234 private-display service path. Resolver ranking, 720-first policy, palette and one-player ownership were unchanged.

### #264 real-device results
User reported almost all tests successful:
- Recents/privacy and fullscreen follow-up behavior worked;
- Audio, Volume/Mute, Playback speed and Diagnostics worked;
- established controller auto-hide/gestures/palette remained healthy;
- existing Vivaldi flow remained healthy;
- **Brave Mobile works as-is with the generic Android share targets**, so no Brave-specific code is justified.
User also tried an unrelated extra site outside the PH/HH scope and reported the generic flow worked; treat this only as evidence for keeping the share architecture generic.

### #264 remaining findings which motivated #275
1. Video Quality still opened centered AlertDialogs because the gear delegated to PlayerActivity's old quality button/dialog handlers.
2. Selecting 480p did not show an obvious change. Do not treat the requested label itself as proof of actual rendition. The next implementation must distinguish requested/manual quality from Media3-observed actual height.
3. User requested slightly shorter/more compact gear-menu and submenu rows.

## Rare HH HLS DNS edge case observed on #264
A very small set of apparently older HH sources can fail while normal HH playback remains healthy. One technical diagnostic showed:
- resolver `browser`, mode `single`;
- HLS master host `master-lengs.org`, path `/api/v3/hh/tsf-monogatari-1-720p-v1x/master.m3u8`;
- Media3 `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED (2001)`;
- nested `UnknownHostException` for downstream host `eng-jaen.top`, `EAI_NODATA` / no DNS address.

Interpretation: Media3 reached an HLS manifest which referenced a downstream host Android could not resolve. This occurs before codec/decoder playback and is a rare source/host availability edge case, not evidence of a general HH regression.

Rules for this case:
- no site-specific DNS substitution, guessed mirror/host rewriting, credentials, or protected-access bypass;
- Retry playback remains useful if DNS/network failure is temporary;
- Refresh source may obtain a new legitimate manifest from the original page;
- existing legitimate alternate-candidate flow may be used if alternatives were already detected;
- if every legitimate source points to the unavailable host, report it unavailable rather than inventing a replacement.

## Build #274 — compile failure caught before device QA
App/source head `3fcbabd1c953d1021ca6c28b9317be6ff024a62b`; Actions run `31904748898`; build job `95060714556`.
The first compact-quality implementation used `Player.videoFormat`, which is not exposed by this project's pinned Media3 Player interface. Kotlin compilation failed with `Unresolved reference 'videoFormat'`. No test APK was produced/handed to the user. The intended feature scope was kept unchanged and the accessor was replaced by a compatibility helper in #275.

## Build #275 — compact quality / actual-quality / DNS polish: CI PASS, DEVICE MENU FAIL
Final app-code commit `dabd3054b0cfaaae820145cf8240c1c57672e4b3`.
GitHub Actions Build #275 run `31904918938`; build job `95061127058`.
Compile/assemble: PASS. Debug APK upload: PASS.
Artifact `9252088982` (`VivaldiExternalPlayer-debug-apk`).
Artifact ZIP size `26,070,225` bytes; ZIP SHA-256 `368fca62c662a1944a25015b896a3267a7921b967024e64c75075f58d5261e13`.
Extracted debug APK size `35,590,497` bytes; APK SHA-256 `a3659a7887e08ac4950bafb28d766b325f215c79f3737bc9df37c7c952d8ff55`.

### #275 implementation
- Replaced stock Android PopupMenu spacing with one custom PopupWindow family using 42dp rows and compact vertical inset.
- Main gear, Video Quality, Audio, Volume/Mute and Playback speed used that same compact popup family.
- Diagnostics stayed a full selectable/copyable dialog.
- Existing quality-switch algorithms/policy were preserved; requested and Media3-observed actual quality were stored separately.
- `Player.Listener.onVideoSizeChanged` is the strongest runtime evidence for adaptive actual height; `PlayerVideoFormatCompat.kt` is a conservative fallback for pinned Media3 1.10.1.
- Manual quality may claim `480p ✓` only if Media3 evidence confirms 480p; otherwise requested and actual must remain distinct.
- `PlayerRecoveryProvider` detects nested `UnknownHostException` only to show clearer bilingual “media host unavailable — DNS lookup failed” wording and explain Retry vs Refresh; it never rewrites the host.

### #275 real-device result
- **Gear menu rendering FAIL.** User tapped the gear and saw only a roughly 2 mm-high rectangle, so Quality/Audio/Volume/Speed/Diagnostics could not be tested further on #275.
- This is treated as a popup geometry/clipping bug, not merely a preference that rows were too compact.
- Code review identified the risky combination: `PopupWindow` height was `WRAP_CONTENT` and `showAsDropDown()` was called from Media3's bottom controller row. On the user's device, the popup was clipped to the tiny remaining area below the anchor rather than displaying the full menu above it.
- **Rare DNS wording improvement PASS-as-designed.** The same rare source still did not play, but the failure explanation was clearer. This is expected when the downstream host remains genuinely unresolvable; no bypass or guessed replacement should be added.

#275 intentionally did **not** modify resolver.py, candidate ranking, 720-first Auto policy, protected private-display BG classes, PlayerActivity's one-ExoPlayer creation, Vivaldi/Brave share architecture, or the approved palette.

## Build #278 — popup geometry correction: CI PASS / DEVICE PASS
App-code commit `8b0566c68eb9082c0aed62e202edfc1a29232983`.
GitHub Actions Build #278 run `31905713180`; build job `95063044270`.
Compile/assemble: PASS. Debug APK upload: PASS.
Artifact `9252287185` (`VivaldiExternalPlayer-debug-apk`).
Artifact ZIP size `26,070,848` bytes; ZIP SHA-256 `9076f2c5aec6fb830fb0551195a1bd475e54051d0f6c380aa78c6be9504318ec`.
Extracted debug APK size `35,590,609` bytes; APK SHA-256 `ee5893ef22a7a38758293ce9647ac133f09bea8527855c836c8dd65f13ba6043`.

### #278 focused fix
- Changes only `PlayerChromeProvider.kt` relative to the #275 app code; intervening commits are documentation only.
- Row height is relaxed from 42dp to 44dp: still compact, but less aggressive for touch.
- Popup height is calculated explicitly as `rowCount * rowHeight + vertical insets`; no `WRAP_CONTENT` height.
- Popup placement no longer relies on `showAsDropDown()` auto-flipping from the bottom controller row.
- The complete popup is positioned explicitly above the gear using its screen coordinates and the visible display frame, with a small screen margin and a bounded below-anchor fallback only if there truly is insufficient space above.
- Quality verification, DNS wording, fullscreen, controller order/auto-hide, Audio/Volume/Speed same-player behavior, resolver/BG architecture, share flow and approved palette are unchanged from #275/#264.

### #278 real-device result — ACCEPTED PLAYER UI BASELINE
User reports **all requested #278 checks worked as expected**.
Treat as PASS for:
- gear menu renders at normal visible height and compactness is acceptable;
- compact anchored Video Quality submenu works instead of the old centered picker;
- Actual-quality row / requested-vs-actual quality verification works as intended, including the manual-quality test path used for 480p;
- Audio, app-level Volume/Mute, Playback speed and Diagnostics remain functional;
- exact lower-right `[tab count] [gear] [fullscreen]` behavior, fullscreen, tab dashboard, controller auto-hide, double-tap ±10s and no visible ±10 buttons remain healthy;
- approved #249 colors remain unchanged.
The exact numeric requested/actual quality values from this pass were not separately recorded, but the user explicitly reported the full #278 checklist worked as expected.

Player-control/menu UI should now be treated as settled unless later regression evidence appears.

## Brave compatibility status
Brave-as-is compatibility passed on #264. Keep generic Android `ACTION_SEND text/plain` share targets; no special Brave architecture. Vivaldi remains the primary protected regression baseline, with Brave a known-compatible smoke target.

## Stored-for-later backlog — keep explicit
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials;
- safe stale/dead historical code cleanup only after proving paths unused;
- operations-log/diagnostics noise cleanup;
- About/version/build/README/release-note consistency;
- final distribution + permanent signing decision; never commit permanent signing material;
- revisit rather than automatically implement the old dedicated “Return to existing Vivaldi task/tab” idea.

Promoted/completed: app-level Volume/Mute is in #264; Brave compatibility passed without special code; player menu/quality UI is accepted on #278.

## Current priority
1. Run the final **PH technical regression** against the accepted #278 player/UI baseline.
2. Run the final **HH technical regression**, treating the known rare downstream-DNS failure as a documented source-availability edge case rather than a general regression.
3. Re-test both Vivaldi share targets end-to-end and run a small Brave as-is smoke regression.
4. Then move to general hardening and failure-edge testing, diagnostics/operations-log cleanup, safe stale/dead-path cleanup, and About/version/build/documentation consistency.
5. Release distribution and permanent signing remain final-stage decisions only.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
