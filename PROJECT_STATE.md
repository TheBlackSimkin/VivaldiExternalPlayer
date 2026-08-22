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

## Permanent release signing — established
Permanent signing is now established. No permanent key material is stored in the repository.

Failure history:
- Build #284 original attempt and retry reached release packaging but failed with `UnrecoverableKeyException` / PKCS#12 bad-padding because the generated separate key-password value did not match the private key password.
- Local keystore validation proved the backup PKCS#12 file was healthy and that the keystore password is also the effective private-key password.
- Signing-only fix commit `cd92c51936cb34594cfb820de0e2c311b8b09253` makes Gradle use `VEP_KEYSTORE_PASSWORD` for both store and key password. Runtime/player/resolver/UI behavior was not changed by that fix.
- Run `32543326847` then built and v2-verified the release successfully but could not upload artifacts because GitHub Actions artifact storage quota was full.

Successful downloadable checkpoint:
- Actions run `32546091175`
- build job `96964652777`
- head commit `74ff674e912e10d6b78fb1ba5fe8544d0325fdc1`
- debug build: success
- release build: success
- `apksigner verify`: success, APK Signature Scheme v2 = true, one signer
- signed artifact `9468570335`
- signed artifact ZIP SHA-256 `b4e70c64f9aa594b402a87ee819ad669ac3ea20aa8926dbed23dc99bf25e849e`
- contained `app-release.apk` SHA-256 `1fe9f098aa202634c7a2a45f61ec8e1d40fecb6f15b9eb79e81e94a3a179f74d`

The compile/signing/upload pipeline is therefore working. Do not change signing secrets casually.

The permanent certificate fingerprint recorded from the backup is:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

The current CI workflow verifies signature validity but does not print the certificate fingerprint, so do not claim the downloaded feature-checkpoint APK fingerprint was independently matched unless that verification is actually performed.

Important installation note: an installed debug APK and the permanent release APK use different signatures. Android normally rejects the release APK as an update over the debug APK with the same application ID. Uninstalling the debug app normally removes app-local tabs/settings. Do not casually instruct the user to uninstall before state-preservation implications are understood.

## Current feature / cleanup phase
The combined post-signing feature phase is now in progress on `main`, preserving the protected architecture/playback/UI baseline.

Implemented and compile-verified at run `32546091175`:
1. **Permanent original-page URL foundation**
   - `TabOriginStore` retains the original page URL independently from temporary resolved media state.
   - The real V2 BG share handoff records the exact shared original URL.
   - Recovery paths prefer the permanent origin rather than allowing refreshed media payloads to replace tab identity.

2. **Update status of tabs**
   - Main-screen action performs conservative serialized health checks.
   - Health is stored separately from preparation state.
   - Hard expiry-like responses can become `Needs refresh`; transient DNS/timeouts/5xx are treated conservatively rather than immediately declaring a tab permanently dead.

3. **Revive expired tabs**
   - Revival uses original page URLs.
   - `TabRevivalCoordinator` serializes stale-tab revival through the protected foreground-service/private-display preparation architecture.
   - It does not create a preparation Activity, PlayerActivity, Media3 or another ExoPlayer.

4. **Close all tabs**
   - Main-screen action with confirmation.
   - Tabs are moved through the existing close/Recently Closed safety path rather than being silently destroyed.
   - queued scheduled work is cancelled where applicable.

5. **Favorites / Private Favorites foundation**
   - Normal Favorites store original page URL + title locally.
   - Private Favorites store encrypted title/URL data in app-private preferences using an Android Keystore AES-GCM key.
   - Private Favorites UI requires Android biometric/device authentication before decrypting/rendering entries.
   - Private Favorites uses `FLAG_SECURE`, is excluded from Recents, uses no thumbnails, and locks again when leaving.
   - Favorites launch a fresh tab through the protected foreground-service/private-display preparation path.
   - Settings exposes both Favorites screens.

6. **Bilingual UI**
   - New tab-maintenance and Favorites copy has English and Spanish resources.

The compile checkpoint confirms the new Android biometric dependency and all currently added feature files compile in both debug and release builds.

## Still to finish in this feature phase
1. Add player-facing **Add to Favorites** and **Add to Private Favorites** actions while preserving the approved compact #278 gear behavior.
2. **Open in Vivaldi Private** remains intentionally unimplemented unless a reliable supported Vivaldi mechanism can guarantee opening an arbitrary URL directly in a private/incognito tab. A normal Android `ACTION_VIEW` must never be labeled as guaranteed private.
3. Conservative diagnostics / operations-log cleanup only; no resolver ranking, playback policy, BG architecture or player ownership changes.
4. Release-readiness consistency: About/version/build/README/release wording.
5. Build final signed feature APK and run focused device QA on changed areas plus quick protected-baseline sanity checks.

## Stored-for-later backlog
- secure browser-based `Report log on GitHub` shortcut; never embed reusable GitHub credentials in the APK
- safe dead/historical code-path cleanup only after proving paths unused
- old dedicated “Return to existing Vivaldi task/tab” idea: reconsider later, do not automatically implement
- broader failure/stress hardening if real personal-use regressions justify it

## Current roadmap
1. Finish player Favorites controls, conservative diagnostics cleanup, and release consistency.
2. Build and verify the final permanently signed feature APK.
3. Run focused QA on the changed areas only, plus quick protected-baseline sanity checks.
4. Preserve signing continuity for all future release APK updates.

## QA request format
Whenever explicitly asking the user to test an APK, use exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.

Do not add extra code blocks to that QA request.
