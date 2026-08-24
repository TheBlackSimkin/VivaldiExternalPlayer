# Vivaldi External Player — Project State

`main` is authoritative. 0.3.2 / versionCode 5 is released, device-accepted, and provenance-closed. Read this file and `CHAT_BOOTSTRAP.md` before substantive work.

## Protected architecture / safety
- Android UI bilingual English/Spanish; comments English.
- PH/HH are technical playback targets only: URLs, manifests, codecs, resolutions, request metadata, resolver/candidate ranking, playback states/errors, local titles. Never inspect/describe media content or thumbnail imagery.
- Never bypass DRM/paywalls/auth/geo/CAPTCHA, import browser credentials, add background playback, or add a second ExoPlayer.
- Protected Build #234 preparation path:
  `short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.
- Preserve exactly one ExoPlayer and the current resolver/quality policy.
- Build #278 remains the accepted player/UI baseline; Build #249 palette remains protected.
- Never machine-translate, infer, rewrite, or invent titles. Prefer legitimate source-provided metadata from the app-selected language source/site variant where available; otherwise preserve original metadata/title.

Permanent signer certificate SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

## 0.3.2 final release
PR #2 (`0.3.2 correctness, privacy and dashboard cleanup`) merged into `main` at `601d32e11355dc9452d01b2f9d4877b1355e1082` after Candidate 7 device QA passed all remaining merge-gate checks.

Final accepted app-code/provenance head: `aa81c55c1d7bd1b60283f9721c029cd62bf17d4f`.
- Actions run `32679562829` / run #411
- job `97293721788`
- signed release artifact `9503753613`
- GitHub artifact digest `sha256:de9cdf59d5e2572b5ce925294d3e51da538942b630ba95a321ddf3e18fb62225`
- extracted signed APK SHA-256 `11d105b171fdfd922ed1c246236d39fa751dc989e69b48b059adbe62c823f4b8`
- debug artifact `9503754068`
- build/sign/package/alignment/upload: **PASS**

Release provenance is closed because GitHub compare from the accepted head to the post-merge handoff head showed only `PROJECT_STATE.md` and `CHAT_BOOTSTRAP.md` changes; no app source, resources, manifest, Gradle, or workflow files changed after the green #411 build.

Direct APK update remains supported with the permanent signer. Material Files installs successfully; Files by Google was the installer-specific failure path observed during 0.3.x QA.

## Accepted 0.3.2 regression fixes
- Candidate 6 resolved the Revive All foreground-interference blocker by preventing `PlayerActivity` from acting as a hidden/default-display preparation host. Only the protected service/private-display revival coordinator continues true background revival. Do not alter this path without regression evidence.
- Candidate 7 fixed the non-fullscreen player title by sourcing title text from exact `ResolvedMedia` JSON first, persistent-tab title second, generic Activity title last, and attaching the title view directly to `activity_player`.

Final Candidate 7 device QA:
1. Non-fullscreen Player title fix — **PASS**.
2. Failed-player recovery UI — **PASS**.
3. Refresh source remains in Player and reloads the same persistent-tab/single-ExoPlayer path — **PASS**.
4. Revive All + foreground playback sanity/regression check — **PASS**.

Other accepted 0.3.2 areas include dashboard return anchoring, language-aware source/title behavior, active-tab multi-select, per-tab playback preferences, search/filter accordions, Diagnostics/History access, Recently Closed up to 100 entries, consolidated dashboard gear menu, privacy curtain + system authentication, decoder fallback, and direct APK validation improvements.

## Roadmap correction
The `Report log on GitHub` / GitHub issue-reporting feature is **NOT PLANNED**.
- Issue #3 is closed as not planned.
- PR #4 is closed without merge.
- The abandoned `work/0.3.3-report-log-github` branch was reset back to the released baseline so its implementation is not an active code line.
- Do not reintroduce GitHub-reporting behavior unless the user explicitly requests it in a future conversation.

## Next development — 0.3.3
Start 0.3.3 cleanly from the current post-release `main` head.
- version target: 0.3.3 / versionCode 6;
- no feature scope is assumed yet;
- keep all 0.3.2 accepted behavior and protected architecture intact until a new feature/fix is explicitly chosen.

Return-to-Vivaldi behavior remains unchanged.

## QA format
When asking the user to test an APK: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
