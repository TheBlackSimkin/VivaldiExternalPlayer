# Temporary Chat Bootstrap — Vivaldi External Player

Public repository:
`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Treat GitHub `main` as the source of truth. Before changing code, read `PROJECT_STATE.md` in full. Keep both files updated whenever requirements, architecture, tests, failures, or priorities materially change.

## Communication

- Conversation: English.
- Windows/Vivaldi UI: normally Spanish; use Spanish UI labels when relevant.
- Android app UI: bilingual English/Spanish.
- Explain plainly; user is not an advanced developer.
- Do GitHub work directly whenever possible.

## QA format

Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, **EXPECTED**, and **RESULT**;
2. one separate short code block containing only the compact answer format.

Never ask for PH/HH page/video title text. Titles may be used locally by the app only.

## Safety/content boundary

PH and HH are real-world technical playback targets. The user performs the playback tests.

Allowed: technical analysis of URLs, manifests, codecs, quality, request metadata, candidate ranking, browser/network state, playback errors/status, and local tab-title handling.

Do not:
- inspect/analyze/classify/describe video content;
- bypass DRM or obtain keys;
- bypass subscriptions/paywalls/authentication/regional controls;
- deliberately automate anti-bot challenges;
- import private credentials.

### Automatic age/cookie consent

The user is over 18 and explicitly wants the resolver to automatically accept **clearly identified**:
- 18+ / age-confirmation prompts;
- cookie-consent prompts.

This automation must be conservative. Never auto-click ambiguous buttons, sign-in/account prompts, paywall/subscription/payment controls, regional controls, DRM controls, anti-bot/CAPTCHA/challenge controls, or unrelated page actions. If uncertain, leave the prompt for the user.

## Verified Batch 4 baseline

GitHub Actions build #48: PASS.

Bitmovin: automatic YES, video YES, audio YES.
PH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
HH: automatic YES, video YES, audio YES, quality options YES, quality switching YES.
Cloudinary is explicitly skipped and is not a required gate.

Protect:
- yt-dlp first, automatic browser-assisted fallback;
- manual candidate chooser fallback only;
- 720p → 1080p → best below 1080p;
- first-seen HLS/DASH ranking;
- up to 80 candidates stored, strongest 20 shown manually;
- no generic playlist bonus;
- soft demotion of obvious audio-only/video-only children;
- sibling quality URLs;
- video + audio, quality switching, double-tap ±10s, seek preview, rotation.

## Foreground-only playback requirement

Playback must never continue when External Player is not actively in the foreground.

Required:
- stop/pause video and audio when user switches to Vivaldi or any other app;
- stop/pause video and audio when the phone is locked / screen is turned off;
- preserve current tab position when stopped by backgrounding/lock;
- background tab preparation/resolution may continue when Android allows it, but playback may not;
- no background-audio continuation, PiP autoplay, or foreground playback service unless this requirement is explicitly changed later.

Automatic background/lock pause should be distinguishable from deliberate user pause so sensible resume behavior can be implemented when the user returns; exact auto-resume policy remains to be decided during implementation. No media may play while backgrounded or locked.

## Multi-video tabs

Architecture on `main`:
- `VideoTabStore`: process-local sessions.
- `TabbedPlayerApplication`: coordinator above validated player/resolver logic.
- one active ExoPlayer at a time;
- each tab stores resolved-media JSON, title, position and play/pause state;
- `ResolvedMedia.toJson()` preserves selected source when practical;
- bilingual tab switcher; tabs can be selected and closed independently.

Full process-restart persistence remains undecided.

First device QA on 2026-08-13:
- tabs 1→2 YES;
- switch YES;
- position restored YES;
- quality preserved YES;
- close one YES;
- close active OK;
- final close partial failure because old resolver was exposed;
- auto resolver/video/audio/quality options/quality switching/double-tap all YES;
- browser-assisted tab labels generic;
- resolver/loading UI still visible.

Post-QA commit `8be38f33c1a1f225ef555133229669f7e9008b1e`:
- final tab clears to neutral `MainActivity` instead of revealing resolver;
- browser-assisted tab title uses local WebView page title;
- Batch 4 candidate ranking unchanged.

GitHub Actions build #62: PASS.

## Loading/buffering UX

Next major feature:
- `Opening video…` + spinner while resolving/opening;
- `Buffering…` only during real Media3 buffering;
- indicator disappears when ready;
- no resolver/WebView/candidate/debug flicker in normal use;
- diagnostics only via explicit diagnostics/error paths.

## Background add from Vivaldi

Required separate share action: `Add to External Player` / `Añadir a External Player`.

Expected behavior:
- Vivaldi stays foregrounded;
- create a new tab immediately;
- begin resolution/preparation immediately;
- by the time the user switches to External Player, earlier tabs should ideally already be READY;
- selecting a READY tab must use stored resolved media without resolving again.

Preparation states should include `QUEUED`, `RESOLVING`, `READY`, and `NEEDS_ATTENTION`/`ERROR`.

Architecture:
- direct/yt-dlp pre-resolution should use Android-supported background work;
- pre-resolution must not start multiple playbacks;
- store resolved-media JSON when direct resolution succeeds;
- if browser-assisted WebView work is still required, do not falsely mark READY; finish it through clean foreground `Opening video…` UX;
- narrow automatic clear age/cookie consent handling should assist browser-assisted completion under the strict consent policy above;
- background preparation never overrides the foreground-only playback rule.

## Current priority

1. Device-QA build #62 final-tab cleanup + browser-assisted titles.
2. Transparent `Opening video…` / `Buffering…` UX and hide resolver flicker.
3. Conservative automatic clear 18+ age-confirmation and cookie-consent handling.
4. Background `Add to External Player` / `Añadir a External Player` with immediate pre-resolution and per-tab preparation states.
5. Enforce foreground-only playback when app is backgrounded or phone is locked.
6. App icon.
7. Playback speed.
8. App-level volume/mute.
9. Return to existing Vivaldi task/tab.
10. Persistent APK signing.
11. Decide full process-restart tab persistence separately.
12. Brave later.
