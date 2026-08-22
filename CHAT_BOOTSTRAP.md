# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` and this file before substantive work. Keep both current whenever QA, architecture, failures, priorities, or decisions change.

## Safety / communication
- Explain plainly; user is not an advanced developer. Use connected GitHub directly. Keep source well commented in English.
- PH/HH are technical playback targets only. Allowed: URLs, manifests, codecs, resolutions, request metadata, resolver/candidate ranking, playback states/errors, local titles.
- Never inspect/describe/classify/summarize/request PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import browser credentials.
- Never add background playback or a second ExoPlayer.

## Protected architecture / playback
Build #234 background preparation is protected:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.

No preparation Activity on display 0. No PlayerActivity/Media3/ExoPlayer during BG preparation.

Preserve exactly one ExoPlayer, yt-dlp first/browser fallback, automatic/manual quality, video+audio, adaptive/sibling switching, gestures, seek preview, rotation, bilingual UI, candidate limits/order, and no imagery-based ranking.

Automatic quality: exact 720p -> otherwise 1080p -> otherwise highest below 1080p -> >1080p rare fallback only.

## Accepted player/UI baseline
Build #278 is DEVICE PASS and is the accepted app-code baseline.
- App commit: `8b0566c68eb9082c0aed62e202edfc1a29232983`
- Actions run `31905713180`; build job `95063044270`; artifact `9252287185`
- APK SHA-256 `ee5893ef22a7a38758293ce9647ac133f09bea8527855c836c8dd65f13ba6043`

Accepted behavior: compact gear and Video Quality menus, requested-vs-actual quality verification including 480p path, Audio, app-level Volume/Mute, speed, Diagnostics, fullscreen, exact `[tab count] [gear] [fullscreen]`, tab dashboard, controller auto-hide, double-tap ±10s, no visible ±10 buttons.

Build #249 palette remains protected: purple `#B05CFF`, charcoal `#17191F` family, white primary; green/amber/red semantic only.

Build #264 retained behavior: Recents privacy, stale-source `Refresh source`, Vivaldi health, Brave-as-is compatibility with generic Android share targets. No Brave-specific code without a real regression.

## Rare HH DNS edge case
Some rare older HH HLS sources can fail because a downstream host genuinely does not resolve (`UnknownHostException` / `EAI_NODATA`). Treat as source availability. Safe recovery only: Retry, Refresh source, or an already-detected legitimate alternate candidate. Never rewrite hosts or invent mirrors.

## Latest QA / decisions
User reports broad PH/HH technical regression is complete and passed, except the previously known old/expired-tab revival limitation.

User repeated the same normal Vivaldi-style flows in Brave and reports they worked correctly. No Brave-specific work is needed.

User reports **31 tabs** could be opened/prepared in background without issue. Broader stress hardening is postponed/lower priority for this personal-use project unless a concrete regression appears.

## Known unresolved gap: revive old tabs
Agreed model:
- store/retain original page URL as permanent tab identity;
- treat resolved media URLs/candidates as temporary playback state;
- revival uses `original page URL -> existing #234 preparation path -> refreshed legitimate candidates`.

Do not repair expired media URLs by guessed hostname/CDN substitutions.

## Immediate priority: permanent signed release APK
User explicitly chose signing before the next feature build.

Current repository already supports signing:
- `app/build.gradle.kts` reads signing from environment variables only;
- `.github/workflows/build-apk.yml` reconstructs the keystore in the runner, builds release, verifies with `apksigner`, deletes temporary keystore, and uploads `VivaldiExternalPlayer-signed-release-apk`.

User confirmed the four required GitHub Actions repository secrets are configured:
- `VEP_KEYSTORE_BASE64`
- `VEP_KEYSTORE_PASSWORD`
- `VEP_KEY_ALIAS`
- `VEP_KEY_PASSWORD`

Never commit permanent signing material.

The first signed-release CI run should keep the accepted #278 app code unchanged unless a signing-specific fix is required. Clearly distinguish the #278 app-code commit from later docs-only main HEAD commits.

First transition from installed debug APK to release APK may require uninstalling the debug app because the signatures differ; uninstall normally removes app-local tabs/settings. Do not ask the user to uninstall until the signed release artifact is fully verified.

## Next combined feature / cleanup build after signing
Agreed scope:
1. Original-page-URL foundation for tab revival and Favorites.
2. Main-screen **Update status of tabs** with conservative serialized/limited checking and clear states.
3. Main-screen **Revive expired tabs**, re-resolving only stale/failed tabs from original page URLs through #234 architecture.
4. Main-screen **Close all tabs** with approved-style confirmation; prefer Recently Closed safety net over destructive deletion.
5. **Favorites / Private Favorites**: store original page URLs. Preferred private design uses Android system/device authentication rather than a custom password. Locked private entries must not leak titles/URLs/thumbnails through normal UI, Recents, or routine diagnostics.
6. Player gear **Open in Vivaldi** using stored original URL, but user requires ALWAYS Private/Incognito. Do not implement unless a reliable supported mechanism can guarantee private mode; normal `ACTION_VIEW` is not enough.
7. Diagnostics / operations-log noise cleanup, without altering resolver ranking, playback policy, #234 architecture, or one-player ownership.
8. About/version/build/README/release-note consistency.

## Stored backlog
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials
- safe proven-dead historical code cleanup only later
- old “Return to existing Vivaldi task/tab” idea: reconsider later, never auto-implement
- broader hardening only when personal-use evidence justifies it

## Current roadmap
1. Verify first permanent signed release from GitHub Actions on accepted #278 app code.
2. Then implement the agreed combined feature/cleanup build.
3. Focused QA on changed areas + quick protected-baseline sanity checks.
4. Preserve signing continuity for all future release APK updates.

## QA format
Whenever explicitly asking the user to test an APK, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.

Do not add extra code blocks to that QA request.
