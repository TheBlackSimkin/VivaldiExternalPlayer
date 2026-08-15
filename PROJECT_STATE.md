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
- Quality and Diagnostics reuse existing PlayerActivity behavior unless intentionally refactored without changing playback logic.
- Audio, app-level Volume/Mute and Playback speed operate on the same Media3 `Player` exposed by `PlayerView`; never create a second ExoPlayer.
- App-level Volume/Mute changes only ExternalPlayer's Player volume relative to Android system media volume; it must not change global device volume.
- Tab count + gear are children of Media3's controller so they auto-hide with it, leaving clean video only.
- Preserve Media3 end-of-video replay behavior; do not add a separate permanent restart button.

## Build #251 — focused player-chrome correction: CI PASS / preliminary device checks
App/UI commit `ac06833ab779c5404cdbd20f69dae1edd437e342`; Actions #251 run `31866740455`; artifact `9242257590`; APK SHA-256 `e77533748a797c3ab38055d88e8be72714f61ec69fff672329aa20fbedb841a0`.

Preliminary #251 device checks reported working well: tab-count placement/dashboard; Audio; Playback speed; Video quality UI access; Diagnostics; controller auto-hide; double-tap ±10s; no visible ±10s buttons; Media3 natural end replay; approved colors.

Additional #251 findings motivated #264: dashboard in Android Recents; fullscreen button missing; Audio/Speed submenus too modal; stale resolved stream could expire and Retry could not obtain a fresh URL.

## Build #264 — focused UI/recovery + Volume/Mute: CI PASS / DEVICE QA MOSTLY PASS
Final app-code commit: `5b1906f1d43643a46458a77e2de67691c1f299c0`.
GitHub Actions Build #264 run `31900203463`; build job `95049647214`.
Compile/assemble: PASS. Debug APK upload: PASS.
Artifact `9250881808` (`VivaldiExternalPlayer-debug-apk`).
Artifact ZIP size `26,053,952` bytes; ZIP SHA-256 `26f3309098377d41fabdaad77b42033672999e5099bf154af13c4382e8bb8232`.
Extracted debug APK size `35,570,425` bytes; APK SHA-256 `d110c4257f9d44c47820ac15627c7a577a0d54cc3f7a2a52dcf42f65e56784e0`.

#264 intentionally did **not** change resolver ranking, quality policy, private-display BG preparation, accepted palette, or PlayerActivity's one-ExoPlayer ownership.

### #264 implementation
- Android 13+ MainActivity Recents-only snapshot suppression.
- Functional Media3 fullscreen callback using the same PlayerActivity/player.
- Exact controller order `[tab count] [gear] [fullscreen]`.
- Visible ±10s buttons hidden; double-tap ±10s unchanged.
- Combined gear: **Video quality, Audio, Volume / mute, Playback speed, Diagnostics**.
- Audio, Volume/Mute and Playback speed use anchored PopupMenus.
- App-level Volume/Mute changes only the same Media3 Player volume, not Android global media volume.
- Stale-source recovery distinguishes **Retry playback** from **Refresh source**; Refresh source preserves same tab ID/position and reuses the protected #234 service-owned private-display preparation path.
- Approved #249 colors unchanged.

### #264 real-device results received
User reports **almost all #264 tests succeeded**, including the Brave-as-is compatibility test. Treat these as successful unless a later regression contradicts them:
- Recents/privacy and fullscreen follow-up behavior;
- combined gear functions including Audio, Volume/Mute, Playback speed and Diagnostics;
- compact popup presentation for Audio/Volume/Speed;
- established controller behavior, gestures and palette;
- existing Vivaldi flow remained healthy;
- **Brave Mobile works with the existing generic Android share flow without Brave-specific code**.

User also tried an additional unrelated site outside the PH/HH test scope and reported the generic flow worked there too. Do not turn that incidental smoke result into a new protected site-specific architecture; it simply supports keeping the browser handoff generic.

### #264 remaining quality/UI findings
Two quality-related items are **not accepted yet**:
1. **Video Quality still opens as a centered AlertDialog/window.** Code audit confirms the combined gear still handles Quality by `qualityButton.performClick()`, which invokes PlayerActivity's existing `showQualityDialog()`. Browser adaptive, browser sibling-variant and yt-dlp quality pickers are still AlertDialogs. Therefore the device result is expected from current code, not a device anomaly.
2. **Selecting 480p still does not produce an obvious visible change.** Do not mark manual 480p as verified on #264. Current code changes the requested browser track/source or re-runs yt-dlp depending on resolver mode, but the UI does not strongly verify/display Media3's actually selected rendition after the choice. `updateReadyDiagnostics()` currently lists available qualities and declared source size but does not report the actively selected video-track height. Next quality work should distinguish **requested quality** from **actually selected/observed quality** and only claim success when Media3 confirms the selected rendition.

QoL request for next player-menu polish:
- make the vertical height/padding of the gear menu and its submenus a little more compact;
- keep them touch-friendly and anchored, without returning to large centered option dialogs;
- convert Video Quality to the same compact anchored-menu family while preserving current quality-selection logic.

## Rare HH HLS DNS edge case observed on #264
User found a very rare HH playback failure while most HH videos continue to work. Technical diagnostics from the failing source:
- resolver: `browser`;
- mode: `single`;
- HLS master source host: `master-lengs.org`;
- source path: `/api/v3/hh/tsf-monogatari-1-720p-v1x/master.m3u8`;
- MIME: `application/x-mpegURL`;
- Media3 error: `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED (2001)`;
- nested failure: `UnknownHostException` for downstream host `eng-jaen.top`, with `EAI_NODATA` / no address associated with hostname.

Interpretation:
- ExternalPlayer successfully obtained and opened the technical HLS master URL far enough for Media3 to request a downstream HLS host referenced by that manifest.
- Android then could not resolve that downstream hostname at DNS level. This occurs before codec/decoder playback and is not evidence of a Media3 codec failure.
- The mismatch between the top-level source host (`master-lengs.org`) and failing nested host (`eng-jaen.top`) is consistent with an HLS master/playlist referencing child playlists or segments on another host.
- Because only a very small set of apparently older HH sources show it while normal HH playback works, treat this as a **rare source/host availability edge case**, not a regression of the general HH resolver/player path.
- Do not add site-specific DNS substitution, host rewriting, credential import, protected-access bypass, or guessed mirror logic.

Safe app-side improvements worth considering for this edge case:
- detect `UnknownHostException` in the Media3 cause chain and show a clearer technical message such as “media host unavailable / DNS lookup failed”;
- keep `Retry playback` for temporary DNS/network recovery;
- keep `Refresh source` to re-resolve the original page in case the page now supplies a fresh working manifest;
- if normal browser discovery already captured another valid candidate, allow the user to try it through the existing candidate flow;
- if every legitimate candidate ultimately points to the same unresolvable host, report the source as unavailable rather than fabricating or bypassing a replacement.

## Brave compatibility decision after #264 QA
Brave-as-is compatibility **PASSED** on the unchanged generic share implementation.
- No Brave-specific code is currently justified.
- Continue using the generic Android `ACTION_SEND text/plain` share targets for Brave and Vivaldi.
- Vivaldi remains the primary protected regression baseline, with Brave now a known-compatible browser smoke target.

## Recovered “store for later” backlog from project history
Keep these items visible so they are not accidentally compressed out again.

### Still genuinely deferred
- **Secure `Report log on GitHub` shortcut.** Keep ordinary full log sharing; later add a browser-based GitHub issue/report path without embedding a PAT, OAuth secret, repository write token, or other reusable credential in the APK.
- **Stale historical/dead-path cleanup.** Remove obsolete Activity/provider/legacy preparation paths only after proving they are unused, and never disturb protected #234/#236 behavior merely for cosmetic cleanup.
- **Operations-log / diagnostics noise cleanup.** Keep useful technical evidence while removing obsolete, duplicate, or confusing development-only noise.
- **About/version/build/documentation consistency.** Finalize version/build display, About information, README/state docs and release notes during release preparation.
- **Release distribution + permanent signing decision.** Debug Actions APKs remain the QA route. Permanent signing/distribution stays last-stage work; never commit a permanent private signing key.

### Historical deferred idea to revisit, not automatically implement
- **Dedicated “Return to existing Vivaldi task/tab” action.** Later Back-flow QA and the persistent dashboard may have partly superseded it. Revisit as a UX decision rather than silently adding a new button.

### Deferred items now promoted/completed
- **App-level Volume/Mute**: implemented in #264 and included in mostly-successful device QA.
- **Brave Mobile evaluation**: #264 compatibility PASS without Brave-specific code.
- multi-video tabs/per-tab titles, persistent tabs, Recently Closed, automatic selection/manual fallback, playback speed, language selector/localization, launcher/logo refinement and current loading/UI work are already implemented/substantially absorbed.

## Current priority
1. Next focused player polish should: convert **Video Quality** from centered AlertDialogs to the compact anchored menu style; slightly reduce vertical menu/submenu padding/height; and add truthful requested-vs-actual quality verification so manual 480p can be proven rather than assumed.
2. Preserve all successful #264 behavior, especially Recents privacy, fullscreen, Audio/Volume/Speed, stale-source Refresh, Brave compatibility, #234 BG architecture, one ExoPlayer, 720-first auto policy and #249 palette.
3. Treat the rare HH `UnknownHostException` child-host case as a source-availability edge case. Improve recovery wording/diagnostics only if useful; do not add guessed host rewrites or bypass behavior.
4. After this small quality/menu polish settles, run final PH technical regression, HH technical regression, both Vivaldi share-target regressions, plus a small Brave smoke regression; then proceed to hardening/diagnostics cleanup and release-readiness work.

## QA format
Whenever asking the user to test, provide exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
