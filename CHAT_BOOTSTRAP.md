# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
`main` is authoritative. 0.3.2 / versionCode 5 is released, device-accepted, and provenance-closed. Read `PROJECT_STATE.md` and this file first.

## Protected rules
Android UI bilingual English/Spanish; comments English. PH/HH are technical playback targets only; never inspect/describe media content or thumbnails. Never bypass DRM/paywalls/auth/geo/CAPTCHA, import credentials, add background playback, or add a second ExoPlayer.

Protected Build #234 preparation path:
`short share Activity -> persistent pending tab -> foreground service -> app-private virtual display -> non-Activity Presentation/WebView -> direct yt-dlp -> serialized browser fallback -> READY / ERROR / NEEDS_ATTENTION`.
Preserve exactly one ExoPlayer, current resolver order/quality policy, Build #278 player/UI baseline, and Build #249 palette.

Permanent signer SHA-256:
`8C:87:E1:F6:A7:A4:87:3F:12:CB:25:BA:34:8B:EF:66:50:57:15:9F:16:A6:5B:90:59:E5:E1:D7:C0:B9:5E:7C`.

## 0.3.2 released baseline
PR #2 merged at `601d32e11355dc9452d01b2f9d4877b1355e1082`.

Final accepted app-code/provenance head `aa81c55c1d7bd1b60283f9721c029cd62bf17d4f`:
- Actions `32679562829` / run #411
- job `97293721788`
- signed artifact `9503753613`
- artifact digest `sha256:de9cdf59d5e2572b5ce925294d3e51da538942b630ba95a321ddf3e18fb62225`
- signed APK SHA-256 `11d105b171fdfd922ed1c246236d39fa751dc989e69b48b059adbe62c823f4b8`
- debug artifact `9503754068`
- build/sign/package/alignment/upload PASS

GitHub compare from that accepted head to the post-merge handoff head showed only `PROJECT_STATE.md` and `CHAT_BOOTSTRAP.md` changes, so no app code changed after the green #411 build. This closes release provenance without relying on the unavailable push-run listing.

## Critical accepted regression fix
Candidate 6 barred `PlayerActivity` from the hidden/default-display preparation path. The exact former Revive All + foreground playback failure is device-PASS, including continued background revival while watching. Do not alter this path without regression evidence.

## Candidate 7 final device QA
1. Non-fullscreen Player title — **PASS**.
2. Failed-player recovery UI — **PASS**.
3. Refresh source stays in Player / same persistent-tab single-ExoPlayer path — **PASS**.
4. Revive All + foreground playback sanity — **PASS**.

Never translate/infer/invent titles; use legitimate resolved/stored source metadata only.

## START HERE — 0.3.3
Issue #3: **Add user-initiated “Report log on GitHub” shortcut**.

Implementation boundaries:
- user initiated only; never upload/post logs automatically;
- keep existing `OperationLog.share(...)`;
- reuse sanitized OperationLog/build metadata;
- no thumbnails, media frames, page/body text, cookies, request headers, Authorization values, credentials, or browser credentials;
- user must review what will be shared before external navigation/submission;
- minimal prefilled GitHub issue context by default; do not silently place the full operations log into a public issue body;
- bilingual English/Spanish UI;
- preserve Build #234 background preparation, current resolver policy, return-to-Vivaldi behavior, and exactly one ExoPlayer.

Create a dedicated 0.3.3 branch from the post-release `main` head before changing app code.

## QA format
When asking for APK QA: exactly one detailed steps/EXPECTED/RESULT code block, then one compact-answer code block. No extra code blocks.
