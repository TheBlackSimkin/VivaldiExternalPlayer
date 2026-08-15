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

#236 app code `d6c1328823ce2027beecab7970b02420d1cffc7b` is the current playback baseline. Device QA PASS:
- PH BG/Vivaldi responsiveness;
- Auto 720-first;
- manual quality including 480p;
- PH playback;
- HH technical smoke on unchanged binary;
- Recently Closed functionality;
- language persistence.
Do not change BG/quality architecture without a concrete regression.

## Current task: user-approved UI redesign
User explicitly asked to move into UI improvements and approved this direction:
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

Implementation is UI-only and must preserve resolver/BG/playback behavior. Implemented files include MainActivity, TabDashboardAdapter, Settings/About, dedicated RecentlyClosedActivity/adapter, PlayerChromeProvider, main/player/browser layouts, manifest, new bilingual strings/icons/shapes/theme tweaks. Thumbnail files survive normal close while their tab remains in Recently Closed and are removed when no open/recent tab references the ID.

Next: let GitHub Actions compile, fix any CI errors, download installable APK, then ask visual/device QA using the exact two-code-block QA format.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
