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

## Build #275 — CI PASS / DEVICE MENU FAIL
App commit `dabd3054b0cfaaae820145cf8240c1c57672e4b3`; Actions #275 run `31904918938`; APK SHA-256 `a3659a7887e08ac4950bafb28d766b325f215c79f3737bc9df37c7c952d8ff55`.

#275 added the compact PopupWindow family, compact Quality UI, requested-vs-actual quality verification, and clearer DNS recovery wording without changing resolver/BG/quality policy/share architecture/colors.

Device result:
- **gear menu FAIL**: tapping gear showed only a roughly 2 mm-high rectangle, so no submenu/quality QA could continue;
- treat this as popup clipping/geometry failure, not simply a preference that 42dp rows were too dense;
- likely trigger was `WRAP_CONTENT` PopupWindow height + `showAsDropDown()` from the Media3 controller's bottom row, which clipped to the tiny area below the gear on this device;
- **rare DNS wording PASS-as-designed**: error remained unplayable but became clearer, which is the intended safe improvement when the downstream host is genuinely unresolvable. Do not add host rewriting or bypass behavior.

## Build #278 — popup geometry correction: CI PASS / DEVICE PASS
App commit `8b0566c68eb9082c0aed62e202edfc1a29232983`.
Actions #278 run `31905713180`; build job `95063044270`; artifact `9252287185`.
ZIP SHA-256 `9076f2c5aec6fb830fb0551195a1bd475e54051d0f6c380aa78c6be9504318ec`.
APK size `35,590,609`; APK SHA-256 `ee5893ef22a7a38758293ce9647ac133f09bea8527855c836c8dd65f13ba6043`.

#278 changes only `PlayerChromeProvider.kt` relative to #275 app code:
- row height relaxed from 42dp to 44dp;
- popup height calculated explicitly from row count + insets instead of WRAP_CONTENT;
- popup placed explicitly above the gear using screen coordinates/visible frame instead of relying on `showAsDropDown()` auto-flip;
- bounded below-anchor fallback only when there is truly insufficient space above.

Quality verification, clearer DNS wording, fullscreen, `[tabs][gear][fullscreen]`, controller auto-hide, same-player Audio/Volume/Speed, resolver/BG architecture, share flow and approved palette are unchanged.

### #278 device result — ACCEPTED PLAYER UI BASELINE
User reports **all requested #278 checks worked as expected**. Treat as PASS for:
- gear menu visible at normal height with accepted compactness;
- compact Video Quality submenu and Actual-quality row;
- requested-vs-actual manual quality verification, including the tested 480p path;
- Audio, app-level Volume/Mute, Playback speed and Diagnostics;
- fullscreen, exact control order, tab dashboard, controller auto-hide, double-tap ±10s, no visible ±10 buttons;
- approved colors unchanged.
Exact numeric requested/actual quality values were not separately recorded, but the full #278 checklist was explicitly reported working as expected.

Treat player-control/menu UI as settled unless later regression evidence appears.

## Brave compatibility
Brave Mobile as-is compatibility passed on #264 using generic Android share targets. Do not add Brave-specific architecture unless future regression evidence requires it.

## Stored-for-later backlog
- secure browser-based `Report log on GitHub` shortcut with no embedded reusable credential;
- safe dead/historical code cleanup only after proving unused;
- diagnostics/operations-log noise cleanup;
- About/version/build/README/release-note consistency;
- distribution + permanent signing last, never commit permanent signing material;
- revisit old dedicated “Return to existing Vivaldi task/tab” idea rather than automatically implementing it.

## Next priority
1. Final PH technical regression against accepted #278.
2. Final HH technical regression; known rare downstream DNS failure is a documented source-availability edge case, not a general failure.
3. Re-test both Vivaldi share targets end-to-end and do a small Brave as-is smoke regression.
4. Then hardening/failure edges, diagnostics/log cleanup, safe stale/dead-path cleanup, and About/version/build/docs consistency.
5. Distribution/permanent signing remain final-stage only.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
