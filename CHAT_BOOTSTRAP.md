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

## UI structure already accepted
Build #242 UI commit `b1772047602a33ec5c50872459715bc28b7fdf8e` passed device structure QA:
- Vivaldi-inspired thumbnail tabs, 2 columns portrait / 3 landscape;
- normal cards omit technical lifecycle strings;
- Recently Closed dedicated thumbnail grid with permanent Recover all / Delete all;
- grouped Settings, About inside Settings;
- collapsible manual URL.

## Palette — approved
Build #249 app/UI commit `cdbd30e01839cb8aa50e3c87d77d1802d04b0a28`; Actions #249 PASS; APK SHA-256 `837457a22956c4c70afc3a9bc9cde82de708086ef31c92cd02ac7bf79757ce1d`.
User feedback: **“colors are perfect, love them”**.
Keep the #249 logo-derived palette unchanged unless explicitly requested:
- purple `#B05CFF` brand/active accent;
- charcoal `#17191F` family surfaces;
- white primary content;
- green/amber/red semantic only.

## Current player UI specification
- Keep Media3 controller/playback/fullscreen/end replay behavior.
- No visible ±10s rewind/forward controls; preserve double-tap left/right seek.
- Exact lower-right order: `[tab count] [gear] [fullscreen]`.
- Tab count opens dashboard.
- One combined gear contains: **Video quality, Audio, Playback speed, Diagnostics**.
- Quality/Diagnostics reuse existing PlayerActivity handlers.
- Audio/Playback speed use the same existing Media3 Player from PlayerView; no second ExoPlayer.
- Tab count + gear must be actual children of Media3's controller so they auto-hide with it and clean video remains when controls hide.
- Preserve existing end-of-video replay/start-again behavior; no custom permanent restart control.

## Build #249 device feedback
- Colors: PASS, strongly approved.
- Player chrome: PARTIAL.
  1. tab square + gear were not visually in the requested corner;
  2. #249 hid Media3's old settings gear, so its Audio and Playback speed entries disappeared; ExternalPlayer's custom gear only had Quality + Diagnostics.

## Focused correction now staged
- Keep colors unchanged.
- Re-parent the existing tab button and one ExternalPlayer gear into Media3's actual horizontal control row immediately before `exo_fullscreen` rather than using screen/decor margins.
- Hide visible Media3 rewind/fast-forward buttons, preserving GesturePlayerView double-tap seeking.
- Combined gear order: Video quality, Audio, Playback speed, Diagnostics.
- Audio chooser lists supported Media3 audio tracks and applies/removes audio overrides on the same Player.
- Playback speed choices: 0.5×, 0.75×, 1×, 1.25×, 1.5×, 1.75×, 2× on the same Player.
- Add bilingual English/Spanish strings for these restored options.
- No resolver/BG/quality/palette changes.

Next: atomic commit -> GitHub Actions compile -> exact player-chrome device QA. Defer deep PH/HH regression until UI settles.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
