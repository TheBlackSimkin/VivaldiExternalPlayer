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
- consistent icons/buttons, minimal animation and clean sans-serif typography;
- palette should shift toward the logo identity: purple `#B05CFF`, charcoal `#17191F`, white.

## Build #242 — first UI pass
UI commit `b1772047602a33ec5c50872459715bc28b7fdf8e`; Actions #242 PASS; APK SHA-256 `ac3e04a27525ffe063219da59e9165b26732b3fc834eafedfc08731dd7695838`.
User reports **results as expected** for the requested #242 structure/functionality.

### Current player UI specification
- Tab-count and gear are part of the **Media3/video controller overlay**, not permanently floating over video.
- Both live in the **lower transport-control row**, immediately to the left of fullscreen.
- Conceptual right-side order: `[tab count] [gear] [fullscreen]`.
- Seek bar/timestamps remain on the row above.
- **Do not show dedicated rewind/forward buttons.** Preserve the existing double-tap left/right ±10s behavior instead.
- Controls visible -> normal transport UI + tab count + gear + fullscreen.
- Controls auto-hide -> tab count + gear disappear too; hidden state is clean video only.
- Tap video -> normal controller plus tab-count/gear return together.
- Gear menu contains the existing Quality and Diagnostics actions.
- **Restart/go to start** is not visible during normal playback; it appears only after playback reaches the ended state, as the replay/restart action.
- This behavior is technically feasible using Media3 controller visibility and playback-state callbacks.
- No app-code change for these latest player clarifications yet.

## Next
1. Implement controller-bound lower-row tab-count/gear with no visible ±10s buttons, plus ended-state restart only.
2. Apply logo-derived purple/charcoal/white palette.
3. Continue visual iteration; defer deep PH/HH regression until UI settles.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
