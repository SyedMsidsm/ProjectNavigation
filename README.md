# RAIN · Risk-Aware Indoor Navigation

Native Android presentation prototype for Samsung S24 Ultra and other Android 8+ phones. Kotlin, Jetpack Compose, CameraX, bundled ML Kit text recognition, Room/KSP and a separately tested Kotlin navigation core. Everything needed at runtime works offline; initial dependency downloads need internet.

## Start here

**AR walking update (0.6.0):** open **AR walking navigation**, confirm the starting location and align once. ARCore then updates route progress, turns and a rolling arrow window while walking. Returning to the map retains progress; tracking loss requires a room-sign check. See [the walking guide and physical acceptance checklist](docs/AR_WALKING.md). Campus alignment and distance accuracy still need measured field trials.

**AI collection update (0.5.0):** open **AI place collection** to capture labelled reference/training/validation/test views with offline MobileNetV3 and OCR. Photos are saved only on explicit confirmation and can be exported as a study ZIP. See [the collection and training guide](docs/AI_COLLECTION.md). The fusion preview is untrained; campus training and evaluated model deployment remain pending.

**Ground-floor update (0.4.0):** the supplied college drawing is bundled under **Floor maps → College ground floor**, with a zoomable original plan and a draft routing graph. Doorways and distances remain provisional pending a marked corridor/door plan. See [ground-floor integration and next steps](docs/GROUND_FLOOR.md).

The supplied floor is **synthetic**, approved for the presentation. It has ten rooms and alternative corridors. It is not a map of MITE. No official floor plan is needed for the later campus build: a sketch with room labels, junctions, directions and approximate segment distances is enough.

1. On this Windows machine, open PowerShell in this folder and run `./scripts/build.ps1`.
2. Connect the S24 Ultra with USB debugging enabled, approve its computer-authorisation dialog, then run `./scripts/install.ps1` to install the verified APK without rebuilding (or `./scripts/build.ps1 -Install` to build and install together).
3. Open **RAIN**, keep **Main entrance → CSE Lab 204**, and compare the route cards. Start navigation with the labelled demo controls. Use **Scan a sign** against [the demonstration signs](docs/demo-signs.html) to try real on-device OCR.
4. Follow [the three-minute runbook](docs/DEMO_RUNBOOK.md). Permission denial retains manual start selection and rehearsal controls.

The verified APK is copied to `output/RAIN-demo.apk`, alongside its SHA-256 checksum. The original build output is `android/app/build/outputs/apk/debug/app-debug.apk`. Phone camera/sensor acceptance requires running it on the actual S24 Ultra. A desktop build or a simulated walk is not evidence of physical positioning accuracy.

## Another computer

**Android Studio:** open the `android/` folder, use JDK 17, install SDK 36 and Build Tools 35.0.0 when prompted, sync, and run on the connected phone. Dependencies are pinned in `android/gradle/libs.versions.toml` as a compatible baseline.

**Windows without Android Studio:** run `./scripts/bootstrap.ps1` once. It downloads official JDK/Gradle/Android tooling into the ignored `.tools/` folder, verifies archive checksums, and prompts for Android SDK licences. Then run `./scripts/build.ps1 -Install`. No global installation or cloud account is needed. The `-AcceptSdkLicenses` option is available for an unattended setup when you accept those licences.

**macOS/Linux with JDK 17 and Android SDK installed:** run `cd android`, then `sh gradlew :core:test :app:assembleDebug :app:lintDebug`. Set `ANDROID_HOME` or create an untracked `local.properties` with `sdk.dir`.

## What this slice implements

Version **0.3.0-field-tools** adds measured-walk calibration, saved settings, optional offline voice/vibration, validated floor imports, an [offline floor editor](docs/floor-editor.html), observer notes and session summaries. See [the field-work guide and remaining work](docs/FIELD_TOOLS.md). The college map and physical calibration are still pending; these tools do not establish positioning accuracy.

AR requires Google Play Services for AR. Version 0.6 replaces the old stationary preview with continuous visual-inertial progress. Historical preview checks are preserved in [the earlier phone report](docs/AR_PHONE_TEST_REPORT.md); they do not verify the new walking behaviour. Follow [the current AR walking guide](docs/AR_WALKING.md).

- Validated CSV floor graph, reverse-direction bearings and turn-aware shortest, weighted-simplest and risk-aware planners.
- Risk-aware routing balances distance, hand-weighted uncertainty and scan cost. A scan is useful only at a registered readable sign. The model is loaded from JSON; its scores are **uncalibrated heuristics**.
- Search by room/name/alias, route comparison, floor schematic and navigation instructions.
- Bundled offline camera OCR with conservative text matching and explicit location confirmation. Similar room numbers and ambiguous signs are rejected rather than silently accepted.
- Foreground step sensing, accelerometer fallback, relative-turn sensing, step-length setting, route progress, scan prompts, off-route checks and scan-based replanning.
- Clearly labelled rehearsal controls; synthetic and live events remain identifiable in exports.
- Local JSONL sessions and Android share-sheet export. No server, Wi-Fi scanning, accounts or API keys in this demo.

Door appearance embeddings/fusion, Wi-Fi survey collection, trained risk models and research evaluations remain in the immediate follow-up. [The execution plan](docs/EXECUTION_PLAN.md) defines the 50% demo gate and the 80% follow-up without conflating implemented code with measured research acceptance. [Decisions](docs/DECISIONS.md) records corrections to the original plan.

## Validate and replace the floor

Use the header-only CSVs in `data/templates/` for your floor walk. Keep a door as a graph node and split corridors at doors; connect branches through junction nodes. Bearings increase clockwise from the building's chosen north; the reverse traversal adds 180°.

```powershell
python -m unittest discover -s python/tests -v
python python/rain_offline/validate_graph.py --rooms android/app/src/main/assets/rooms.csv --edges android/app/src/main/assets/edges.csv --svg output/demo-floor.svg
./scripts/build.ps1 -CoreOnly
```

With a physical phone connected and authorised, `./scripts/build.ps1 -PhoneTests` additionally runs Android integration tests for bundled OCR on three generated signs, Room/model loading, sensor availability and session logging. These checks do not substitute for camera-photo accuracy or a measured walking trial. Results and observed UI checks are recorded in [the phone test report](docs/PHONE_TEST_REPORT.md).

Validate real CSVs, then replace `android/app/src/main/assets/rooms.csv` and `edges.csv`, update the synthetic-data labels/provenance, and rebuild. The Room cache refreshes when the bundled CSV digest changes. Do not put raw photos, Wi-Fi identifiers, salts, consent forms or participant names into source control.

## Repository

`android/core/` pure Kotlin algorithms and tests · `android/app/` Android adapters and UI · `python/` standard-library validation tools · `docs/` blueprint, audit, runbook and data notes · `scripts/` reproducible setup/build · original Claude files retained unchanged for provenance.
