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

## Build #275 — compact quality / actual-quality / DNS polish: CI PASS, DEVICE QA PENDING
Final app-code commit `dabd3054b0cfaaae820145cf8240c1c57672e4b3`.
GitHub Actions Build #275 run `31904918938`; build job `95061127058`.
Compile/assemble: PASS. Debug APK upload: PASS.
Artifact `9252088982` (`VivaldiExternalPlayer-debug-apk`).
Artifact ZIP size `26,070,225` bytes; ZIP SHA-256 `368fca62c662a1944a25015b896a3267a7921b967024e64c75075f58d5261e13`.
Extracted debug APK size `35,590,497` bytes; APK SHA-256 `a3659a7887e08ac4950bafb28d766b325f215c79f3737bc9df37c7c952d8ff55`.

### #275 player-menu changes
- Replaces stock Android PopupMenu spacing with one custom anchored PopupWindow family using 42dp rows and compact vertical inset.
- Main gear, Video Quality, Audio, Volume/Mute and Playback speed now use the same compact anchored menu style.
- Diagnostics remains a full selectable/copyable dialog.
- Approved #249 colors/resources are reused; palette itself was not changed.
- Exact `[tabs] [gear] [fullscreen]`, controller auto-hide, no visible ±10 buttons, fullscreen logic and same-player Audio/Volume/Speed behavior are preserved.

### #275 truthful quality verification
- Existing quality-switch algorithms/policy are preserved; the patch changes UI/verification, not resolution policy.
- yt-dlp manual choices remain Auto, 1080p, 720p, 480p, 360p and still call the existing resolver quality-change method.
- Browser adaptive choices still use the existing Media3 track override method; browser sibling variants still use the existing source-switch method.
- The compact Quality menu includes a non-clickable **Actual** row.
- Manual requests are persisted separately as the user's requested quality.
- Actual quality is written only from Media3 evidence, not from the request label.
- `Player.Listener.onVideoSizeChanged` is the strongest runtime evidence for adaptive playback.
- New `PlayerVideoFormatCompat.kt` supplies a conservative fallback for pinned Media3 1.10.1: it reports a selected format only if selected video tracks resolve to exactly one height; adaptive multi-height selections return null rather than pretending the highest available rendition is active.
- Manual quality can therefore report `480p ✓` only when the observed/selected height confirms 480p; otherwise it can report requested 480p with a different actual height.

### #275 DNS recovery polish
- `PlayerRecoveryProvider` detects `UnknownHostException` in the playback-error cause chain only to improve explanation.
- Shows bilingual “media host unavailable — DNS lookup failed” wording.
- Recovery dialog explains Retry vs Refresh source for this case.
- It never substitutes/rewrites the failed host.

#275 intentionally does **not** modify resolver.py, candidate ranking, 720-first Auto policy, protected private-display BG classes, PlayerActivity's one-ExoPlayer creation, Vivaldi/Brave share architecture, or the approved palette.

## Brave compatibility status
Brave-as-is compatibility passed on #264. Keep generic Android `ACTION_SEND text/plain` share targets; no special Brave architecture. Vivaldi remains the primary protected regression baseline, with Brave a known-compatible smoke target.

## Stored-for-later backlog — keep explicit
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials;
- safe stale/dead historical code cleanup only after proving paths unused;
- operations-log/diagnostics noise cleanup;
- About/version/build/README/release-note consistency;
- final distribution + permanent signing decision; never commit permanent signing material;
- revisit rather than automatically implement the old dedicated “Return to existing Vivaldi task/tab” idea.

Promoted/completed: app-level Volume/Mute is in #264; Brave compatibility passed without special code.

## Current priority
1. Device-test Build #275 specifically for compact menu sizing/style, compact Video Quality UI, and truthful manual-quality verification, especially 480p.
2. If the rare HH DNS-failing source is still conveniently available, verify the clearer DNS wording/Retry/Refresh UI; do not require that dead upstream host to become playable.
3. Preserve/regression-check successful #264 behavior rather than redesigning it.
4. If #275 is accepted, consider player UI settled and run final PH technical regression, HH technical regression, both Vivaldi share-target regressions and a small Brave smoke regression.
5. Then proceed to general hardening, diagnostics/log cleanup, stored-for-later cleanup and release-readiness work.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
