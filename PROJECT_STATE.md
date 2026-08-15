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
UI commit `b1772047602a33ec5c50872459715bc28b7fdf8e`; Actions #242 PASS. User accepted:
- loose Vivaldi-inspired thumbnail tabs, 2 columns portrait / 3 landscape;
- no technical lifecycle strings on normal cards;
- dedicated Recently Closed thumbnail grid with fixed Recover all/Delete all;
- grouped Settings with About inside Settings;
- collapsible manual URL;
- player tab-count + gear concept.

## Logo-derived palette — DEVICE PASS on Build #249
Build #249 app/UI commit `cdbd30e01839cb8aa50e3c87d77d1802d04b0a28`; Actions #249 PASS; APK SHA-256 `837457a22956c4c70afc3a9bc9cde82de708086ef31c92cd02ac7bf79757ce1d`.
User feedback: **“colors are perfect, love them”**. Treat this palette as approved/protected:
- purple `#B05CFF` = brand/active accent;
- charcoal `#17191F` family = principal surfaces;
- white = primary content/text;
- green/amber/red = semantic success/attention/destructive only.
Do not change this palette unless the user asks.

## Player-control specification
- Keep Media3's normal controller, timeline, play/pause, fullscreen and ended-state replay/start-again behavior.
- Do **not** show dedicated visible rewind/fast-forward ±10-second buttons.
- Preserve `GesturePlayerView` left/right double-tap for `-10s / +10s`.
- Exact lower-right order: `[tab count] [gear] [fullscreen]`.
- Tab count opens the dashboard.
- One combined ExternalPlayer gear contains: **Video quality, Audio, Volume / mute, Playback speed, Diagnostics**.
- Quality and Diagnostics reuse existing PlayerActivity handlers.
- Audio, app-level Volume/Mute and Playback speed operate on the same Media3 `Player` exposed by `PlayerView`; never create a second ExoPlayer.
- App-level Volume/Mute changes only ExternalPlayer's Player volume relative to Android system media volume; it must not change global device volume.
- Tab count + gear are children of Media3's controller so they auto-hide with it, leaving clean video only.
- Preserve Media3 end-of-video replay behavior; do not add a separate permanent restart button.

## Build #251 — focused player-chrome correction: CI PASS / preliminary device checks
App/UI commit `ac06833ab779c5404cdbd20f69dae1edd437e342`; Actions #251 run `31866740455`; artifact `9242257590`; APK SHA-256 `e77533748a797c3ab38055d88e8be72714f61ec69fff672329aa20fbedb841a0`.

Preliminary #251 device checks reported working well:
1. tab count positioned properly beside the player controls;
2. tab count opens dashboard;
3. Audio works on the existing single ExoPlayer session;
4. Playback speed works;
5. Video quality works;
6. Diagnostics works;
7. controller auto-hide removes all visible controls;
8. double-tap left/right remains -10s/+10s;
9. no visible dedicated ±10s buttons;
10. Media3 natural end replay remains;
11. approved #249 colors remain unchanged.

Additional #251 findings requiring a follow-up:
- dashboard image appeared in Android Recents;
- fullscreen button was missing because no fullscreen listener was registered;
- Audio/Speed submenus were centered dialogs rather than the preferred compact Media3-like presentation;
- a saved tab could fail after its old resolved stream URL expired; Retry only retried the stale resolved URL.

## Build #264 — focused UI/recovery + Volume/Mute: CI PASS / DEVICE QA PENDING
Final app-code commit: `5b1906f1d43643a46458a77e2de67691c1f299c0`.
GitHub Actions Build #264 run `31900203463`; build job `95049647214`.
Compile/assemble: PASS. Debug APK upload: PASS.
Artifact `9250881808` (`VivaldiExternalPlayer-debug-apk`).
Artifact ZIP size `26,053,952` bytes; ZIP SHA-256 `26f3309098377d41fabdaad77b42033672999e5099bf154af13c4382e8bb8232`.
Extracted debug APK size `35,570,425` bytes; APK SHA-256 `d110c4257f9d44c47820ac15627c7a577a0d54cc3f7a2a52dcf42f65e56784e0`.

#264 is intentionally narrow and does **not** change resolver ranking, quality policy, private-display BG preparation, accepted palette, or PlayerActivity's one-ExoPlayer ownership.

### #264 player/UI changes
- On Android 13+, MainActivity uses Recents-only screenshot suppression so the tab dashboard should not appear in Overview/Recents; ordinary dashboard screenshots are not intentionally disabled.
- Registers Media3's fullscreen callback and toggles system bars while using the existing PlayerActivity/player. Rotation behavior remains unchanged.
- Keeps exact controller order `[tab count] [gear] [fullscreen]`.
- Keeps visible ±10s buttons hidden; double-tap ±10s remains unchanged.
- Combined gear is now: **Video quality, Audio, Volume / mute, Playback speed, Diagnostics**.
- Audio and Playback speed submenus are compact anchored popup menus instead of centered option dialogs.
- New app-level Volume/Mute submenu operates on the same existing Media3 Player with Mute/Unmute and 25/50/75/100% choices. It does not modify Android's global media volume.
- Approved #249 colors are unchanged.

### #264 stale-source recovery
Recovery now distinguishes:
- **Retry playback** = prepare/retry the current already-resolved stream URL, useful for a short transient failure;
- **Refresh source** = re-resolve the original persisted page `sourceUrl` and repair the **same tab** when an old stream URL has expired.

Refresh source:
- preserves existing tab ID and saved playback position/play intention where practical;
- prevents PlayerActivity's pause-time persistence from writing the stale resolved payload back to READY;
- marks the same tab queued and starts `BackgroundPreparationKeepAliveService.acquire(...)` with that tab ID/source URL;
- therefore reuses the protected #234 service-owned private-display `Presentation/WebView` architecture rather than the older display-0 retry Activity;
- creates no duplicate tab and no second ExoPlayer.

CI proves the new Android/Media3 APIs and resources compile against the project's pinned dependency/SDK baseline. Runtime behavior still needs device QA.

## Brave decision for #264 QA
The user wants to test whether ExternalPlayer already works with **Brave Mobile as-is**.
- Do **not** add Brave-specific code before this compatibility test.
- Use the existing Android `ACTION_SEND text/plain` share targets (`ExternalPlayer` and `BG - External Player`) exactly as currently implemented.
- If Brave passes, record compatibility without creating a special Brave architecture.
- If Brave fails, capture the precise technical handoff/preparation behavior first and only then decide whether a focused compatibility change is justified.
- Vivaldi remains the protected primary regression baseline.

## Recovered “store for later” backlog from project history
Keep these items visible so they are not accidentally compressed out again.

### Still genuinely deferred
- **Secure `Report log on GitHub` shortcut.** Keep ordinary full log sharing; later add a browser-based GitHub issue/report path without embedding a PAT, OAuth secret, repository write token, or other reusable credential in the APK.
- **Stale historical/dead-path cleanup.** Remove obsolete Activity/provider/legacy preparation paths only after proving they are unused, and never disturb protected #234/#236 behavior merely for cosmetic cleanup.
- **Operations-log / diagnostics noise cleanup.** Keep useful technical evidence while removing obsolete, duplicate, or confusing development-only noise.
- **About/version/build/documentation consistency.** Finalize version/build display, About information, README/state docs and release notes during release preparation.
- **Release distribution + permanent signing decision.** Debug Actions APKs remain the QA route. Permanent signing/distribution stays last-stage work; never commit a permanent private signing key.

### Historical deferred idea to revisit, not automatically implement
- **Dedicated “Return to existing Vivaldi task/tab” action.** This was an explicit early requirement. Later Back-flow QA and the persistent dashboard may have partly superseded it. Revisit as a UX decision rather than silently adding a new button.

### Deferred items now promoted/completed
- **App-level Volume/Mute**: promoted into Build #264; device QA pending.
- **Brave Mobile evaluation**: promoted into Build #264 compatibility QA; no Brave-specific code added.
- multi-video tabs/per-tab titles, persistent tabs, Recently Closed, automatic selection/manual fallback, playback speed, language selector/localization, launcher/logo refinement and current loading/UI work are already implemented/substantially absorbed.

## Current priority
1. Device-test Build #264's focused changes: Recents privacy, fullscreen, compact settings, Volume/Mute and stale-source Refresh source while preserving the eleven #251 working behaviors and #249 colors.
2. On the same unchanged #264 APK, perform a small **Brave as-is compatibility smoke test** of the existing Android share targets. Do not add Brave-specific code first.
3. Do not redesign unrelated UI or touch resolver/BG/720-first architecture during this test cycle.
4. If #264 UI/recovery is accepted and Brave works as-is, consider UI settled unless the user requests more changes.
5. After UI settles: final PH technical regression, HH technical regression, both Vivaldi share-target regressions, then hardening/diagnostics cleanup and release-readiness/stored-for-later work.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
