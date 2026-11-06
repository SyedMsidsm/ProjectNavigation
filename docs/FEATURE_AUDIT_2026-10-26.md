# RAIN automated feature audit — 26 October 2026

Scope: native Android RAIN, current campus-logo build. The user requested automated checks now and physical checks later. No live camera, microphone or walking session was started for this audit.

## Faults reproduced and fixed

- Rejecting a duplicate recorded-place name previously flushed the pending segment before showing the error. The user could then no longer join the existing place. Validation now happens before modifying the route.
- An invalid alias previously introduced an unwanted node/segment even though the name operation failed. All aliases are now validated first.
- Map reload clears a previously suggested destination; confirming a suggestion checks the current node rather than trusting a reused ID or dereferencing a missing graph. This is a source-review hardening change, not a reproduced device crash.

Four regression tests were added. Two failed against the old implementation and pass after the fix. The others exercise duplicate starting-place rejection and confirmed sensor-turn events replayed through recorded-route navigation.

## Coverage and boundaries

| Feature | Automated evidence | Still requires live acceptance |
|---|---|---|
| Recording, aliases, loops | Geometry, distance retention, duplicate/error recovery and turn-confirmation tests | Actual hand-held walks and loop closure |
| Saved maps | Serialize/load, rename, delete isolation, path validation | Real campus recordings |
| Multi-floor navigation | Floor metadata and explicit transition gating | Stairs/lift/escalator walks |
| Search and local AI | Exact/fuzzy/alias/ambiguity, valid destination IDs, actual on-device model inference | Broader campus-language evaluation |
| Route planning | Graph validation, shortest/risk routing and turn costs | Corrected map and measured passages |
| Guidance/recovery | Missed and wrong turns, overruns, position fixes, confidence, arrival estimates | Spoken/vibration cues and real tracking drift |
| Sign recognition | Bundled OCR on generated sign images, matching thresholds | Live signs, glare and camera permission flows |
| AR | Path geometry, progress, wrong corridors, stale/jumping poses; actual phone GPU shader tests | Floor detection, alignment and walking tracking |
| AI place collection | Actual neural encoder, dataset splits, reference isolation and export | Real reference-photo collection and held-out accuracy |
| Calibration/settings/import/export | Persistence, invalid input, interrupted calibration, graph import validation, session ordering and dataset export | Measured calibration and external document-picker flows |
| Offline operation | App lacks INTERNET permission; actual local OCR/model execution | Offline speech language-pack availability and live speech |
| Theme and screen navigation | Build/lint and phone UI smoke checks | Broader screen-size/accessibility evaluation |

Synthetic events and generated images are not measurements of real walking or camera accuracy. The campus floor map remains a draft with unresolved doorway annotations. This report does not certify every hardware condition or declare the project finished.

## Logs

- `.tools/full-audit-python.log`
- `.tools/audit-regression-before.log`
- `.tools/full-audit-fixed.log`
- `.tools/full-audit-ui.log`

## Test-runner correction

The Gradle connected-test workflow uninstalled the target app, removing its private model file; a later AI test consequently skipped. The model was restored from the verified local artifact and its phone checksum checked. scripts/build.ps1 now builds the test APK, installs both APKs with adb install -r and runs instrumentation directly. This avoids the connected-test teardown that clears app data. No claim of an AI pass is based on the skipped run.

The phone UI smoke check passed for home, recording setup, floor map, saved maps, local assistant, navigation planning, settings and floor-pack import dialog. It did not start recording, microphone or camera capture, or change saved settings. An initial UI assertion used the wrong settings title; it was corrected to the actual title, “Tune your walkthrough”.

## Final results

- 78 core tests: passed, zero failures.
- 31 Python validation/learning/export tests: passed.
- 18 phone integration tests: passed after model restoration, including actual local inference. Final instrumentation log: .tools/full-audit-preserved.log.
- APK build and Android lint: passed.
- Main-screen UI smoke check: passed.
- Corrected APK installed on S24 Ultra. Local model remains present after the corrected test workflow.

Current installable artifact: output/RAIN-demo.apk (with output/RAIN-demo.apk.sha256). The app retains version 0.8.2-campus-logo; this is a locally rebuilt fix, not a published release. Changes have not been pushed to GitHub.
