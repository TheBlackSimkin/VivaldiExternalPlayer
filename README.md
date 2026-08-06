# Vivaldi External Player — Android prototype

This Android app receives a shared page URL from Vivaldi, resolves an accessible non-DRM media stream with yt-dlp, and plays it in AndroidX Media3.

## Intended features

- Android Share target for `text/plain`
- Best available stream up to 1080p
- Separate video/audio playback when the selected format uses separate streams
- HLS, DASH, and progressive playback
- Visible extracted-frame preview while dragging the timeline
- Double-tap left/right for −10/+10 seconds
- No playback-history database or disk media cache

## Important boundary

The app is not intended to bypass DRM, authentication, subscriptions, regional restrictions, or other access controls. Use it only for media you are authorized to access.

## Build configuration

- Android Gradle Plugin 8.13.2
- Gradle 8.13
- compileSdk/targetSdk 36
- JDK 17
- Minimum Android API 24
- Chaquopy 17.0 / Python 3.13
- Media3 1.10.1

See `BUILD_APK.md` for a no-code cloud build workflow.

## Prototype limitations

- The project has not been compiled or device-tested in the ChatGPT execution environment because that environment has no Android SDK or Gradle distribution.
- No manual quality chooser yet; it automatically caps selection at 1080p.
- Browser cookies are not imported, so login-only or age/session-gated pages may fail.
- HentaiHaven is handled only through yt-dlp's available generic/embedded extraction paths and may fail after site changes.
- Frame previews depend on the selected remote stream supporting seeking; some adaptive streams may be slow or unsupported.
