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

## UI direction
Approved structural direction:
- Vivaldi-inspired tab distribution only as loose layout inspiration;
- thumbnail tabs: 2 columns portrait / 3 landscape;
- normal cards omit technical lifecycle strings;
- Recently Closed dedicated thumbnail grid with permanent Recover all / Delete all;
- grouped Settings, About inside Settings;
- collapsible manual URL;
- deliberate state/empty/error UI;
- square tab count + player gear with Quality + Diagnostics inside gear;
- consistent icons/buttons, minimal animation and clean sans-serif typography.

## Build #242 — first UI pass
UI commit `b1772047602a33ec5c50872459715bc28b7fdf8e`; Actions #242 PASS; APK SHA-256 `ac3e04a27525ffe063219da59e9165b26732b3fc834eafedfc08731dd7695838`.
User reports **results as expected** for the requested #242 structure/functionality.

### Current user feedback / correction
- Palette: user wants stronger use of the actual logo colors. Launcher mark uses purple `#B05CFF`, charcoal `#17191F`, and white. Next UI iteration should explore that identity instead of #242's red-heavy accent.
- Player: tab-count and gear are part of the **video controller overlay visibility**, not permanently floating over video.
- Placement is now explicit from the user's approved wireframe:
  - both controls live in the **lower transport-control row**;
  - they sit immediately to the **left of fullscreen**;
  - conceptual right-side order is `[tab count] [gear] [fullscreen]`;
  - rewind/seek-back and seek-forward remain earlier in that row;
  - seek bar and timestamps remain on the row above;
  - controls visible -> transport UI + tab-count + gear visible;
  - controls auto-hide -> tab-count + gear disappear too;
  - hidden state -> clean video only;
  - tap video -> normal controller plus tab-count/gear return together;
  - gear menu contains existing Quality and Diagnostics actions.
- This layout is technically feasible with Media3 controller visibility. No app-code change for this clarification yet.

## Next
1. Treat the lower-row wireframe as the approved player placement concept.
2. Implement controller-bound lower-row tab-count/gear + logo-derived purple palette.
3. Continue visual iteration; defer deep PH/HH regression until UI settles.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
