# RAIN progress and acceptance ledger

## Recorded routes and local assistant — 26 October 2026

Version 0.8.0-trails-assistant (versionCode 8) is installed and launched on the Samsung S24 Ultra. The native app now has the four reference workflows with RAIN branding: recording, navigation, map viewing and saved maps, plus campus tools and a local assistant. Features include gravity-projected gyro turn candidates, two-step turn confirmation, named places/aliases, explicit loop joins, separate saved recordings with rename/delete, floor-aware graph serialization, manual stairs/lift/escalator transitions, fuzzy destination search, on-device-only speech input, a large direction arrow, heuristic confidence and manual position/arrival confirmation. Multi-floor routes use map guidance; AR remains restricted to single-floor routes.

Verification: 74 core tests passed, including 10 new workflow tests. APK assembly and lint passed in `.tools/trails-acceptance-build.log`. The Android test APK initially failed in the earlier invocation; a retry with the configured Java/SDK/Gradle environment succeeded in `.tools/trails-test-repair.log`. All 18 connected integration tests passed in `.tools/trails-device-tests.log`, including four new storage/serialization checks and real local language-model inference. The installed app's home, recording setup, saved-map empty state and local assistant were inspected. Through the visible assistant UI, “Where can I borrow a book” produced “Library · floor 0” and required **Use this destination** confirmation. No physical walking, speech or live-camera trial was performed for this release.

The separate Gemma 4 E2B model was downloaded from the official LiteRT Community repository, checksum-verified and prepared in RAIN's private storage. `.tools/assistant-prepare.log` records successful transfer and device checksum verification. Models and reference source remain under ignored `.tools/`; neither is part of the source commit. No cloud model or runtime Internet permission was added. See `TRAIL_WORKFLOWS.md` for model/runtime sources and limits.

APK SHA-256: `f5ccd60b9205729c2868f81c5c355a8b78d81fb1fcaa51d5eaa5ea780cb4e037`. `output/RAIN-trails-assistant.apk` preserves this build alongside `output/RAIN-demo.apk`. Existing marked-floor edits remain a draft; unresolved doorway/opening confirmations and the stopped AR walking test are still pending. Changes have not been committed or pushed during this update.

Updated 7 October 2026. This file separates implementation from evidence. The target phone is Samsung S24 Ultra. A ground-floor architectural image has now been supplied; surveyed corridor distances and verified doorway locations are still pending.

## AR walking upgrade on 7 October

Version 0.6.0-ar-walking replaces the static preview with ARCore translation-based route progress, a rolling arrow window through successive turns, distance/turn guidance, inherited voice/haptics and return-to-map progress transfer. Phone step tracking is suspended only as an input source while ARCore supplies progress, preventing duplicate movement. Tracking loss, pose discontinuity, sustained off-route movement and activity interruptions require an explicit location check. Initial location and corridor heading still require user confirmation. See `AR_WALKING.md` for the workflow, thresholds and physical acceptance checklist.

APK assembly and lint pass. All 64 core tests pass, including 11 new AR walking regressions covering progress beyond six metres, left/right turns, partial starting edges, rounded corners, U-turns, wrong corridors, missed turns, backtracking, timestamp gaps, pose jumps, route-leg selection and return-to-map progress. The initial sandbox build could not connect to its local Gradle service; the retry with the necessary access succeeded. USB debugging was authorised for the connected checks below. The user is available for a walking test; measured segment lengths and turn direction are still requested. No new physical AR accuracy result is claimed yet.

Final automated/device result: all 13 connected integration tests passed on the S24 Ultra, including the updated AR graphics smoke test. The release installed and launched successfully; Android reports versionCode 6 / versionName 0.6.0-ar-walking. The tested release is preserved as `output/RAIN-ar-walking.apk` with a SHA-256 file, and is also the current `output/RAIN-demo.apk`. Logs: `.tools/ar-walking-build.log`, `.tools/ar-walking-phone-tests.log`, `.tools/ar-walking-install.log`.

Physical acceptance remains pending. In the later test attempt the user confirmed a five-metre stretch, right turn and another five metres. The matching Room 203 → Lab junction → CSE Lab 204 sample route was selected and the AR screen opened with the correct instruction. The user stopped the test before arrow alignment or a measured walk was confirmed. The active journey was ended and RAIN returned to the planning screen. No walking result, arrow stability, real distance accuracy or new floor alignment is claimed. No camera frames were saved during these checks.

## AI phone-test result recovered on 6 October

Follow-up: USB debugging was approved and the tested 0.5.0-ai-collection APK was successfully installed and launched. Package versionCode 5 was verified. The user requested installation only while in class, so live capture checks remain deferred and no new collection data was saved.

The previously pending connected run completed successfully: `.tools/ai-collection-phone-final.log` and its XML report show 13 tests, zero failures and zero errors. This includes real bundled neural inference and isolated dataset persistence/export checks. The tested APK matches the current build-output hash. The older pending-device-test statements below are historical; live camera capture/review/discard and campus accuracy remain unverified. USB debugging required renewed authorisation when the user reconnected on 6 October.

## AI data collection on 3 October

The user selected **build the data-collection workflow first**. Version 0.5.0-ai-collection implements explicit CameraX captures, bundled offline MobileNetV3 embeddings, OCR/visual preview rankings, independently selected room labels, separate reference/train/validation/test sessions, private photo/feature persistence, study separation and a user-initiated ZIP export. An offline logistic-fusion trainer validates provenance/splits, trains only on training captures, chooses an acceptance threshold using validation captures and compares OCR/visual/fusion on test captures. It does not automatically deploy a learned model.

Verification: 53 core tests and 29 Python tests pass. Android APK assembly and lint pass with zero lint errors. The new device-test APK compiles after retrying a cache-permission failure. Actual connected tests and installation of this release are pending: the phone changed from authorised to unauthorised during the session and the test runner reported no online devices. The earlier 11-test phone pass applies to 0.4.0, not this AI release. The two new phone tests use isolated synthetic images/storage and have not yet run successfully. See `.tools/ai-collection-phone-retry.log` and `AI_COLLECTION.md`.

The initial escalated test command was delayed by an automatic approval-review timeout; a later retry completed compilation. No real campus photos, trained campus parameters or physical accuracy results have been fabricated. Remaining work includes actual data collection, trained-model evaluation/deployment, corrected floor geometry, measured walking/AR checks and research trials.

## Ground-floor integration on 24 September

Final build/lint and 50 core, 25 Python and 11 phone integration tests passed. The 0.4.0 APK was installed on the S24 Ultra and the draft map activated. The original-plan viewer and default lobby-to-library selection were checked on the phone. Desktop overlay review used an isolated map-only preview folder after automatic review rejected serving the entire project root; the broad server was stopped. This is software acceptance, not physical route validation.

Version 0.4.0-ground-floor-draft adds a selectable 74-node/74-segment draft based on the supplied image, 33 room destinations, a zoomable original plan, explicit plan-derived provenance and visible draft warnings. Unknown sign conditions remain unverified, so no automatic scan prompts are scheduled from assumed signage. The source image, CSVs, import pack, review overlay and survey checklist are saved. The user confirmed corridor access and free courtyard movement, but door placements require their marked-up plan before the graph can be finalized. See `GROUND_FLOOR.md`. Appearance fusion, learned risk training and physical evaluation remain outstanding.

## AR phone verification on 23 September

**Later update — field tools 0.3.0:** walking calibration, persistent settings, offline voice/haptic adapter, four exposed prompt policies, validated atomic floor imports, browser floor editor, observer tags and descriptive session summaries are now implemented. Fifty core tests, 23 Python tests and eight S24 Ultra integration tests passed. The updated APK was installed and launched. A zero-step calibration was rejected without a save action; no measured calibration was saved. Editor creation/export and map-management UI checks passed. See `FIELD_TOOLS.md` for instructions, acceptance limits and the complete remaining-work list. The latest `RAIN-demo.apk` is the field-tools release; `RAIN-ar-preview.apk` retains the older AR release.

Installed the AR build on the S24 Ultra and passed all five Android integration tests, including real GPU shader compilation and optional AR configuration. The live AR UI reached Arrows aligned. CameraX scanner and AR camera handoff, camera permission denial/retry, and preservation of the stationary demo route passed. Fixed a duplicate-home-screen issue on background relaunch with singleTask launch behaviour; the final phone regression preserved the original route and left one MainActivity. Build/lint passed; final log is `.tools/ar-phone-final-check.log`.

The updated APKs are `output/RAIN-demo.apk` and `output/RAIN-ar-preview.apk`. See `AR_PHONE_TEST_REPORT.md`. User confirmation of arrow appearance/stability, covered-camera tracking loss, physical rotation recovery and live-motion pause remains pending. These results do not establish real-campus AR accuracy. The records below retain the previous stages' evidence.

## AR preview addition on 22 September

Version 0.2.0-ar-preview adds an optional AR activity with a live camera background, horizontal floor detection, manual heading alignment and local floor arrows over at most the next 6 m of the selected route. Navigation progress and phone-motion tracking pause during the preview. Tracking loss clears the anchor; return/background/rotation require fresh alignment. Map navigation remains available on unsupported devices. See `AR_DEMO.md` for setup, limitations and physical checks.

The final AR build passed all 44 core tests (39 existing plus 5 geometry tests), APK assembly and Android lint with zero errors (23 warnings and 5 hints, including version and localisation suggestions). Final verification is recorded in `.tools/ar-final-build.log`. The previous phone-verified APK is retained as `output/RAIN-demo-pre-ar.apk`; the new version 0.2.0-ar-preview APK is available as `output/RAIN-demo.apk` and `output/RAIN-ar-preview.apk`, with SHA-256 checksum files.

At the 22 September handoff, physical AR checks were pending because the user deferred reconnecting the phone. The 23 September results above supersede that installation status. The three connected tests and live ROOM 201 result below describe the 21 September baseline.

## Implemented in this build

- Native Android project, reproducible setup/build scripts, compatible pinned dependency catalog and offline bundled OCR dependency.
- Synthetic map assets: 15 nodes, 15 segments and 10 rooms; alternative corridors support route-comparison demonstration.
- Pure Kotlin graph, planners, recognition matcher and navigation logic; Android camera, motion, database, logging and Compose adapters.
- Standard-library Python CSV validator, schematic SVG output, JSONL schema/privacy checker and automated regression tests.
- Audit/decisions, 48-hour execution plan, 80% follow-up gates, three-minute runbook, printable synthetic signs and data inventory.

## Verification at handoff

| Check | State | Evidence / remaining work |
|---|---|---|
| Supplied context reviewed | Complete | Six Markdown files read; duplicate pack hashes checked; both PDFs have identical extracted text; architecture diagram visually reviewed |
| Synthetic graph validation | Passed | 15 nodes / 15 edges; all node pairs reachable |
| Python validation tests | Passed | 19 regression tests, including schema 0.2 nullable estimates and unverified arrivals |
| Kotlin core tests | Passed | 39 JUnit tests; all 225 node pairs satisfy lambda-zero shortest-route parity; sample routes and scan prompts verified |
| Android build | Passed | `:core:test :app:assembleDebug :app:lintDebug`; zero lint errors (18 warnings and 5 hints, mostly pinned-version upgrade suggestions) |
| Installable APK | Built | `output/RAIN-demo.apk` and SHA-256 checksum; native MainActivity included; Internet permission removed from merged APK |
| S24 Ultra install / camera / sensors | Partial physical acceptance passed | Installed and launched on Android 16; user confirmed live ROOM 201 recognition; all 3 connected integration tests passed, including sensor availability. Measured walking and turn calibration remain pending |
| Real campus routes | Pending follow-up | Requires floor sketch, labels and measured distances |
| Learned model / recognition fusion / Wi-Fi survey | Deferred | Acceptance gates in EXECUTION_PLAN.md; not represented as finished |
| Research results / participant trials | Not started in this build | Requires measured data, approved protocol and independent ground truth |

Do not convert generated source files into a percentage of validated research completion. Build log: `.tools/verification-build.log`; JUnit reports: `android/core/build/test-results/test/`; lint: `android/app/build/reports/lint-results-debug.html`.

## Next actions by owner

- A: rehearse the installed demo using the runbook; launch, route comparison and simulated recovery have passed.
- B: expand the successful ROOM 201 camera check to additional signs and lighting conditions.
- C: calibrate physical steps/turns; the exported 16-event simulated recovery session already passed JSONL checks.
- D: organise the measured-floor walk for the immediate follow-up; keep independent test captures separate.

## Phone verification record

USB authorised and verified on Samsung SM-S928B, Android 16 / API 36, app 0.1.0-demo, on 21 September 2026. Installed the verified APK and exercised route comparison, simulated wrong-turn recovery, Library anchoring and replanning. The resulting 16-event session passed the export checker. The user confirmed ROOM 201 through the live scanner. Three Android integration tests passed, including bundled OCR on three generated signs, hardware sensor availability and session writing. See PHONE_TEST_REPORT.md for evidence and limitations. No measured physical walking trial or real-campus accuracy result is claimed.

## Theme update — 26 October 2026

RAIN 0.8.1-olive-theme (version code 9) applies the supplied visual reference through olive/cream surfaces, lime accents, rounded components and a redesigned home screen. The supplied RAIN artwork is used on the home screen and as an adaptive launcher icon. The home destination prompt opens the existing planning workflow. Navigation and sensing logic are unchanged.

Validation: scripts/build.ps1 -Install completed successfully (core test task, APK assembly and lint); installed on S24 Ultra. Home and floor-map screenshots inspected. This update does not constitute a new physical navigation test. APK: output/RAIN-olive-theme.apk. Home preview: output/theme-home.png.

## Replacement logo — 26 October 2026

RAIN 0.8.2-campus-logo (version code 10) replaces the previous wordmark artwork with the user-supplied campus/pin/radar image. The home logo shows the full image without the earlier zoom. The adaptive launcher icon uses a dark navy background and inset artwork. Source artwork is included unchanged.
Validation: build, core test task and lint succeeded; installed on the connected S24 Ultra. Home-screen appearance checked. Build log: .tools/logo-build.log.

## Automated feature audit — 26 October 2026

Fixed duplicate/invalid recorded-place edits mutating a trail before rejection, guarded stale assistant suggestions, and changed phone testing to in-place installation to preserve app-private data. Added four core regression tests (two reproduced failures before the fix). Final result: 78 core, 31 Python and 18 actual device tests pass; build/lint and main UI screen checks pass. The local model was restored after the old connected-test runner removed it, and actual inference passed again. Fixed APK installed. Physical tests deferred at the user's request. See FEATURE_AUDIT_2026-10-26.md for feature coverage and limitations.
