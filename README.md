# Vivaldi External Player — Android prototype

This Android app receives a shared webpage URL from Vivaldi, resolves an
accessible non-DRM media stream, and plays it in AndroidX Media3 / ExoPlayer.

## Current behavior

- Android Share target for `text/plain`.
- Direct yt-dlp resolution first, then automatic browser-assisted fallback.
- Browser-assisted mode automatically tries its best detected video; manual
  candidate selection is a fallback, not the normal workflow.
- Quality preference: 720p, then 1080p, then best available below 1080p.
- HLS, DASH, progressive playback, and yt-dlp separate video/audio merging.
- Quality selection for adaptive Media3 tracks and page players which expose
  separate URLs per quality.
- Visible extracted-frame preview while dragging the timeline where supported.
- Double-tap left/right for -10/+10 seconds.
- English and Spanish user-facing strings.
- Copyable playback diagnostics.

## Important boundary

The app is not intended to bypass DRM, authentication, subscriptions, regional
restrictions, anti-bot challenges, or other access controls. Use it only for
media you are authorized to access.

## Build configuration

- Android Gradle Plugin 8.13.2
- Gradle 8.13
- compileSdk/targetSdk 36
- JDK 17
- Minimum Android API 24
- Chaquopy 17.0 / Python 3.13
- Media3 1.10.1

See `BUILD_APK.md` for the GitHub Actions build workflow.

## Current backlog

- Resolver/candidate-selection regression testing is still in progress.
- Playback-speed control is pending.
- App-level volume/mute is pending.
- Dedicated return to the existing Vivaldi task/tab is pending.
- Frame previews depend on the selected remote stream supporting seeking.
