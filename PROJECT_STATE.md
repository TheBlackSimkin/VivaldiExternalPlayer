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

## UI structure accepted from Build #242
UI commit `b1772047602a33ec5c50872459715bc28b7fdf8e`; Actions #242 PASS. User reported the requested structure/results as expected:
- loose Vivaldi-inspired thumbnail tabs, 2 columns portrait / 3 landscape;
- no technical lifecycle strings on normal cards;
- dedicated Recently Closed thumbnail grid with fixed Recover all/Delete all;
- grouped Settings with About inside Settings;
- collapsible manual URL;
- player tab-count + gear concept.

## Logo-derived palette — DEVICE PASS on Build #249
Build #249 app/UI commit `cdbd30e01839cb8aa50e3c87d77d1802d04b0a28`; Actions #249 PASS; APK SHA-256 `837457a22956c4c70afc3a9bc9cde82de708086ef31c92cd02ac7bf79757ce1d`.
User feedback: **“colors are perfect, love them”**. Treat this palette as approved/protected for the current UI iteration:
- purple `#B05CFF` = brand/active accent;
- charcoal `#17191F` family = principal surfaces;
- white = primary content/text;
- green/amber/red remain semantic success/attention/destructive colors.
Do not change this palette unless the user asks.

## Player-control specification
- Keep Media3's normal controller, timeline, play/pause, fullscreen and ended-state replay/start-again behavior.
- Do **not** show dedicated visible rewind/fast-forward ±10-second buttons.
- Preserve `GesturePlayerView` left/right double-tap for `-10s / +10s`.
- Exact lower-right order: `[tab count] [gear] [fullscreen]`.
- Tab count opens the dashboard.
- One combined ExternalPlayer gear contains: **Video quality, Audio, Playback speed, Diagnostics**.
- Quality and Diagnostics reuse existing PlayerActivity handlers.
- Audio and Playback speed operate on the same Media3 `Player` exposed by `PlayerView`; never create a second ExoPlayer.
- Tab count + gear are children of Media3's controller so they auto-hide with it, leaving clean video only.
- Preserve Media3 end-of-video replay behavior; do not add a separate permanent restart button.

## Build #249 device feedback — PARTIAL
Passed:
- colors/palette: PASS and strongly approved.
Needs correction:
1. tab-count square + gear were not visually in the requested lower-right corner;
2. hiding Media3's old gear also removed its familiar Audio and Playback speed options because #249's custom gear exposed only Quality + Diagnostics.

Decision:
- stop positioning `[tabs] [gear]` by decor/screen margins;
- physically insert them into Media3's real horizontal control row immediately before fullscreen;
- restore Audio + Playback speed inside the one combined ExternalPlayer gear.

## Build #251 — focused player-chrome correction: CI PASS, DEVICE QA PENDING
App/UI commit `ac06833ab779c5404cdbd20f69dae1edd437e342`.
GitHub Actions run #251 `31866740455`; build job `94968929823` compile PASS and debug artifact upload PASS.
Artifact `9242257590` (`VivaldiExternalPlayer-debug-apk`).
ZIP SHA-256 `2e0d3f42351c44258d07a6e1968e4573eaf8c41911576b29f184d2a1dcbf0362`.
Debug APK size `35,568,537` bytes; APK SHA-256 `e77533748a797c3ab38055d88e8be72714f61ec69fff672329aa20fbedb841a0`.

#251 changes are intentionally narrow:
- approved #249 colors unchanged;
- `PlayerChromeProvider` re-parents the existing tab-count button and one ExternalPlayer gear into Media3's nearest horizontal controller row immediately before `exo_fullscreen`;
- visible rewind/fast-forward controls remain hidden; double-tap seek is unchanged;
- combined gear menu order: Video quality, Audio, Playback speed, Diagnostics;
- Audio chooser enumerates supported Media3 audio tracks and applies/removes `TRACK_TYPE_AUDIO` overrides on the existing Player;
- Playback speed choices: 0.5×, 0.75×, 1×, 1.25×, 1.5×, 1.75×, 2× on the same Player;
- bilingual English/Spanish player-menu strings added;
- no resolver, source-selection, 720-first, private-display BG, palette, or player-creation code changed.

CI compilation/resource linking proves the pinned Media3 version supports the fullscreen/control-row resource and Audio/speed APIs used here. Exact runtime placement and menu behavior still require device QA.

## Current priority
1. Device-check #251 exact lower-right `[tabs] [gear] [fullscreen]` placement.
2. Confirm combined gear contains and operates Video quality, Audio, Playback speed, Diagnostics.
3. Confirm controls still auto-hide cleanly and double-tap ±10s still works.
4. Keep #249 palette unchanged.
5. Continue UI iteration; after UI settles run final PH/HH + both Vivaldi share-target regression, then hardening/docs/version/release work.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
