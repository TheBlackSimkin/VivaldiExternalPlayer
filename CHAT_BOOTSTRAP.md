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
Quality policy: exact 720p -> 1080p -> highest below 1080p -> rare smallest >1080p fallback.

#234 established the protected BG architecture: short share Activity -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct resolver -> serialized browser fallback. No preparation Activity on display 0 and no ExoPlayer during preparation.

#236 app code `d6c1328823ce2027beecab7970b02420d1cffc7b` is the protected playback baseline. Device QA PASS:
- PH BG/Vivaldi responsiveness;
- Auto 720-first;
- manual quality including 480p;
- PH playback;
- HH technical smoke on unchanged binary;
- Recently Closed functionality;
- language persistence.
Do not change BG/quality architecture without a concrete regression.

## User-approved UI direction
- Vivaldi-inspired tab distribution only as loose interaction/layout inspiration;
- thumbnail tab grid: 2 columns portrait, 3 landscape;
- no technical lifecycle text in normal tab cards;
- Recently Closed as a dedicated thumbnail grid with permanent Recover all / Delete all;
- grouped visual Settings, About inside Settings;
- collapsible secondary manual URL section;
- deliberate empty/loading/error states;
- player Quality + Diagnostics inside gear menu; square tab-count button directly left of gear;
- cleaner quality/browser resolver/diagnostics/About styling;
- consistent button hierarchy/icons/dark graphite palette/minimal animation;
- Android system sans-serif / sans-serif-medium typography;
- tall layouts are acceptable.

## Build #242 — UI implementation, CI PASS / DEVICE QA PENDING
UI commit `b1772047602a33ec5c50872459715bc28b7fdf8e`; Actions #242 run `31862910307` PASS; artifact `9241146094`.
ZIP SHA-256 `4ddc12ba80d92944ac5006b81e19c39674f19b754985da17492c4508a55f4040`.
APK size `35,565,818`; APK SHA-256 `ac3e04a27525ffe063219da59e9165b26732b3fc834eafedfc08731dd7695838`.

The implementation is UI-only and preserves resolver/BG/playback behavior. Changed areas include MainActivity, TabDashboardAdapter, grouped Settings/About, dedicated RecentlyClosedActivity/adapter, UI-only PlayerChromeProvider, main/player/browser layouts, manifest and new bilingual strings/icons/shapes/theme tweaks. Thumbnail files survive normal close while their tab remains in Recently Closed and are removed when no open/recent tab references the ID.

Important runtime QA points:
- #242 compiled, but the player chrome needs device confirmation that the square count appears immediately left of the gear and that the gear successfully invokes existing Quality and Diagnostics handlers;
- main grid should be 2 columns portrait / 3 landscape;
- normal tab cards should have no `tech ...` lifecycle strings;
- Recently Closed should show cached thumbnails and fixed Recover all/Delete all actions;
- user wants to judge the visual direction and propose changes after seeing this build.

Next: install/test #242 for visual/usability QA plus a light player-control sanity check. Do not run a deep PH/HH regression until UI iteration settles.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
