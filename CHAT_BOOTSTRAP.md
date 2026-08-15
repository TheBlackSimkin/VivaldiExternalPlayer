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
Build #242 UI commit `b1772047602a33ec5c50872459715bc28b7fdf8e` compiled and the user reported requested structure/results as expected:
- loose Vivaldi-inspired thumbnail tabs, 2 columns portrait / 3 landscape;
- no technical lifecycle strings on normal cards;
- dedicated Recently Closed grid with thumbnails and fixed Recover all/Delete all;
- grouped Settings with About inside Settings;
- collapsible manual URL;
- square tab count + gear concept on player.

## Latest user-approved visual/player specification
### Palette
Use the logo identity rather than #242's red-heavy accent:
- purple `#B05CFF` = brand/active accent;
- charcoal `#17191F` family = surfaces;
- white = primary content/text;
- green/amber/red stay semantic success/attention/destructive colors.

### Player controls
- Keep Media3 controller/playback behavior.
- No visible rewind/fast-forward ±10s buttons.
- Preserve GesturePlayerView double-tap left/right for `-10s / +10s`.
- Lower-right controller order is conceptually `[tab count] [gear] [fullscreen]`.
- Tab count opens dashboard; gear contains existing Quality + Diagnostics actions.
- Tab count/gear follow Media3 controller visibility: when controls hide they disappear too, leaving clean video only; tapping video brings them back with the controller.
- Preserve current end-of-video replay/start-again behavior from Media3. Do not add a separate custom restart button.

## Current implementation — second UI iteration staged, CI pending
Presentation-only changes staged on top of current main:
- `colors.xml`: logo-derived purple/charcoal/white palette;
- new translucent purple-outlined player control background;
- `PlayerChromeProvider`: hides visible Media3 rewind/fast-forward buttons and duplicate Media3 settings gear, moves the existing tab-count and ExternalPlayer gear to the lower-right before fullscreen, and binds both to `PlayerView.ControllerVisibilityListener`;
- existing hidden Quality/Diagnostics button click handlers are still reused;
- resolver/BG/quality/ExoPlayer architecture is untouched.

Next: atomic commit -> GitHub Actions compile -> focused device UI/player-chrome QA. Defer deep PH/HH regression until UI iteration settles.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
