# Temporary Chat Bootstrap — Vivaldi External Player

Repository: `https://github.com/TheBlackSimkin/VivaldiExternalPlayer`
GitHub `main` is authoritative. Read `PROJECT_STATE.md` before substantive work. Keep both state files current whenever QA, architecture, failures, priorities, or decisions change.

## Communication / safety
- Conversation English; Vivaldi/Windows UI normally Spanish; Android UI bilingual.
- Explain plainly; user is not an advanced developer. Use connected GitHub tools directly. Source should contain abundant English comments.
- PH/HH are real technical playback targets. URLs/manifests/codecs/resolutions/request metadata/candidate ranking/states/errors/local titles are allowed.
- Never inspect/describe/classify PH/HH media content or thumbnail imagery and never ask the user to provide it.
- Never bypass DRM, paywall/subscription, authentication, regional restriction, CAPTCHA/anti-bot, or import Vivaldi credentials.
- Never add background playback or a second ExoPlayer session.

## Protected baseline
Quality policy: exact 720p -> otherwise 1080p -> otherwise best below 1080p; >1080 only rare fallback. Preserve yt-dlp first/browser fallback, automatic/manual quality, adaptive/sibling quality, double-tap ±10s, seek preview, rotation, bilingual UI, candidate limits/order, page-config families, no imagery ranking, one actual ExoPlayer playback session.

## BG history that must not be forgotten
### #205
Stopped Activity failure: after moving behind Vivaldi, Activity STOPPED at 16:42:01.422 and was destroyed almost immediately. Foreground service protects process importance, not a stopped Activity. Do not intentionally return to the old move-behind-Vivaldi architecture.

### #212
Private virtual display could be created, but Android denied launching the first normal third-party Activity onto it. Do not retry Activity launch there or request privileged `ACTIVITY_EMBEDDING`. A non-Activity private-display window remains only a distinct contingency.

### #215
First real automatic PH BG completion: preparation finished before ExternalPlayer/card open (`READY +9s`), but Vivaldi touch blocked ~3–5s and a brief visible flash occurred.

### #225
Alpha changed to 0.0. Flash fixed, icon PASS, language change PASS, playback PASS, but Vivaldi still blocked ~7s. Automatic/default playback was 1080p although 720p was available and UI showed `Auto - 720p`: strict 720-first FAIL confirmed.

## Build #227 — current validated BG architecture
App code `f53cfcdce45e6e1d982bfee97b042195969134cb`; CI #227 PASS run `31852454518`; artifact `9237954029`; APK SHA-256 `853cc2da965de5c606e2f1cd083f120787d8d55534809c84bdf77a3ccc560170`.

Architecture:
- default-display preparation Activity;
- alpha `0.0f`;
- `FLAG_NOT_TOUCHABLE`;
- `FLAG_NOT_FOCUSABLE`;
- explicit `FLAG_NOT_TOUCH_MODAL`;
- direct + automatic browser fallback start at BG-share time;
- no `moveTaskToBack()`, no virtual-display Activity launch, no ordinary Worker fallback, no PlayerActivity/ExoPlayer during BG prep.

Post-CI inspection confirmed tab creation/preparation request/foreground lease/preparation Activity/direct resolver/browser fallback are all started by `BackgroundShareActivityV2.onCreate()` / preparation `onCreate()`, not dashboard/card/tab opening.

### #227 one-PH user QA — focused BG UX PASS
User followed the clean test sequence without opening ExternalPlayer before Vivaldi and reported: **“all seemed ok to me, i didnt notice anything weird.”** Treat this as PASS for the focused visible/input behavior: no noticed Vivaldi freeze and no noticed flash/weird overlay.

Actual operations log confirms build 227 and intended flags/path:
- 21:30:44.736 share handoff;
- 21:30:44.763 prep launch requested;
- 21:30:44.870 Activity created on display 0 with `alpha=0.0 notTouchable=true notFocusable=true notTouchModal=true`;
- 21:30:44.872 host created; 21:30:44.874 RESOLVING;
- 21:30:45.287 WebView created;
- 21:30:45.295 direct started;
- 21:30:45.315 Activity RESUMED;
- 21:30:46.232 PAUSED;
- 21:30:46.422 STOPPED;
- 21:30:48.248 direct finished;
- 21:30:48.250 browser requested;
- pasted log then truncates during `BROWSER_AUTO_AFTER...` and does not include final READY/ERROR anchor.

Important: despite becoming STOPPED after Vivaldi regained foreground, this same session demonstrably kept running ~1.8s longer through direct completion and browser request. That is materially different from #205's immediate destruction. Do not invent an exact READY timestamp because the pasted excerpt is truncated.

Keep #227 architecture unchanged while serialization QA is pending.

## Quality diagnosis
- #225 definitively reconfirmed Auto 1080 while 720 existed.
- Manual 240 works; manual 480 still needs repair/verification.
- Code diagnosis: browser payload can have a 1080 primary plus sibling `browserVariants` containing 720. Player UI later knows Auto should be 720, but playback has already started from the primary 1080 source.
- Planned fix after serialization QA: normalize automatic browser payloads at `ResolvedMedia.fromJson()`. Prefer 720, else 1080, else best below 1080, else smallest >1080. Only automatic/browser requests are normalized; explicit manual qualities such as 480 stay exact. This also repairs persisted old payloads when reopened.
- Do not claim manual 480 fixed by that change; test/repair separately.

## Other backlog
- Recently closed implemented but explicit device QA pending.
- language selector/change PASS; reopen persistence not separately reported.
- secure GitHub log-report shortcut later; never embed GitHub PAT/token/client secret.

## Current priority
1. Use the **same build #227**; no reinstall/new APK.
2. Test 2–3 PH BG shares close together.
3. Vivaldi must stay responsive and show no flash during each share.
4. Do not open individual tabs to trigger preparation. Later open dashboard and check each tab progressed automatically.
5. Export operations log. Browser discovery is serialized because `ServiceWorkerController` is process-wide; waiting tabs may show `BROWSER_WAITING_FOR_SLOT` but must later progress when the slot is released.
6. If serialization is clean, implement/CI the narrow 720-first normalization, inspect committed path, update both state files, and give one focused quality APK.
7. Then repair/verify manual 480.
8. Later test Recently closed/language persistence.
9. No HH until PH BG + quality blockers clear.

## QA format
Whenever asking the user to test, always provide exactly:
1. one detailed code block with steps, EXPECTED, RESULT;
2. one separate short code block containing only the compact answer format.
