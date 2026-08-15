# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Always read `PROJECT_STATE.md` and this file before substantive work. Keep both current whenever QA, architecture, failures, priorities, or decisions change.

## Safety / communication
- Explain plainly; user is not an advanced developer. Use connected GitHub directly. Keep source well-commented in English.
- PH/HH are technical playback targets. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/states/errors/local titles are allowed.
- Never inspect/describe/classify PH/HH media content or thumbnail imagery and never ask the user to provide it.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials.
- Never add background playback or a second ExoPlayer session.

## Protected baseline
#234 BG architecture is protected:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.
No preparation Activity on display 0 and no PlayerActivity/Media3/ExoPlayer during preparation.

#236 commit `d6c1328823ce2027beecab7970b02420d1cffc7b` is the protected playback baseline. Preserve Auto exact 720 -> 1080 -> highest below 1080, manual quality, video+audio, browser fallback, gestures, rotation, bilingual UI and exactly one ExoPlayer.

#242 UI structure passed. #249 palette passed and user said “colors are perfect, love them”: keep purple `#B05CFF`, charcoal `#17191F` family, white primary; green/amber/red semantic only.

## Player specification
- Media3 controller/timeline/play-pause/fullscreen/end replay.
- No visible ±10s buttons; double-tap left/right remains ±10s.
- Lower-right `[tab count] [gear] [fullscreen]`; tab count opens dashboard.
- One gear: Video quality, Audio, Volume/mute, Playback speed, Diagnostics.
- Audio/Volume/Speed use same Player; Volume never changes Android global volume.
- Controls auto-hide together. Diagnostics may remain full dialog; simple settings should use compact anchored menus.

## Build #264 — CI PASS / DEVICE QA MOSTLY PASS
App commit `5b1906f1d43643a46458a77e2de67691c1f299c0`; Actions #264 run `31900203463`; APK SHA-256 `d110c4257f9d44c47820ac15627c7a577a0d54cc3f7a2a52dcf42f65e56784e0`.

#264 implemented Recents privacy, functional fullscreen, compact Audio/Volume/Speed, app-level Volume/Mute, and stale-source Refresh source through protected #234 BG preparation.

User reports almost all #264 tests succeeded, including Vivaldi health and **Brave Mobile as-is compatibility** using the generic Android share targets. No Brave-specific code is justified.

Remaining #264 issues which motivated #275:
- Video Quality still used centered AlertDialogs.
- 480p request showed no obvious visual change; requested quality must not be treated as proof of actual rendition.
- user requested slightly more compact vertical spacing in gear menus/submenus.

## Rare HH DNS edge case
One rare older-looking HH HLS source produced Media3 network error 2001 with nested `UnknownHostException`: HLS master host `master-lengs.org` referenced downstream `eng-jaen.top`, which Android DNS could not resolve (`EAI_NODATA`). Most HH sources still work.

Treat as rare source/host availability, before codec decoding. No host rewriting, DNS substitution, guessed mirror, credentials or bypass. Safe recovery: clearer DNS wording, Retry for transient DNS, Refresh source for a fresh legitimate manifest, and legitimate alternate candidates if already detected.

## Build #274 — compile failure caught in CI
App/source head `3fcbabd1c953d1021ca6c28b9317be6ff024a62b`; Actions run `31904748898`; job `95060714556`.
Initial quality-verification code referenced `Player.videoFormat`, which pinned Media3 1.10.1 does not expose. Kotlin compile failed; no APK was handed to the user. Scope was unchanged and corrected in #275.

## Build #275 — compact quality + actual-quality + DNS polish: CI PASS / DEVICE QA PENDING
Final app-code commit `dabd3054b0cfaaae820145cf8240c1c57672e4b3`.
Actions #275 run `31904918938`; build job `95061127058`; compile and debug upload PASS.
Artifact `9252088982`.
ZIP size `26,070,225`; ZIP SHA-256 `368fca62c662a1944a25015b896a3267a7921b967024e64c75075f58d5261e13`.
APK size `35,590,497`; APK SHA-256 `a3659a7887e08ac4950bafb28d766b325f215c79f3737bc9df37c7c952d8ff55`.

#275 changes:
- custom anchored popup family with 42dp rows for main gear + Quality + Audio + Volume + Speed;
- Diagnostics remains full selectable/copyable dialog;
- Quality keeps existing selection logic/policy but is no longer a centered picker;
- compact Quality menu includes an Actual row;
- manual requested height and Media3-observed actual height are stored separately;
- `onVideoSizeChanged` is strongest actual-height evidence;
- new `PlayerVideoFormatCompat.kt` is only a conservative fallback for pinned Media3: it reports a selected height only when selected video tracks have exactly one distinct height; adaptive multi-height selection returns null;
- manual 480p may say `480p ✓` only when Media3 evidence confirms 480p, otherwise requested and actual are shown separately;
- DNS recovery detects `UnknownHostException` only to show clearer bilingual wording and explain Retry vs Refresh; it never rewrites the host.

#275 does NOT change resolver.py, candidate ranking, 720-first Auto policy, protected BG service/private-display classes, one-player creation, Vivaldi/Brave share architecture, or approved colors.

## Stored-for-later backlog
- secure browser-based `Report log on GitHub` shortcut with no embedded reusable credential;
- safe dead/historical code cleanup only after proving unused;
- diagnostics/operations-log noise cleanup;
- About/version/build/README/release-note consistency;
- distribution + permanent signing last, never commit permanent signing material;
- revisit old dedicated “Return to existing Vivaldi task/tab” idea rather than automatically implementing it.

## Next priority
1. Device-test #275: compact menu dimensions, compact Quality UI, actual-vs-requested quality (especially 480p), and DNS wording if the rare failing source is convenient.
2. Preserve successful #264 behavior.
3. If #275 passes, consider player UI settled; run final PH + HH technical regression, both Vivaldi share-target regressions and small Brave smoke.
4. Then hardening, diagnostics/log cleanup and release-readiness/stored-for-later work.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
