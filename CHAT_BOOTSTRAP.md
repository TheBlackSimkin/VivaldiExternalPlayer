# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` before substantive work. Keep both state files current whenever QA, architecture, failures, priorities, or decisions change.

## Safety / communication
- Explain plainly; user is not an advanced developer. Use connected GitHub directly. Keep source well-commented in English.
- PH/HH are technical playback targets. Technical URLs/manifests/codecs/resolutions/request metadata/candidate ranking/states/errors/local titles are allowed.
- Never inspect/describe/classify PH/HH media content or thumbnail imagery and never ask the user to provide it.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials.
- Never add background playback or a second ExoPlayer session.

## Protected playback baseline
#234 private-display service architecture is protected. #236 app code `d6c1328823ce2027beecab7970b02420d1cffc7b` is the protected playback baseline and passed PH BG, Auto 720-first, manual quality including 480p, PH playback, HH smoke, Recently Closed functionality and language persistence. Do not change BG/quality architecture without a concrete regression.

## Accepted UI structure
#242 structure/device PASS: thumbnail grid 2 portrait / 3 landscape, no technical card text, Recently Closed thumbnail grid with Recover all/Delete all, grouped Settings/About, collapsible manual URL.

## Palette — approved
#249 app/UI commit `cdbd30e01839cb8aa50e3c87d77d1802d04b0a28`; user: **“colors are perfect, love them”**.
Keep unchanged unless requested: purple `#B05CFF`, charcoal `#17191F` family, white primary content; green/amber/red semantic only.

## Player UI specification
- Keep Media3 controller/playback/fullscreen/end replay behavior.
- No visible ±10s buttons; double-tap left/right remains ±10s.
- Exact lower-right order: `[tab count] [gear] [fullscreen]`.
- One gear contains **Video quality, Audio, Playback speed, Diagnostics**.
- Quality/Diagnostics reuse existing PlayerActivity handlers.
- Audio/speed use the same Media3 Player; never create a second ExoPlayer.
- Tabs/gear are actual children of Media3 controller and auto-hide with it.
- No custom permanent restart button; preserve Media3 end replay.

## #249 device feedback
- Colors PASS/perfect.
- Player chrome PARTIAL: tab square/gear not in requested corner; Audio + Playback speed disappeared when old Media3 gear was hidden.

## Build #251 — focused correction: CI PASS / DEVICE QA PENDING
App/UI commit `ac06833ab779c5404cdbd20f69dae1edd437e342`.
Actions run #251 `31866740455`; job `94968929823` compile + debug upload PASS.
Artifact `9242257590`.
ZIP SHA-256 `2e0d3f42351c44258d07a6e1968e4573eaf8c41911576b29f184d2a1dcbf0362`.
APK size `35,568,537`; APK SHA-256 `e77533748a797c3ab38055d88e8be72714f61ec69fff672329aa20fbedb841a0`.

#251:
- keeps #249 colors unchanged;
- re-parents existing tab count + one ExternalPlayer gear into Media3's actual horizontal row immediately before `exo_fullscreen`;
- visible seek buttons stay hidden; double-tap unchanged;
- combined gear: Video quality, Audio, Playback speed, Diagnostics;
- Audio uses supported `TRACK_TYPE_AUDIO` tracks/overrides on the existing Player;
- speed choices 0.5× through 2× on the same Player;
- bilingual strings added;
- resolver/BG/720-first/player-creation architecture untouched.

## #251 early device feedback — PARTIAL, no app-code patch yet
- Android Recents currently exposes the tab dashboard preview. Desired QoL/privacy behavior: hide the dashboard from Recents similarly to player mode, preferably with Recents-only screenshot suppression where supported rather than unnecessarily blocking all screenshots.
- Fullscreen button is missing. Code audit: #251 uses `exo_fullscreen` as the row anchor but `PlayerActivity` does not register Media3's fullscreen-button listener, so restoring a functional Media3 fullscreen control is a focused player-chrome fix.
- User prefers the compact original Media3-style settings experience rather than centered modal option windows. Keep one combined gear with Quality, Audio, Playback speed, Diagnostics, but prefer compact anchored menus/submenus.
- Overnight saved-tab failure exposed a stale-source case: `Retry` currently retries the already-resolved stream URL, which may have expired; returning to old detected candidates may be equally stale.
- `VideoTabStore.VideoTab` already persists the original page URL as `sourceUrl` separately from `resolvedMediaJson`. A new recovery action can re-run normal preparation from `sourceUrl` and repair the same tab without changing the protected resolver/BG/quality architecture.
- Proposed distinction: **Retry playback** = retry same resolved stream for short transient failures; **Refresh source** (working name) = re-resolve original page URL, replace stale resolved data in the same tab, and preserve saved position where possible.
- Exact overnight Media3 error was not saved, so do not over-classify that incident.

## #251 preliminary working checks — CONTINUE TESTING
Working well so far, but not final sign-off yet: tab-count placement; tab count opens dashboard; Audio; Playback speed; Video quality; Diagnostics; complete controller auto-hide; double-tap ±10s; no visible ±10s buttons; Media3 end replay; approved #249 colors.

Next APK scope: Recents privacy, functional fullscreen, compact Media3-like gear presentation, and fresh-source recovery for stale saved tabs. Preserve the currently working #251 behaviors above. Deep PH/HH regression remains deferred until UI settles.

## Later-stage roadmap after UI settles
- full PH technical regression;
- full HH technical regression;
- both Vivaldi share-target regressions;
- general hardening;
- diagnostics cleanup;
- version/release preparation;
- permanent signing/release work only at the final release stage.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
