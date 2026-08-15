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
- Quality/Diagnostics reuse existing PlayerActivity handlers.
- Audio, Volume/Mute and speed use the same Media3 Player; never create a second ExoPlayer.
- Volume/Mute is app/player-relative and must not change Android global media volume.
- Tabs/gear are actual children of Media3 controller and auto-hide with it.
- Preserve Media3 natural end replay; no custom permanent restart button.

## #251 preliminary device state
Build #251 app/UI commit `ac06833ab779c5404cdbd20f69dae1edd437e342`; Actions #251 PASS.
Working well under continued testing: tab-count placement/dashboard; Audio; Playback speed; Video quality; Diagnostics; full controller auto-hide; double-tap ±10s; no visible ±10s buttons; Media3 end replay; approved colors.

Findings which motivated the next APK: dashboard appeared in Android Recents; fullscreen button missing; Audio/Speed option dialogs too modal; overnight/stale resolved stream could expire and Retry could not obtain a fresh URL.

## Build #264 — focused UI/recovery + Volume/Mute: CI PASS / DEVICE QA PENDING
Final app-code commit `5b1906f1d43643a46458a77e2de67691c1f299c0`.
Actions Build #264 run `31900203463`; job `95049647214`; compile + debug upload PASS.
Artifact `9250881808` (`VivaldiExternalPlayer-debug-apk`).
ZIP size `26,053,952`; ZIP SHA-256 `26f3309098377d41fabdaad77b42033672999e5099bf154af13c4382e8bb8232`.
APK size `35,570,425`; APK SHA-256 `d110c4257f9d44c47820ac15627c7a577a0d54cc3f7a2a52dcf42f65e56784e0`.

#264 changes:
- Android 13+ dashboard uses Recents-only snapshot suppression; ordinary dashboard screenshots are not intentionally blocked.
- Media3 fullscreen listener restored; fullscreen hides/shows system bars using the existing player/activity and leaves normal rotation support intact.
- controller order stays `[tabs] [gear] [fullscreen]`; visible ±10 buttons remain hidden.
- combined gear now: Video quality, Audio, Volume / mute, Playback speed, Diagnostics.
- Audio and Playback speed use compact anchored popup submenus.
- new app-level Volume/Mute uses the same Player with Mute/Unmute and 25/50/75/100%; it does not change device global volume.
- stale-source recovery distinguishes **Retry playback** from **Refresh source**.
- Refresh source preserves same tab ID/position, avoids re-persisting stale resolved data, and starts `BackgroundPreparationKeepAliveService.acquire(...)` for the same `sourceUrl`/tab through the protected #234 private-display path.
- resolver ranking, 720-first policy, private-display architecture, palette and player creation were not changed.

## Brave QA decision
User wants to test whether the existing app works with **Brave Mobile as-is** on the unchanged #264 APK.
- Do not add Brave-specific code before testing.
- Test the existing Android share targets `ExternalPlayer` and `BG - External Player` from Brave.
- If it passes, record compatibility without special Brave architecture.
- If it fails, capture the technical handoff/preparation behavior first, then decide on any focused compatibility fix.
- Vivaldi remains the primary protected regression baseline.

## Stored-for-later backlog — keep explicit
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials;
- safe stale/dead historical code cleanup only after proving paths unused;
- operations-log/diagnostics noise cleanup;
- About/version/build/README/release-note consistency;
- final distribution + permanent signing decision; never commit permanent signing material;
- revisit, rather than automatically implement, the old dedicated “Return to existing Vivaldi task/tab” idea.

Promoted from this backlog: app-level Volume/Mute is now in #264; Brave evaluation is now #264 compatibility QA.

## Next priority
1. Device-test #264 focused UI/recovery changes and ensure the previously working #251 behaviors/colors remain intact.
2. On unchanged #264, perform Brave as-is share compatibility smoke test.
3. No unrelated redesign or resolver/BG/quality changes during this QA.
4. If UI is accepted and Brave passes, UI can be considered settled unless user requests more changes.
5. Then final PH regression, HH regression, both Vivaldi share-target regressions, hardening/diagnostics cleanup and release-readiness/stored-for-later work.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
