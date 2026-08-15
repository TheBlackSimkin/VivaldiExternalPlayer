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
- consistent icons/button hierarchy, minimal animation and clean system sans-serif typography.

## Build #242 — first broad UI redesign: DEVICE STRUCTURE PASS, VISUAL ITERATION REQUIRED
UI commit `b1772047602a33ec5c50872459715bc28b7fdf8e`; Actions #242 run `31862910307` PASS; artifact `9241146094`.
ZIP SHA-256 `4ddc12ba80d92944ac5006b81e19c39674f19b754985da17492c4508a55f4040`; APK SHA-256 `ac3e04a27525ffe063219da59e9165b26732b3fc834eafedfc08731dd7695838`.

User device result: **all requested #242 structural/UI behaviors worked as expected**. Treat grid/history/settings/player-control wiring as a functional PASS for that iteration.

## Agreed visual/player corrections after #242
### Logo-derived color identity
User wants the UI based more strongly on the launcher/logo colors:
- purple `#B05CFF` = brand/active accent;
- charcoal `#17191F` family = principal surfaces;
- white = primary content/text.
Green/amber/red remain semantic success/attention/destructive colors rather than competing brand accents.

### Final agreed player-control model
- Keep Media3's normal controller, timeline, play/pause, fullscreen and ended-state replay/start-again behavior.
- Do **not** add a separate custom restart button. Media3's existing ended-state replay behavior is the desired functionality.
- Do **not** show dedicated visible rewind/fast-forward ±10-second controls.
- Preserve the existing left/right double-tap gesture for `-10s / +10s`.
- Square open-tab count and ExternalPlayer gear live at the **lower-right of the video controller**, immediately left of fullscreen.
- Conceptual right-side order: `[tab count] [gear] [fullscreen]`.
- Gear menu contains the existing Quality and Diagnostics actions; reuse their current PlayerActivity click handlers.
- Tab count + gear are tied to Media3 controller visibility:
  - controls visible -> tab count + gear visible;
  - controls auto-hide -> both disappear;
  - hidden state -> clean video only;
  - tap video -> normal controller plus tab count/gear return;
  - pause/end -> follow Media3 controller behavior.

## Current implementation — second UI iteration staged, CI pending
The next UI commit is intentionally small and presentation-only:
- `colors.xml` moves the redesigned UI from the old red accent to a purple/charcoal/white logo-derived palette, including a restrained translucent player-control surface;
- new `player_control_button_background.xml` gives tab-count/gear a charcoal translucent surface with a subtle purple outline;
- `PlayerChromeProvider` now:
  - keeps hidden legacy Quality/Diagnostics buttons only as action owners;
  - hides Media3's visible rewind/fast-forward controls while leaving `GesturePlayerView` double-tap seeking untouched;
  - hides Media3's own settings gear to avoid duplicate gear controls;
  - places the existing tab-count and ExternalPlayer gear in the lower-right, before fullscreen;
  - binds their visibility to `PlayerView.ControllerVisibilityListener` so no app chrome remains when Media3 controls hide;
  - does not modify ExoPlayer, resolver code, source selection, quality policy or BG preparation.

This implementation requires GitHub Actions compilation/resource-link verification, followed by focused device visual/player-chrome QA. Deep PH/HH regression remains deferred until the UI settles.

## Current priority
1. Compile the second UI iteration and fix only build/runtime UI issues if necessary.
2. Device-review logo-derived palette and player controller placement/auto-hide/double-tap/end replay behavior.
3. Continue visual iteration based on user feedback.
4. After UI settles: final PH/HH + both Vivaldi share-target regression, hardening, diagnostics cleanup, docs/version/release work.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
