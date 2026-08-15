# Vivaldi External Player — Project State

GitHub `main` is authoritative. Keep this file and `CHAT_BOOTSTRAP.md` current whenever requirements, architecture, QA results, failures, decisions, or priorities change.

## Working / safety rules
- Conversation English; Android UI bilingual (English/Spanish). Explain plainly; user is not an advanced developer.
- Use connected GitHub tools directly. Source should contain abundant English comments.
- PH and HH are technical playback targets. URLs/manifests/codecs/resolutions/request metadata/candidate ranking/playback states/errors/local titles are allowed.
- Do **not** inspect, describe, classify, summarize, or request PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials. Conservative automation may only handle clearly identified age/18+ and cookie prompts.
- Never add background playback or a second ExoPlayer session.

## Protected playback baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise highest below 1080p; >1080 only rare fallback.
Preserve Vivaldi share targets; yt-dlp first/browser fallback; automatic/manual quality; video+audio; adaptive/sibling switching; double-tap ±10s; seek preview; rotation; bilingual UI; candidate limits/order; page-config families; no imagery-based ranking; exactly one actual ExoPlayer playback session.
Permanent release signing remains deferred. Debug GitHub Actions APKs are the QA path; never commit a permanent signing key.

## Protected BG architecture
#234 established the working service-owned private-display path after #205/#212/#227 failures:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY/ERROR/NEEDS_ATTENTION`.
No preparation Activity on display 0 and no PlayerActivity/Media3/ExoPlayer during preparation. Do not change this architecture without a concrete regression.

## Build #236 — protected playback baseline: DEVICE PASS
App-code commit `d6c1328823ce2027beecab7970b02420d1cffc7b`; APK SHA-256 `ca24f6943849853d4ba6580ceaf107b9795ebc8b943dc55ac28cdab66b8c3bff`.
Device QA PASS for PH BG/Vivaldi responsiveness, Auto 720-first, manual quality including 480p, playback sanity, HH technical smoke, Recently Closed functionality and language persistence.

## UI redesign direction
User-approved direction:
- loose Vivaldi-inspired tab distribution, not a visual clone;
- thumbnail-first tabs, 2 columns portrait / 3 landscape;
- no technical lifecycle text in normal cards; keep it in diagnostics/logs;
- Recently Closed as a dedicated thumbnail grid with fixed Recover all / Delete all;
- grouped visual Settings; About inside Settings;
- collapsible secondary manual URL section;
- intentional empty/loading/ready/error/browser-step states;
- player tab-count + gear controls, with Quality + Diagnostics inside the gear menu;
- consistent icons/button hierarchy, minimal animation and clean system sans-serif typography.

## Build #242 — first broad UI redesign: DEVICE STRUCTURE PASS, VISUAL ITERATION REQUIRED
UI commit `b1772047602a33ec5c50872459715bc28b7fdf8e`; Actions #242 run `31862910307` PASS; artifact `9241146094`.
ZIP SHA-256 `4ddc12ba80d92944ac5006b81e19c39674f19b754985da17492c4508a55f4040`; APK SHA-256 `ac3e04a27525ffe063219da59e9165b26732b3fc834eafedfc08731dd7695838`.

User device result: **all requested #242 structural/UI behaviors worked as expected**. Treat the grid/history/settings/player-control wiring as a functional PASS for this iteration.

### User visual feedback after #242
1. **Color direction needs revision.** User prefers the UI to be based more strongly on the launcher/logo identity. Current logo palette is:
   - purple accent `#B05CFF`;
   - charcoal `#17191F`;
   - white `#FFFFFF`.
   The next visual iteration should move away from the red-heavy #242 accent system and explore a purple/charcoal/white palette derived from the logo.

2. **Player control layout is now explicitly defined.** Quality and Diagnostics live in the **player gear menu that is part of the Media3/video controller overlay**. The tab-count square belongs to that same controller visibility lifecycle.
   - **Placement:** tab count and gear live in the lower transport-control area immediately to the left of Media3's fullscreen control.
   - Conceptual right-side order: `[tab count] [gear] [fullscreen]`.
   - Seek bar/timestamps remain on the row above.
   - **Do not show dedicated seek-back/seek-forward buttons.** Keep the existing double-tap left/right gesture for ±10 seconds instead, preserving a cleaner transport row.
   - When controller controls are visible: show tab-count + gear + fullscreen with the normal transport UI.
   - When controls auto-hide: tab-count and gear disappear too, leaving a clean video surface with no persistent app chrome.
   - Tapping video restores the controller overlay and these controls together.
   - Gear menu contains existing Quality and Diagnostics actions; reuse existing behavior rather than reimplement it.
   - A **restart/go-to-beginning** action should not be shown during normal playback. It should appear only after the video reaches the ended state, acting as a replay/restart control.

This layout/behavior is technically feasible with Media3 controller visibility and playback-state callbacks. No app-code change has been made for this clarification yet.

## Current priority
1. Treat the user's lower-row player layout and hidden-seek-button behavior as approved.
2. Implement the logo-derived purple palette and controller-bound lower-row tab-count/gear behavior, preserving double-tap ±10s and adding restart only in ended state.
3. Continue visual iteration based on user feedback; do not run deep PH/HH regression until UI settles.
4. Then final PH/HH + both Vivaldi share-target regression, hardening, diagnostics cleanup, docs/version/release work.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
