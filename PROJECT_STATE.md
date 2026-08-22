# Vivaldi External Player — Project State

GitHub `main` is authoritative for released code. Read this file and `CHAT_BOOTSTRAP.md` before substantive work. Keep both current whenever architecture, QA, failures, priorities, or decisions change.

## Working / safety rules
- Explain plainly; Android UI remains bilingual English/Spanish. Keep source comments in English.
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

Accepted behavior includes compact gear and Video Quality menus, requested-vs-actual verification including 480p, Audio, app-level Volume/Mute, speed, Diagnostics, fullscreen, exact lower-right `[tab count] [gear] [fullscreen]`, tab dashboard, controller auto-hide, double-tap ±10s, no visible dedicated ±10 buttons, and approved colors.

Player-control/menu UI is settled unless regression evidence appears.

## Build #264 retained behavior
Build #264 established Recents privacy, stale-source `Refresh source`, Vivaldi health, and Brave Mobile compatibility using generic Android share targets. Do not add Brave-specific code without a concrete regression.

## Rare HH DNS edge case
A very small number of older HH HLS sources may fail because a downstream host genuinely does not resolve (`UnknownHostException` / `EAI_NODATA`). Treat this as source/upstream availability, not a general app regression.

Safe recovery only: Retry playback, Refresh source, or an already-detected legitimate alternate candidate. Never rewrite hostnames, invent mirrors, substitute DNS hosts, or bypass access controls.

## Permanent release signing — established
Permanent signing is established. No permanent key material is stored in the repository.

Signing-only fix commit: `cd92c51936cb34594cfb820de0e2c311b8b09253`. Gradle uses `VEP_KEYSTORE_PASSWORD` for both PKCS#12 store and private-key password.

Permanent certificate SHA-256 fingerprint:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

CI runs `apksigner verify --verbose --print-certs`; 0.3.2 also checks zip alignment and package badging. Do not change signing secrets casually.

## Released baseline: 0.3.1 / versionCode 4
0.3.1 added **Vivaldi Private + Copy URL** after device/APK investigation proved there is no safe exported arbitrary-URL private launch route in tested Vivaldi Android.

0.3.1 build:
- Actions run `32552025984`
- build job `96980224294`
- feature/release head `6a195bd0019b3335236375650f4123f11f3bc3fc`
- signed artifact `9470356520`
- ZIP SHA-256 `c4660868785eb06b52ba274a14e4f0b9e3f34aa7ac452e7374be9c0b55a90606`
- APK SHA-256 `6e02fb3df1ea831a42d4d4c582a37c46f4b68772f9cd8f438ae821fc9fa0db51`
- signer certificate SHA-256 `8c87e1f6a7a4873f12cb25ba348bef665057159f16a65b9059e5e1d7c0b95e7c`

0.3.1 was installed successfully by ADB in-place update. Focused device QA for **Vivaldi Private + Copy URL** is now **PASS**.

## 0.3.2 candidate — correctness, privacy and dashboard cleanup
Work is on branch `work/0.3.2-correctness-ux`, PR #2. Do not merge/release until focused device QA is reported.

Version: `0.3.2` / versionCode `5`.

Final code-gate build before this documentation refresh:
- Actions run `32590746439` (run #331)
- build job `97074080536`
- signed artifact `9480279353`
- signed artifact ZIP SHA-256 `9081a7851b272ad9f1ccb039c77fe5ccea32137fb73c5444d92a5f9ab896af6b`
- release APK SHA-256 `bc5b854980faa214ee0b9d7ef5a7676923ffc2c95e3cac4029e47f79a3f77799`
- debug + signed release build PASS
- zipalign/package checks PASS
- signing verification PASS; permanent signer continuity retained

### 0.3.2 implemented changes
- `TabMaintenanceController` is the single stale/error-tab revival policy used by both individual and global revival.
- A READY tab whose health is `NEEDS_REFRESH` now exposes **Revive** directly on its card; ERROR tabs also use the same revival path.
- Status checks may continue while PlayerActivity is open, but MainActivity suppresses dashboard redraw/thumbnail callbacks while not RESUMED.
- A stronger codec-contention fix removes PlayerActivity-triggered dashboard thumbnail warm-up. Background `FrameExtractor` work is serialized and suspended/cancelled while PlayerActivity owns foreground playback.
- Media3 decoder fallback is enabled inside the same ExoPlayer via `DefaultRenderersFactory.setEnableDecoderFallback(true)`. No second player and no forged codec/frame-rate metadata.
- If all compatible decoders fail initialization, recovery UI explains the decoder case and retains Retry / Refresh source / legitimate alternate/browser recovery.
- Recently Closed is now browser-like and bounded at 100 entries instead of 12; normal Close All archives through the same history.
- Dashboard global operations moved under the main gear menu: Tabs, Library, Privacy, App sections. The main screen is visually cleaner.
- App privacy shield is manually activated with **Hide & lock app**. The app starts unlocked. While hidden, the dashboard is covered and protected by `FLAG_SECURE`; **Reveal ExternalPlayer** requires Android biometric/device credential.
- Shared URLs received while hidden are deferred until successful authentication rather than resolving behind the curtain.
- `SystemAuthGate` centralizes Android biometric/device-credential prompting and is shared by Private Favorites and the privacy shield.
- Tab-card text `×` was replaced by a proper close icon.
- CI now runs on PRs and adds zip alignment/package sanity checks before merge.

Private Favorites caveat remains unchanged: its AES-GCM blob is encrypted locally, but the Keystore key is not currently configured as a hardware/user-auth-bound key. Do not overclaim stronger cryptographic binding.

### Decoder failure investigation
A reported PH HLS source reached playback but failed with `ERROR_CODE_DECODER_INIT_FAILED`, Qualcomm `c2.qti.avc.decoder`, `format_supported=NO_EXCEEDS_CAPABILITIES`, and an implausible reported frame rate around `12857.142` fps for 1280x720 AVC. Treat this as a decoder/stream-metadata compatibility case, not an expired-source or DNS failure.

0.3.2 management is deliberately conservative: let Media3 try another compatible decoder inside the same ExoPlayer; if that still fails, expose recovery. Do not rewrite manifests or invent/sanitize stream metadata unless a future reproducible investigation proves a safe rule.

### Direct APK installation investigation
Normal Android file-tap installation has historically shown the generic `App not installed`, while `adb install -r` proves package/signing continuity. 0.3.2 CI verifies signing, alignment, and package metadata, so device QA must now test a standalone extracted `.apk` saved as a normal file (not launched from inside the GitHub Actions ZIP/archive view).

If the standalone APK still fails by tap, capture the actual PackageInstaller/PackageManager failure reason during that failed attempt; do not guess from the generic UI message and do not uninstall the working signed app just to test.

## Owed 0.3.2 device QA
- direct standalone APK tap update over 0.3.1
- individual stale-tab Revive
- Check status -> open Player midway: no blinking/reload race
- reported decoder-failure source if still available: fallback playback or graceful decoder-specific recovery
- Close All / Recently Closed with more than 12 tabs when practical
- consolidated gear-menu UX and proper close icon
- Hide & lock app / Reveal ExternalPlayer authentication, including no background shared-URL resolution while hidden
- basic protected player regression spot-check

Do not mark any of these 0.3.2 items PASS until the user reports results.

## Deferred backlog
- **Report log on GitHub** shortcut remains postponed; never embed reusable GitHub credentials in the APK.
- Return-to-Vivaldi behavior stays as-is for now.
- Continue disciplined, evidence-based cleanup/refactoring alongside feature work; remove historical paths only after proving them unused.
- Broader stress hardening only when real personal-use evidence justifies it.

## QA request format
Whenever explicitly asking the user to test an APK, use exactly:
1. one detailed code block containing steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.

Do not add extra code blocks to that QA request.
