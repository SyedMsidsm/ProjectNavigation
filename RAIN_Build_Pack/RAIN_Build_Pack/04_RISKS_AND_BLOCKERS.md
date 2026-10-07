# RAIN — Risks, Blockers and Likely Errors

Read this before each build day. Each entry lists what goes wrong, how you'll notice, and what to do.

---

## A. Blockers that stop the whole build

| # | Blocker | How you'll notice | Prevention / fix |
|---|---|---|---|
| A1 | **Floor walk delayed** | Day 2 planners have no graph | Do it on Day 1 morning. Until it's done, use a 6-node fake graph in `templates/`. |
| A2 | **Trial approval not requested** | Day 12 comes with no permission to run the headline experiment | Ask Dr. Haneef on Day 0. Prepare a one-page consent form and protocol by Day 8. |
| A3 | **Phone lacks a step detector** | `getDefaultSensor(TYPE_STEP_DETECTOR)` returns null | Accelerometer peak fallback (spec §4.5). Choose test phones on Day 0. |
| A4 | **Only emulator testing** | Wi-Fi scans are empty, no steps, camera is fake | Physical phones only for sensors, camera and Wi-Fi. |
| A5 | **ChatGPT usage limits run out mid-day** | Astra becomes unavailable or slower | Plus plans have Work/Codex limits that Astra uses faster. Budget: one member drives Astra per module, write small focused prompts, reuse the handoff prompt instead of re-uploading everything, and keep a fallback model for simple edits. |
| A6 | **Context drift in long AI chats** | Astra forgets the spec, renames files, or re-invents architecture | One chat per module or day. Start each with the handoff prompt (`05_PROMPT_TEMPLATES.md`). The docs in the repo are the source of truth, not the chat. |
| A7 | **Nobody merges** | Four branches that don't compile together on Day 5 | Merge to `main` every evening. The build must compile before merging. |

## B. Android errors you will almost certainly hit

| # | Problem | Symptom | Fix |
|---|---|---|---|
| B1 | **Gradle / AGP / Kotlin version mismatch** from AI-generated build files | Sync fails; KSP or Compose compiler errors | Create the project from Android Studio's latest "Empty Activity (Compose)" template first, then let Astra add dependencies through `libs.versions.toml`. Check versions in official release notes. |
| B2 | **AI invents or uses outdated APIs** | Unresolved references | Astra's knowledge cutoff is 30 April 2026. Paste the exact compiler error and the relevant docs snippet back into the chat. |
| B3 | **Wi-Fi scan throttling** (4 scans per 2 min in foreground) | `startScan()` returns false; `EXTRA_RESULTS_UPDATED=false`; identical results repeated | Scan every 30 s or more in survey mode. For measurement sessions, set Developer options → Wi-Fi scan throttling as intended and **record the setting**. Always log `results_updated` and `result_age_ms`. |
| B4 | **Empty Wi-Fi scan results** | List is empty although networks exist | Needs ACCESS_FINE_LOCATION granted, plus NEARBY_WIFI_DEVICES on Android 13+, **and** device Location turned on. Check current docs. |
| B5 | **ML Kit model not ready** | First OCR call fails offline | Use the bundled `com.google.mlkit:text-recognition`, not the Play Services variant. |
| B6 | **Camera image rotated** | OCR reads nothing in portrait | Pass `imageProxy.imageInfo.rotationDegrees` into `InputImage.fromMediaImage`. Close `ImageProxy` after every frame, or the preview freezes. |
| B7 | **CameraX frames pile up** | Lag, overheating, dropped frames | `STRATEGY_KEEP_ONLY_LATEST`; run OCR at most every 300–500 ms; stop analysis when the scan sheet closes. |
| B8 | **MediaPipe model won't load** | Asset not found or memory-mapping error | Model in `assets/`; if loading fails, add `noCompress` for `tflite` in `androidResources`. Match the embedder API to the model version. |
| B9 | **Activity Recognition permission** | Step detector silent on Android 10+ | Request `ACTIVITY_RECOGNITION` at runtime. |
| B10 | **Magnetic distortion indoors** | Heading jumps near walls and steel | Don't use a compass. Use relative turns from `TYPE_GAME_ROTATION_VECTOR` and snap to the graph. |
| B11 | **Sensors stop when the screen is off or the app is backgrounded** | Steps missing in logs | Keep the Navigate screen on (`FLAG_KEEP_SCREEN_ON`); no background PDR in v1. |
| B12 | **Room database schema changes** | Crash after update | During build, use destructive migration and re-import CSVs; keep a schema version. |
| B13 | **Phone battery optimisation (Xiaomi, Oppo, Vivo, Realme)** | Scans or sensors killed | Exempt the app from battery optimisation on test phones; note it in the logger instructions for other colleges. |
| B14 | **Different phones behave differently** | Works on one phone, fails on another | Test every feature on both primary phones before marking it done. |

## C. Data and ML pitfalls

| # | Problem | Why it matters | Fix |
|---|---|---|---|
| C1 | **ML Kit cannot run on a laptop** | The OCR benchmark can't be done purely in Python | Use the app's `OcrBenchmarkScreen` to export ML Kit predictions as CSV, then compare in Python. |
| C2 | **Testing on training photos** | Inflated, unpublishable accuracy | Enrolment photos and test photos come from different sessions. Tune on validation data only. |
| C3 | **Too few wrong turns to train the risk model** | v1 model is noise | Keep v0 hand weights; report that. Use grouped cross-validation by walker. Team pilots know the building, so they under-represent errors; say so. |
| C4 | **Identical doors confuse the embedder** | Confident-wrong recognitions | Use the graph prior plus the margin rule; count confident-wrong separately; show it honestly in E5. |
| C5 | **The three planners give the same route** | The navigation comparison has nothing to compare | Run the route-diff script on Day 6 and choose trial routes where they differ. If none differ, that is a real finding about this building. |
| C6 | **LA bit ≠ hotspot** | Enterprise APs (e.g. Ruckus) can broadcast virtual BSSIDs with the LA bit set; phones randomise MACs | Don't equate LA bit with hotspot blindly. Cross-check: consistent presence across days, fixed location, `ssid_class=college`, and a manual walk to the physical AP. |
| C7 | **"3 infrastructure APs" vs "1–2 APs"** | Contradictory numbers in the paper | Group BSSIDs by physical device (shared MAC prefix, same place, same presence pattern). Report physical APs and BSSIDs separately. |
| C8 | **Cached scans mistaken for "hidden APs"** | False research claim | Compare `results_updated`, `result_age_ms`, repeated captures at different times, throttle on vs off. |
| C9 | **Salted hashes break across devices** | The same AP gets different hashes on different phones | Use one project salt shared by the team (kept out of Git). Multi-college exports use the same APK salt, or colleges are analysed only within themselves. |
| C10 | **UJIIndoorLoc "room" level is fuzzy** | Misleading accuracy | Use its SPACEID + building + floor; state the definition. Report random-subset and strongest-k subsets separately. |
| C11 | **Python and Kotlin model outputs differ** | On-device risk ≠ trained risk | Parity test (50 rows, tolerance 1e-6); keep feature order and scaling in the JSON. |

## D. Privacy, ethics and integrity

| # | Risk | Fix |
|---|---|---|
| D1 | **Raw MACs or SSIDs saved or shared** | Anonymise in the logger, and run the regex check on every export. Never paste raw scans into ChatGPT or any external AI tool. |
| D2 | **People in photos** | Shoot when corridors are empty; blur faces before storing; set `contains_people` in the metadata. |
| D3 | **Participant data** | Participant codes only; consent forms stored offline; no names in logs. |
| D4 | **AI-written references** | Never let any AI write citations. Every reference must be opened and read by a team member. |
| D5 | **Overclaiming** | Use the "not claiming" list. Unmeasured numbers never go in slides or papers. |
| D6 | **Unlicensed assets** | Check the licence of every model and dataset (MediaPipe models, PaddleOCR, TrOCR, UJIIndoorLoc) and record it in `DATASHEET.md`. |

## E. Signs you are drifting off plan

- Astra suggests Flutter, a backend server, Firebase, a cloud OCR API, or TFLite conversion → **reject**.
- Someone is training a neural network before Day 5's end-to-end walk works → **stop**.
- A feature is "almost done" for 2 days → cut it to the simplest version that logs data.
- Code exists only in the chat and not in Git → **commit now**.
