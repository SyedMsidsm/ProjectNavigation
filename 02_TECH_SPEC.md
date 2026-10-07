# RAIN — Technical Specification

This is the source of truth for architecture, data formats and algorithms. Any change needs team approval and a note in `docs/DECISIONS.md`.

---

## 1. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| App language / UI | Kotlin, Jetpack Compose, Material 3 | Single-activity app, Navigation Compose |
| Camera | CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) | `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` |
| OCR | ML Kit Text Recognition v2, **bundled** Latin model (`com.google.mlkit:text-recognition`) | Works offline with no model download |
| Doorway embedding | MediaPipe Tasks Vision `ImageEmbedder` (`com.google.mediapipe:tasks-vision`) | Pretrained MobileNet-V3 embedder `.tflite` in `assets/` |
| Storage | Room (SQLite) + KSP | CSVs imported from `assets/` on first run |
| Sensors | `SensorManager`: `TYPE_STEP_DETECTOR` (fallback: accelerometer peaks), `TYPE_GAME_ROTATION_VECTOR` | Relative heading; avoids indoor magnetic distortion |
| Wi-Fi | `WifiManager.startScan()` + `SCAN_RESULTS_AVAILABLE_ACTION` | Survey mode only, plus an optional feature |
| Models on device | Plain Kotlin evaluation of JSON-exported models | No TFLite conversion of custom models |
| Tests | JUnit 5 (or JUnit 4), kotlinx-coroutines-test | Pure logic has no Android imports |
| Offline | Python 3.11, pandas, numpy, scikit-learn, lightgbm, matplotlib, jupyter, mediapipe, paddleocr, transformers (TrOCR) | `python/requirements.txt` |
| Version control | Git + GitHub private repo | `main` protected; feature branches per member |

Min SDK 26, target the latest stable SDK. Confirm every library version against the official docs, then record it in `gradle/libs.versions.toml`.

## 2. Repository layout

```
rain/
├── android/
│   └── app/src/main/
│       ├── assets/
│       │   ├── rooms.csv  edges.csv  doors_photos.csv
│       │   ├── risk_model.json          # v0 hand weights, later trained
│       │   ├── embedder.tflite          # MediaPipe image embedder
│       │   └── enrolment/<node_id>/*.jpg
│       └── java/in/mite/rain/
│           ├── core/        # pure Kotlin: no Android imports
│           │   ├── graph/       Graph.kt, CsvGraphLoader.kt, TransitionGraph.kt
│           │   ├── planning/    Planner.kt, ShortestPlanner.kt, SimplestPlanner.kt,
│           │   │                RiskAwarePlanner.kt, RouteInstructions.kt
│           │   ├── risk/        RiskFeatures.kt, RiskModel.kt, JsonLogisticModel.kt,
│           │   │                JsonTreeEnsembleModel.kt
│           │   ├── recognition/ RoomNameMatcher.kt, FusionRecognizer.kt, Embeddings.kt
│           │   ├── motion/      PdrTracker.kt, TurnDetector.kt, GraphSnapper.kt
│           │   ├── position/    PositionBelief.kt
│           │   ├── guidance/    PromptPolicy.kt, OffRouteDetector.kt, NavigationSession.kt
│           │   └── survey/      MacUtils.kt, Anonymiser.kt
│           ├── android/     # Android adapters
│           │   ├── camera/      CameraAnalyzer.kt, MlKitOcrEngine.kt, MediaPipeEmbedder.kt
│           │   ├── sensors/     StepSensorSource.kt, RotationSource.kt
│           │   ├── wifi/        WifiScanSource.kt
│           │   ├── data/        RainDatabase.kt, entities, DAOs, AssetImporter.kt
│           │   └── logging/     SessionLogger.kt (JSONL), ExportManager.kt
│           └── ui/          HomeScreen, DestinationPicker, NavigateScreen, ScanSheet,
│                            EnrolScreen, SurveyScreen, OcrBenchmarkScreen, DebugScreen
├── python/
│   ├── requirements.txt
│   └── rain_offline/
│       ├── validate_graph.py        ocr_benchmark/        ap_scarcity/
│       ├── churn_analysis/          hotspot_persistence/  risk_model/
│       ├── recognition_eval/        planner_eval/         dataset_package/
├── data/                # gitignored except templates/ and anonymised exports
│   ├── templates/  raw_photos/  exports/
├── docs/                # these build-pack files + DECISIONS.md + DATASHEET.md
└── .gitignore           # must include: local.properties, secrets.properties, data/raw_*, *.salt
```

## 3. Data schemas

### 3.1 `rooms.csv` — graph nodes (one row per node)

| Column | Type | Example | Notes |
|---|---|---|---|
| node_id | string | `N07` | Unique, stable |
| node_type | enum | `door` | `door`, `junction`, `corridor_point`, `entrance`, `stairs`, `washroom` |
| room_name | string | `Room 204` | Blank for non-doors |
| sign_text | string | `ROOM NO. 204 CSE LAB` | Exactly as printed; blank if missing |
| aliases | string | `204\|CSE Lab 2` | Pipe-separated alternatives users might search |
| x_m, y_m | float | `12.5`, `3.0` | Approximate local metres from the floor walk; origin at the main entrance |
| corridor_side | enum | `left` | Side of the corridor when walking the edge in the from→to direction; blank for non-doors |
| sign_condition | enum | `printed_faded` | `printed_clear`, `printed_faded`, `handwritten`, `glare`, `obscured`, `missing` |
| similar_doors_nearby | int | `3` | Doors within 6 m that look alike |
| notes | string | | Free text |

### 3.2 `edges.csv` — walkable corridor segments

| Column | Type | Example | Notes |
|---|---|---|---|
| edge_id | string | `E12` | Unique |
| from_node, to_node | string | `N07`, `J02` | Must exist in `rooms.csv` |
| length_m | float | `8.4` | Tape, laser, or counted steps × calibrated step length |
| bearing_deg | float | `90` | Walking direction from→to, 0 = the building's "north" wall. Only relative angles matter |
| corridor_id | string | `C-EAST` | Groups straight corridors |
| bidirectional | bool | `true` | |
| has_stairs | bool | `false` | |
| notes | string | | |

**Rule:** doors sit on the graph as nodes. Split corridor edges at every door, so a corridor with 4 doors becomes 5 edges.

### 3.3 `doors_photos.csv` — photo metadata

`photo_id, node_id, capture_type (nameplate | enrol | test), sign_condition, device_model, lighting (day | evening | artificial), distance_m, angle_deg, flash (false), timestamp_iso, contains_people (false), file_name`

### 3.4 Wi-Fi survey records — `wifi_scans.jsonl`, one JSON object per BSSID per scan

```json
{"session_id":"S2026-09-20-C1","scan_id":42,"ts_ms":1758350000000,
 "device_model":"...","android_api":34,"node_id":"N07","college_code":"MITE",
 "bssid_hash":"a1b2c3d4e5f60718","la_bit":true,"oui":null,
 "rssi":-67,"freq_mhz":5180,"is_5ghz":true,
 "ssid_present":true,"ssid_class":"other",
 "results_updated":true,"result_age_ms":850,"throttle_setting":"unknown"}
```

- `bssid_hash` = first 16 hex characters of SHA-256(salt + lowercase MAC). The salt is read from `secrets.properties` and is never committed.
- `la_bit` = `(firstByte & 0x02) != 0`.
- `oui` = first three octets **only when `la_bit` is false** (vendor identification for infrastructure), otherwise `null`.
- `ssid_class` = `college` if the SSID matches the configured list of college network names (the comparison happens in memory), otherwise `other`. **The SSID itself is never written anywhere.**
- `results_updated` = `EXTRA_RESULTS_UPDATED` from the broadcast. `false` means Android returned cached results.
- `result_age_ms` = `(SystemClock.elapsedRealtimeNanos()/1000 - scanResult.timestamp)/1000`. It detects stale, cached entries.
- `throttle_setting` = what the tester set in Developer options (`on`, `off`, `unknown`).

### 3.5 Navigation session log — `session_<id>.jsonl`, one event per line

All events include `session_id`, `ts_ms`, `event`. Event types and extra fields:

| event | fields |
|---|---|
| `session_start` | participant_code, device_model, planner (`shortest`/`simplest`/`risk`), lambda, start_node, dest_node, app_version |
| `route_planned` | nodes[], edges[], total_length_m, expected_errors, scheduled_prompts[node_id] |
| `step` | step_count, pdr_edge_id, pdr_offset_m, heading_rel_deg |
| `turn_detected` | angle_deg, at_node_estimate |
| `recognition` | method (`text`/`image`/`fused`), top1_node, confidence, margin, top3[], ocr_raw_text_hash, accepted |
| `prompt_shown` | reason (`scheduled`/`uncertainty`/`off_route`), node_estimate, p_wrong |
| `prompt_result` | scanned (bool), recognition_top1, seconds_to_respond |
| `off_route` | reason (`unexpected_turn`/`missed_turn`/`overrun`), confidence |
| `replan` | from_node, new_route_nodes[] |
| `observer_tag` | tag (`wrong_turn`/`hesitation`/`asked_someone`/`gave_up`), node_id |
| `arrival` | node_id, correct (bool), total_time_s, distance_walked_m |
| `session_end` | completed (bool) |

**Never log** raw OCR text containing names, raw SSIDs, raw MACs or images.

## 4. Algorithms

### 4.1 Graph and turn-aware state space

- Load CSVs into `Graph(nodes, edges)`. Validate: all edge endpoints exist, the graph is connected, lengths are > 0, and each door node has degree 2 or less.
- Planners run Dijkstra over **transition states** `(node, incomingEdge)` so that turn costs and junction risk can be charged. Turn angle at a node = the smallest signed difference between the incoming edge bearing and the outgoing edge bearing.
- Turn classes: `straight` (|θ| < 30°), `slight` (30–60°), `turn` (60–135°), `u_turn` (> 135°).

### 4.2 Planners (all implement `Planner.plan(start, dest, params): Route`)

- **ShortestPlanner:** cost = length_m.
- **SimplestPlanner:** cost = 100 × (number of `turn` or `u_turn` transitions) + 20 × `slight` + length_m. This is the fewest-turns baseline, with length as a tie-break.
- **RiskAwarePlanner:** for each transition t,

```
risk_t      = RiskModel.predict(features_t)             // probability 0..1
avoid_cost  = λ · risk_t
ask_cost    = C_ask + λ · risk_t · (1 − e_scan)
transition_cost = length_m(outgoing edge) + min(avoid_cost, ask_cost)
```

When `ask_cost < avoid_cost`, the transition is marked `scheduled_prompt`. Defaults: `λ = 40` m per expected error, `C_ask = 6` m-equivalent, `e_scan = 0.7` (fraction of risk removed by a correct scan). All three are configurable in `DebugSettings` and swept offline. Setting `λ = 0` must reproduce ShortestPlanner exactly. This is a required unit test.

### 4.3 Risk features and model

Features per transition `(incoming edge → node → outgoing edge)`:

| Feature | Source |
|---|---|
| junction_degree | graph |
| turn_angle_abs_deg | bearings |
| turn_class_onehot | derived |
| similar_doors_nearby | rooms.csv |
| next_landmark_readability | fraction of correct recognitions for the next door on the route, from the recognition eval table; defaults from sign_condition until measured (clear 0.95, faded 0.75, glare 0.65, handwritten 0.55, obscured 0.35, missing 0.20) |
| landmark_gap_m | distance along the route since the last door with readability ≥ 0.7 |
| edge_length_m | graph |
| wifi_infra_visible | optional; 0 if unknown |

- **v0, HandWeighted:** `risk = sigmoid(w·x + b)` with hand-set weights in `risk_model.json`. Weights are documented, and set so a 4-way junction with a turn and a readability of 0.2 gives about 0.4, and a straight corridor with a readable next door gives under 0.05.
- **v1, learned:** logistic regression and LightGBM trained in Python on transitions from pilot and trial logs, labelled 1 if an `observer_tag=wrong_turn` or `off_route` occurred at that node. Export formats:
  - logistic: `{"type":"logistic","features":[...],"weights":[...],"bias":b,"scaler":{"mean":[...],"std":[...]}}`
  - trees: `{"type":"tree_ensemble","features":[...],"trees":[{"nodes":[{"f":i,"t":thr,"l":idx,"r":idx,"v":leaf}]}],"base":b}`
  - Kotlin parity test: Python and Kotlin predictions on 50 fixed rows must agree within 1e-6.

### 4.4 Room recognition

**Text path**
1. CameraX frame → ML Kit → text blocks.
2. Normalise: uppercase, strip punctuation; in digit contexts map O→0, I/L→1, S→5, B→8, Z→2.
3. `RoomNameMatcher` computes a weighted Levenshtein distance against every room's `sign_text`, `room_name` and `aliases`. Confusable pairs (O/0, I/1/L, S/5, B/8, Z/2, rn/m) cost 0.3; all other substitutions cost 1.
4. Score = 1 − distance / max(len). `p_text` = softmax(score / 0.1) over rooms. If the best score is below 0.5, text is "not read".

**Image path**
1. Frame → MediaPipe ImageEmbedder (L2-normalised, quantised off) → vector.
2. Cosine similarity with each enrolment vector. The room score is the maximum over that room's enrolment photos.
3. `p_img` = softmax(sim / 0.05).

**Fusion**
```
w_text = 0.8 if text read with margin ≥ 0.15, 0.5 if text read with margin < 0.15, 0.0 if not read
p      = normalise( (w_text·p_text + (1−w_text)·p_img) · prior )
prior  = rooms within (PDR distance since last fix + 10 m) along the graph get 1.0, others 0.2
         (uniform when there is no fix yet)
accept if top1 ≥ 0.6 and (top1 − top2) ≥ 0.2, otherwise ask the user to rescan or move closer
```
All thresholds live in `DebugSettings` and are tuned on a validation split, **never on the test photos**.

Enrolment: `EnrolScreen` captures 5 doorway photos per door (straight, ±20°, near, far), stores the embeddings in Room, and stores the images in `assets/enrolment` or app storage.

### 4.5 Pedestrian dead reckoning

- **Steps:** `TYPE_STEP_DETECTOR`. If absent, detect peaks in the accelerometer magnitude (low-pass filter, minimum interval 250 ms, threshold calibrated).
- **Step length:** calibrated per user by walking a measured 10 m in `DebugScreen`. Default 0.7 m.
- **Turns:** relative yaw from `TYPE_GAME_ROTATION_VECTOR`. A turn is a yaw change of more than 50° within 2.5 s, reported with its sign and angle. Absolute compass heading is not used.
- **Graph snapping:** position = (current edge, offset in metres). Each step adds step length to the offset. Near the edge end (within 3 m), a detected turn selects the outgoing edge whose turn angle best matches. With no turn, continue straight if possible. A recognition fix resets to (the door node, offset 0, drift 0).
- **Drift estimate:** `drift_m = 0.05 × distance_since_fix + 1.5 × turns_since_fix`, a documented heuristic replaced by the measured value from experiment E6.

### 4.6 Scan-prompting policy

At each step event:
```
p_wrong_belief = sigmoid(a·distance_since_fix + b·turns_since_fix − c·last_fix_confidence + d)
cost_of_error_ahead = λ · max(risk of transitions within the next 10 m)
prompt if (scheduled_prompt within 4 m ahead)
       or (p_wrong_belief · cost_of_error_ahead > C_ask)
rate limit: at most one prompt per 15 m walked, unless off-route
```
- Baselines selectable in `DebugSettings`: `always` (every door), `fixed_interval` (every N m), `never`.
- Ablation flag: `use_wifi_features` true or false.

### 4.7 Off-route detector (v0 rules)

- `unexpected_turn`: a turn is detected mid-edge (more than 3 m from both ends), or at a node where the route goes straight.
- `missed_turn`: the route expects a turn at node X, the offset exceeds the edge length plus 4 m, and no turn was detected.
- `overrun`: distance on the current edge exceeds 1.3 × edge length + 3 m.
- On a flag: if the belief confidence is below 0.5, prompt a scan; otherwise re-plan from the best-matching node.
- Log detection delay in steps for experiment E9.

## 5. App screens

1. **Home:** destination search (name, alias, number), plus a "start from here" option (scan the nearest door or pick a start).
2. **Navigate:** the next instruction in large text ("Walk 12 m, then turn left at the junction"), a simple schematic of the route on the graph, remaining distance, and a manual scan button.
3. **Scan sheet:** camera preview, live OCR text, top-3 rooms with confidence, accept or rescan.
4. **Enrol:** pick a door node, capture 5 photos, see a success check.
5. **Survey:** choose college code and node, Start/Stop scanning, scan counter, throttle-setting selector, export. It shows counts only, never names.
6. **OCR benchmark:** batch-run ML Kit over a folder of test photos and export a CSV (needed because ML Kit cannot run on a laptop).
7. **Debug:** planner selector, λ, C_ask and e_scan, prompt policy and baseline, Wi-Fi feature flag, step calibration, live PDR state, participant code, export logs.

## 6. Python experiments (offline)

| ID | Script / notebook | Output |
|---|---|---|
| — | `validate_graph.py` | Errors or OK; connectivity; plot of the graph from x_m, y_m |
| E1 | `churn_analysis/multi_college.ipynb` | Per college: BSSIDs, estimated physical APs, hotspot share |
| E2 | `churn_analysis/`, `hotspot_persistence/` | Survival curves within an hour vs across days |
| E3 | `ap_scarcity/` | Accuracy vs AP count (all APs, 8, 6, 4, 3, 2, 1) for kNN, MLP, 1D-CNN, AE+classifier on UJIIndoorLoc; 30 random subsets per count + strongest-k; mean ± 95% CI |
| E4 | `ocr_benchmark/` | ML Kit (CSV from phone) vs PP-OCR mobile vs TrOCR-small, accuracy by sign condition, size, latency |
| E5 | `recognition_eval/` | Text-only vs image-only vs fused, top-1 and confident-wrong rate by condition (uses the Python MediaPipe embedder with the same `.tflite`) |
| E6 | `planner_eval/drift.ipynb` | PDR error vs distance since fix |
| — | `planner_eval/route_diff.py` | Start–destination pairs where shortest, simplest and risk routes differ; λ sweep of extra length vs expected errors |
| E7–E9 | `risk_model/` | Train v1 risk model, prompt-policy curves, off-route delay |
| — | `dataset_package/` | Anonymisation verification (regex scan for MAC/SSID patterns must find zero), DATASHEET.md |

**Split rule:** keep photos from a separate capture session as the test set. Never tune thresholds on it.
