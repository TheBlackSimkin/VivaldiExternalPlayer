# Temporary Chat Bootstrap — Vivaldi External Player

I am continuing an existing Android project called **Vivaldi External Player**.

Public repository:

`https://github.com/TheBlackSimkin/VivaldiExternalPlayer`

Before every response, verify whether you can still read this repository
directly and briefly state the result. If direct access fails, use the newest ZIP
I provide and do not claim to have read files you could not access.

Before changing code, read `PROJECT_STATE.md` in full. Treat it as the
operational memory/source of truth. Update `PROJECT_STATE.md` and this file when
requirements, architecture, tests, failures, decisions, or next steps materially
change.

## Communication preferences

- Keep the conversation in **English**.
- Windows and Vivaldi UI are normally **Spanish**, so give Spanish UI labels when
  instructions depend on what I see on screen.
- The Android app itself must remain **bilingual English/Spanish**.
- I am not an advanced developer: explain behavior in plain language.
- Candidate-selection UI should tell me what I am looking at in plain language;
  host/protocol/discovery-source jargon is not useful as the main description.
- Source code should contain abundant English comments.
- If you ask me to replace a source file manually, give the FULL replacement
  file in one code block.

## QA response format

Whenever you give me a QA test, always provide two code blocks:

1. A detailed block with the test steps, **Expected**, and a place for **Result**.
2. A separate compact block containing only the short answer format I should
   fill in and send back.

Keep the answer block simple and compact.

## Project goal

This is a personal Android external video player intended primarily for Vivaldi
Mobile Browser. Phase 1 targets Pornhub (PH) and HentaiHaven (HH). The app
receives a browser-shared page URL, resolves an accessible non-DRM media stream,
and plays it with Media3 / ExoPlayer.

## Boundaries

- Do not bypass DRM or obtain DRM keys.
- Do not bypass subscriptions/paywalls, authentication, or regional controls.
- Do not deliberately automate anti-bot challenges.
- Intended target usage is logged out.
- ChatGPT must never inspect, analyze, classify, summarize, or describe PH/HH video content itself.
- I perform PH/HH playback tests on my own device and report only technical results.
- ChatGPT may analyze technical URLs, manifests, containers, codecs, resolutions, request metadata, candidate ranking, and playback errors/status.
- Use safe non-adult proxy pages such as Cloudinary and Bitmovin for direct inspection whenever practical.

## Required workflow

The app should NOT normally make me search manually through many detected
videos.

Expected flow:

1. Share page from Vivaldi.
2. Direct yt-dlp attempt runs first.
3. If it fails, browser-assisted resolution opens automatically.
4. Browser-assisted resolution detects candidates and automatically tries the
   best match after discovery stabilizes.
5. Manual "Choose another video" list is only a fallback if the first attempt
   is wrong.
6. Quality policy: 720p first, otherwise 1080p, otherwise best below 1080p.

Other requirements:

- double-tap left/right = -10/+10 seconds;
- timeline preview where supported;
- quality controls for adaptive manifests and separate per-quality URLs;
- playback speed pending;
- volume/mute pending;
- portrait/landscape rotation;
- final Return to existing Vivaldi task/tab pending.

## Latest test results before Batch 4

### Bitmovin safe proxy

`https://bitmovin.com/demos/hls-fmp4/`

- 5 candidates detected.
- A manually chosen candidate played correct video+audio.
- Recommended candidate was wrong.

This is the key master-vs-child ranking regression.

### Cloudinary safe proxy

`https://cloudinary.github.io/video-player-demo/player.html`

- 20 candidates displayed.
- At least one played.
- Audio was not a useful validation signal in this test.
- The exact count of 20 exposed the old hard candidate limit/deletion behavior.

### Pornhub

`https://www.pornhub.com/view_video.php?viewkey=68913ce2533cb`

- 6 candidates.
- First/Recommended worked.
- HLS host `em-h.phncdn.com`.
- Declared 720p.
- Could not change quality.

Batch 4 should preserve the working 720p selection and add switching between
sibling page-config quality URLs when available.

### HentaiHaven

`https://hentaihaven.xxx/watch/nuki-nuki-zupposism/episode-1/`

- 6 candidates.
- First/Recommended worked correctly.
- HLS host `octopusmanifest.org`.
- Audio/video and quality behavior were correct.

HH is the real-target regression baseline.

## Batch 4 working direction

Read `PROJECT_STATE.md` for full details. Current intended changes are:

- automatic best-candidate first attempt;
- manual candidate chooser only as fallback;
- plain-language candidate descriptions;
- preserve more candidates without deleting the oldest at 20;
- give first-seen HLS/DASH ordering real ranking weight;
- remove the old generic `playlist` ranking bonus;
- soft-demote obvious audio-only/video-only child renditions;
- group page-config quality siblings;
- let PlayerActivity switch between sibling quality URLs;
- update successful playback diagnostics with video/audio/quality results.

Do not skip the GitHub Actions compile gate. If it fails, fix the build before
asking for functional device tests.
