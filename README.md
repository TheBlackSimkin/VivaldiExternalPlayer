# Vivaldi External Player — Android

Vivaldi External Player receives an ordinary Android-shared webpage URL, resolves an accessible non-DRM media stream, and plays it with AndroidX Media3 / ExoPlayer. Vivaldi is the primary browser workflow; Brave Mobile is also compatible through the same generic Android share targets.

## Current behavior

- Two Android share flows: play now and background preparation.
- Background preparation uses a foreground service, an app-private virtual display, and a non-Activity Presentation/WebView. It does not create PlayerActivity or ExoPlayer and does not perform background playback.
- Direct yt-dlp resolution first, then serialized browser-assisted fallback when needed.
- Automatic quality preference: exact 720p, otherwise 1080p, otherwise the highest available quality below 1080p; above 1080p is only a rare fallback.
- Manual video quality, including adaptive Media3 tracks and page players which expose sibling URLs per quality.
- HLS, DASH, progressive playback, and separate video/audio merging.
- Exactly one ExoPlayer playback session.
- Audio-track selection, app-level Volume/Mute, playback speed, fullscreen, and copyable diagnostics.
- Visible extracted-frame seek preview where supported.
- Double-tap left/right for -10/+10 seconds, with no dedicated visible seek buttons.
- Persistent multi-video tab dashboard with local titles, playback position, requested/actual quality state, drag reordering, close/recovery, and Recently Closed.
- Recents privacy for the dashboard.
- Permanent original-page URL storage for refresh/revival instead of treating temporary CDN/media URLs as permanent identity.
- Conservative serialized **Update status** checks and **Revive expired** recovery through the same protected background-preparation architecture.
- Main-screen **Close all tabs** confirmation using Recently Closed as a safety net.
- **Favorites** store original page URLs and local titles.
- **Private Favorites** encrypt titles/URLs at rest with an Android Keystore AES-GCM key and require Android biometric/device authentication before displaying them. The private screen is screenshot/Recents protected and does not use thumbnails.
- Player gear actions for adding the current original page to Favorites or Private Favorites.
- Player gear action **Vivaldi Private + Copy URL** copies the stored original page URL and opens Vivaldi's genuine blank Private tab so the user can paste it there.
- English and Spanish user-facing strings.
- Permanently signed release APKs from GitHub Actions; private signing key material is never committed.

## Browser privacy note

Device inspection of Vivaldi 8.1.4099.123 confirmed that Vivaldi exposes Chromium's genuine `IncognitoTabLauncher`, but that exported launcher intentionally opens a blank Private tab and ignores an incoming URL. A normal `ACTION_VIEW` opens a regular tab, the launcher shortcut activity is not exported, and the tested Chromium incognito extras do not provide a reliable arbitrary-URL private launch.

For that reason the app does **not** pretend it can force an arbitrary URL directly into a Vivaldi Private tab. The supported fallback is explicit: **Vivaldi Private + Copy URL** copies the stored original webpage URL, opens Vivaldi's real blank Private tab, and tells the user to paste the copied URL there.

## Important boundary

The app is not intended to bypass DRM, authentication, subscriptions, regional restrictions, CAPTCHA/anti-bot challenges, paywalls, or other access controls. It does not import browser passwords or credentials. Use it only for media you are authorized to access.

## Build configuration

- Version 0.3.1 (`versionCode 4`)
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- compileSdk/targetSdk 36
- Minimum Android API 24
- JDK 17
- Chaquopy 17.0 / Python 3.13
- yt-dlp 2026.06.09
- Media3 1.10.1
- arm64-v8a APK

See `BUILD_APK.md` for the GitHub Actions workflow and `PROJECT_STATE.md` for the protected architecture, accepted QA baseline, signing continuity, and current development status.

## Current follow-up work

- Focused device QA for the 0.3.1 Vivaldi Private + Copy URL action, plus a quick player-menu regression check.
- Conservative diagnostics/operations-log noise cleanup where it can be proven not to affect resolver or playback behavior.
- Broader hardening only when real personal-use regressions justify it.
