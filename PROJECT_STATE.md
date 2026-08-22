# Vivaldi External Player — Project State

GitHub `main` is authoritative. Read this file and `CHAT_BOOTSTRAP.md` before substantive work. Keep both current whenever architecture, QA, failures, priorities, or decisions change.

## Working / safety rules
- Explain plainly; user is not an advanced developer. Android UI remains bilingual English/Spanish. Keep source well commented in English.
- Use connected GitHub directly whenever possible.
- PH/HH are technical playback targets only: URLs, manifests, codecs, resolutions, request metadata, resolver/candidate ranking, playback state/errors, and local titles are allowed.
- Never inspect, describe, classify, summarize, request, or analyze PH/HH media content or thumbnail imagery.
- Never bypass DRM, paywalls/subscriptions, authentication, regional restrictions, CAPTCHA/anti-bot, or import browser credentials.
- Never add background playback or a second ExoPlayer.

## Protected architecture
Build #234 background preparation architecture is protected:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.

Do not reintroduce a preparation Activity on display 0. Do not use PlayerActivity/Media3/ExoPlayer during BG preparation.

## Protected playback policy
Preserve exactly one ExoPlayer, yt-dlp first/browser fallback, automatic + manual quality, video + audio, adaptive/sibling switching, double-tap ±10 seconds, seek preview, rotation, bilingual UI, existing candidate limits/order, and no imagery-based ranking.

Automatic quality policy:
1. exact 720p
2. otherwise 1080p
3. otherwise highest below 1080p
4. >1080p only as rare fallback

## Protected UI baseline
Build #249 palette is approved: purple `#B05CFF`, charcoal `#17191F` family, white primary content; green/amber/red semantic only. Do not change unless explicitly requested.

Build #278 is the accepted player/UI DEVICE PASS baseline.
- App-code commit: `8b0566c68eb9082c0aed62e202edfc1a29232983`
- Actions run `31905713180`; build job `95063044270`; artifact `9252287185`
- APK SHA-256 `ee5893ef22a7a38758293ce9647ac133f09bea8527855c836c8dd65f13ba6043`

Accepted #278 behavior includes compact gear and Video Quality menus, requested-vs-actual quality verification including the 480p path, Audio, app-level Volume/Mute, speed, Diagnostics, fullscreen, exact lower-right `[tab count] [gear] [fullscreen]`, tab dashboard, controller auto-hide, double-tap ±10s, no visible dedicated ±10 buttons, and approved colors.

Player-control/menu UI is settled unless later regression evidence appears.

## Build #264 retained behavior
Build #264 established Recents privacy, stale-source `Refresh source`, Vivaldi health, and Brave Mobile compatibility using the generic Android share targets. Do not add Brave-specific code without a concrete regression.

## Rare HH DNS edge case
A very small number of older HH HLS sources may fail because a downstream HLS host genuinely does not resolve (`UnknownHostException` / `EAI_NODATA`). Treat this as source/upstream availability, not a general app regression.

Safe recovery only:
- Retry playback
- Refresh source
- an already-detected legitimate alternate candidate

Never rewrite hostnames, invent mirrors, substitute DNS hosts, or bypass access controls.

## Regression status after #278
User completed the broad PH/HH technical regression and repeated the normal Vivaldi flows. Result: PASS except the already-known inability to reliably revive some old/expired tabs.

User also repeated the same normal flows in Brave and reports they worked correctly. Brave remains generic-share compatible; no Brave-specific implementation is needed.

User reports the protected background-preparation architecture successfully handled **31 tabs** without issue. General stress hardening is therefore intentionally lower priority for this personal-use app unless a concrete regression appears.

## Known unresolved functional gap
### Old/expired tab revival
Some old tabs cannot be revived reliably. The agreed direction is to treat the original page URL as permanent tab identity and temporary resolved media URLs as replaceable playback state.

Future revival should use:
`expired/failed media -> stored original page URL -> existing #234 preparation path -> refreshed legitimate playback candidates`

Do not attempt to repair expired CDN/HLS URLs by guessing or rewriting hosts.

## Permanent signed release — current immediate priority
The user chose to establish a proper permanently signed release APK before the next feature build.

Repository support already exists:
- `app/build.gradle.kts` reads release signing only from environment variables.
- `.github/workflows/build-apk.yml` reconstructs the keystore only in the runner, builds `assembleRelease`, verifies the APK with `apksigner`, removes the runner keystore, and uploads `VivaldiExternalPlayer-signed-release-apk`.
- No permanent private signing material may ever be committed.

The user confirmed the four GitHub Actions repository secrets are now configured:
- `VEP_KEYSTORE_BASE64`
- `VEP_KEYSTORE_PASSWORD`
- `VEP_KEY_ALIAS`
- `VEP_KEY_PASSWORD`

First signed-release CI verification is now the immediate task. Keep the accepted app code on #278 while establishing signing unless a signing-specific build fix is required.

Important installation note: the existing debug APK and the permanent release APK use different signatures. The first migration may require uninstalling the debug app, which normally removes app-local tabs/settings. Do not tell the user to uninstall until the signed artifact has been verified and migration implications are clear.

## Agreed next combined feature / cleanup build
After permanent signing is established, implement the following as one scoped phase, preserving protected architecture/playback/UI:

1. **Original-page-URL revival foundation**
   - Favorites and revival should store/use the original page URL, never a temporary resolved media URL as permanent identity.

2. **Update status of tabs**
   - Check tab health conservatively with serialized/limited work.
   - Distinguish states such as Ready, Needs refresh, Checking, Unavailable, Needs attention.
   - Avoid marking a tab permanently dead from one transient network failure.

3. **Revive expired tabs**
   - Re-resolve only tabs needing refresh, from stored original page URLs, through the existing protected #234 path.

4. **Close all tabs**
   - Main-screen action with confirmation using the approved UI family.
   - Prefer moving closed tabs into Recently Closed rather than destructive deletion.
   - Safely cancel relevant pending preparation work.

5. **Favorites / Private Favorites**
   - Favorites store original page URLs.
   - Preferred privacy design: normal Favorites plus an optional Private Favorites collection protected with Android system/device authentication rather than a custom password system.
   - When locked, do not expose protected titles/URLs/thumbnails through ordinary UI, Recents, or routine diagnostics.

6. **Open in Vivaldi**
   - Desired player gear submenu action opens the stored original URL in Vivaldi.
   - User requires it to **always** open in Vivaldi Private/Incognito mode.
   - Do not implement a one-tap version unless a reliable supported mechanism can actually guarantee private mode; do not falsely label normal `ACTION_VIEW` as private.

7. **Diagnostics / operations-log cleanup**
   - Remove duplicate/noisy routine entries and obsolete debug wording while preserving useful resolver/playback evidence.
   - Improve separation of resolver vs playback failures and retain requested-vs-actual quality and source/DNS causes where useful.
   - No resolver ranking, playback policy, BG architecture, or ExoPlayer ownership changes for cosmetic cleanup.

8. **Release-readiness consistency**
   - Align About/version/build/README/release-note wording with actual behavior.
   - Keep permanent signing material private and outside the repository.

## Stored-for-later backlog
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials in the APK
- safe dead/historical code-path cleanup only after proving paths unused
- old dedicated “Return to existing Vivaldi task/tab” idea: reconsider later, do not automatically implement
- broader failure/stress hardening if real personal-use regressions justify it

## Current roadmap
1. Verify the first permanent signed release APK from GitHub Actions while app code remains the accepted #278 baseline.
2. After signing is confirmed, implement the agreed combined feature/cleanup build above.
3. Run focused QA on the changed areas only, plus quick protected-baseline sanity checks.
4. Keep distribution/signing continuity documented for future updates.

## QA request format
Whenever explicitly asking the user to test an APK, use exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.

Do not add extra code blocks to that QA request.
