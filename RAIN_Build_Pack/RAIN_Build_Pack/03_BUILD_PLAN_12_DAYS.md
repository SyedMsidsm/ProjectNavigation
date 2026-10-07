# RAIN — 12-Day Build Plan

**Goal:** by the end of Day 12, an Android app that recognises rooms, tracks the user, plans shortest, simplest and risk-aware routes, prompts scans, detects off-route walking and logs everything, plus a Wi-Fi survey logger and running offline experiments. That is **70–80% of the project**.

**Roles** (assign names):
- **A:** Android core (graph, planners, UI)
- **B:** Recognition (camera, OCR, embedder, fusion)
- **C:** Sensors and survey (PDR, Wi-Fi logger, anonymisation, session logging)
- **D:** Data and ML (floor walk data, Python experiments, risk model, literature)

**Daily rhythm:**
- 15-minute stand-up in the morning (done / doing / blocked).
- Build with GPT-6 Astra.
- Test on real phones.
- Merge to `main` by evening.
- 10-minute demo walk in the corridor before leaving.

---

## What "70–80% done" means on Day 12

| # | Item | Weight | Done when |
|---|---|---|---|
| 1 | Floor graph data (`rooms.csv`, `edges.csv`) validated | 8% | `validate_graph.py` passes and the plot matches the building |
| 2 | Destination search + turn-by-turn route UI | 8% | Any destination gives correct instructions |
| 3 | Three planners (shortest, simplest, risk-aware) with λ | 12% | Unit tests pass; λ=0 equals shortest; route-diff pairs found |
| 4 | Text recognition (ML Kit + matcher) | 8% | ≥ 18/20 clear or faded signs recognised on the phone |
| 5 | Doorway enrolment + fused recognition | 8% | Missing or obscured doors recognised in a test walk |
| 6 | PDR + graph snapping + fix reset | 12% | A 30 m, 2-turn walk ends on the correct edge |
| 7 | Scan-prompting policy + baselines | 7% | Prompts appear before risky junctions; baselines switchable |
| 8 | Off-route detector + re-plan | 6% | A deliberate wrong turn is flagged within about 10 steps |
| 9 | Session JSONL logging + export | 5% | Pilot walks produce valid logs that parse in Python |
| 10 | Wi-Fi survey mode with anonymisation | 6% | Zero raw MACs or SSIDs in exports (scripted check) |
| 11 | Offline: OCR benchmark (E4) running | 4% | Table by sign condition for at least 2 models |
| 12 | Offline: AP-scarcity curve (E3) | 4% | Plot down to 1 AP with confidence intervals |
| 13 | Churn + multi-college data collection started | 4% | At least 6 home sessions; logger APK sent to at least 5 colleges |
| 14 | Risk model v1 trained from pilot logs (or v0 kept, with documented reason) | 4% | JSON model loads; parity test passes |
| 15 | Demo script + recorded demo video | 4% | 3-minute walk-through recorded |
| **Total** | | **100% of the 12-day build scope**, about 75–80% of the whole project | |

**Not in the 12 days** (the remaining 20–30%): the participant navigation trial (needs approval and participants), final learned policy from trial data, hotspot persistence analysis over many days, conformal prediction, fine-tuned OCR, neural corrector, full dataset release, both papers.

---

## Day 0 — Before build day 1 (half a day, no coding)

- [ ] **All:** get Dr. Haneef's approval for the build plan, the rename to RAIN, **and the future navigation trial** (ask now; approval takes time).
- [ ] **All:** write down each phone's model and Android version. Check it has a step detector (use a free sensor-listing app). Pick 2 primary test phones.
- [ ] **A:** create the GitHub private repo, add all four members, protect `main`, add `.gitignore`.
- [ ] **All:** install Android Studio (latest stable), Git and Python 3.11. Enable Developer options and USB debugging on test phones.
- [ ] **D:** download the UJIIndoorLoc dataset (UCI repository) to the laptop.
- [ ] **C:** generate a random salt (32+ characters), store it in `secrets.properties` on each build machine, and never commit it.
- [ ] **All:** fill in the Team setup block in `00_MASTER_PROMPT.md`.

---

## Day 1 — Fieldwork and skeleton

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 1.1 Floor walk | D + B | Sketch the floor. Mark every door, junction, entrance and stairs. Measure corridor segment lengths (laser meter or measuring tape; or count steps after calibrating a 10 m walk). Note turn directions. | Paper sketch with node IDs |
| 1.2 Nameplate + doorway photos | B | For every door: 1 straight-on nameplate photo (no flash, corridor light), plus 5 doorway enrolment photos (straight, ±20°, near, far). Tag sign_condition. Avoid people in frame. | ~20 nameplate + ~100 enrolment photos, `doors_photos.csv` filled |
| 1.3 Repo + Android skeleton | A | Prompt Astra (master prompt, Task 1.3): Compose app, package `in.mite.rain`, `core/` and `android/` modules, version catalog, empty screens, navigation. | App installs and opens on both phones |
| 1.4 CSV entry | D | Fill `rooms.csv` and `edges.csv` from the sketch using `templates/`. | CSVs complete |
| 1.5 Validator | D | Astra writes `validate_graph.py` (spec §6). | Validation passes; plot matches the sketch |
| 1.6 Test photo session | B | Late afternoon: a second nameplate + doorway photo set in different light. **This is the test set.** | Separate folder, never used for tuning |

## Day 2 — Graph, routes and survey logger

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 2.1 Graph core | A | `Graph`, `CsvGraphLoader`, `TransitionGraph`, turn classes, plus unit tests. | Tests pass on sample graph |
| 2.2 ShortestPlanner + instructions | A | Dijkstra over transition states; `RouteInstructions` ("walk 12 m, turn left"). | Correct routes for 5 hand-checked pairs |
| 2.3 Destination UI | A | Home search + Navigate screen with an instruction list and schematic. | Pick a room → see the route |
| 2.4 Survey logger | C | `WifiScanSource`, `MacUtils` (LA bit), `Anonymiser` (salted hash), JSONL writer, Survey screen, export. | Export has hashes only; `results_updated` and `result_age_ms` recorded |
| 2.5 Anonymisation check | D | Script: regex for MAC patterns and a list of known SSIDs across all exports must return zero. | Script passes on the Day 2 export |
| 2.6 Start churn collection | C | Scan at 6 fixed nodes, 2 sessions per day (morning, afternoon), from today to Day 12. | Session 1 exported |

## Day 3 — Text recognition and motion

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 3.1 Camera + ML Kit | B | CameraX `ImageAnalysis` → bundled ML Kit → live text overlay in the Scan sheet. | Live text on screen at a door |
| 3.2 RoomNameMatcher | B | Normalisation + weighted Levenshtein + aliases + unit tests (`R00M 2O4` → Room 204). | Tests pass; top-3 shown in the UI |
| 3.3 Step + turn sources | C | `StepSensorSource` (with accelerometer fallback), `RotationSource`, `TurnDetector`; ACTIVITY_RECOGNITION permission. | Debug screen shows steps and turn events live |
| 3.4 Step calibration | C | 10 m calibration flow in Debug. | Step length saved per device |
| 3.5 OCR benchmark screen | B | `OcrBenchmarkScreen` batch-runs ML Kit on the nameplate photo folder, exports CSV. | CSV of predictions for the test set |
| 3.6 AP-scarcity setup | D | Load UJIIndoorLoc; kNN baseline with all APs; subsampling function. | Baseline accuracy printed |

## Day 4 — Fusion and graph snapping

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 4.1 MediaPipe embedder | B | Add `embedder.tflite` to assets; `MediaPipeEmbedder`; Enrol screen stores embeddings in Room. | 20 doors enrolled on a phone |
| 4.2 FusionRecognizer | B | Spec §4.4 fusion + prior + accept rule; unit tests with fake vectors. | Tests pass; a covered sign is still recognised |
| 4.3 PdrTracker + GraphSnapper | C | Edge + offset state, turn-based edge selection, reset on fix; unit tests with synthetic step and turn streams. | Synthetic 3-edge walk ends on the correct edge |
| 4.4 OCR benchmark Python side | D | PP-OCR mobile + TrOCR-small on the same test photos; join with the ML Kit CSV; accuracy by condition. | First E4 table |
| 4.5 Risk features | A | `RiskFeatures` extraction per transition + readability defaults. | Feature rows printed for one route |

## Day 5 — First end-to-end walk

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 5.1 PositionBelief + NavigationSession | A + C | Wire recognition fixes, PDR updates and instruction progression. | Instructions advance as you walk |
| 5.2 SessionLogger | C | JSONL events per spec §3.5; export button. | Log parses in Python |
| 5.3 End-to-end demo walk | All | Scan start door → pick destination → walk → arrive. Record problems. | At least 1 complete successful walk |
| 5.4 Multi-college logger | C + D | Build a survey-only APK flavour (or a feature flag) plus a one-page instruction sheet; send to friends at 5–10 colleges. | Sent to at least 5 contacts |
| 5.5 Bug list | All | Prioritise P0 (blocks demo) / P1 / P2 in GitHub Issues. | Issues created |

## Day 6 — Risk-aware and simplest planners

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 6.1 RiskModel v0 | A + D | `JsonLogisticModel` + `risk_model.json` hand weights (spec §4.3). | Risk values look sensible on the plot |
| 6.2 RiskAwarePlanner | A | Avoid-or-ask cost (spec §4.2), scheduled prompts; unit test λ=0 equals shortest. | Tests pass |
| 6.3 SimplestPlanner | A | Turn-penalty costs. | Tests pass |
| 6.4 Planner selector | A | Debug settings: planner, λ, C_ask, e_scan. | Switch planners live |
| 6.5 Route-diff script | D | Find start–destination pairs where the three planners differ; λ sweep plot. | List of candidate trial routes |
| 6.6 Fix P0 bugs | B + C | From Day 5. | P0 list empty |

## Day 7 — Prompting and off-route

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 7.1 PromptPolicy | A | Spec §4.6 + baselines (always, fixed interval, never) + rate limit. | Prompt shows before a risky junction |
| 7.2 OffRouteDetector | C | Spec §4.7 rules + re-plan + logging of detection delay. | Wrong turn flagged in a test walk |
| 7.3 Scan UX | B | Accept/rescan flow, "move closer" hint, confidence display. | Smooth scan in under 5 s at a clear sign |
| 7.4 Recognition eval | D | E5 text vs image vs fused on the test set (Python embedder, same model). | First E5 table |

## Day 8 — Pilot walks

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 8.1 Pilot protocol | D | Route list from 6.5; observer tag sheet; each member walks each planner on different routes. | Protocol doc |
| 8.2 Pilot walks | All | At least 12 logged walks across planners. Observer tags wrong turns and hesitations in the app. | 12+ valid logs |
| 8.3 Drift analysis | C + D | E6: PDR error vs distance since fix, from ground-truth door checkpoints. | Drift plot; heuristic updated |
| 8.4 AP-scarcity curve | D | E3 complete down to 1 AP, 30 repeats, 4 models. | Plot with confidence intervals |

## Day 9 — Learned risk model v1

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 9.1 Label transitions | D | From pilot logs: label 1 at nodes with wrong_turn or off_route. | Training table |
| 9.2 Train + export | D | Logistic + LightGBM, grouped cross-validation by walker; export JSON. | Metrics reported honestly (even if weak) |
| 9.3 Kotlin tree model + parity | A | `JsonTreeEnsembleModel`; parity test on 50 rows. | Parity test passes |
| 9.4 Decide v0 vs v1 | A + D | Use v1 only if better in cross-validation; otherwise keep v0 and write down why. | Decision in `DECISIONS.md` |
| 9.5 Thresholds on validation data | B | Tune fusion thresholds on enrolment/validation photos, not the test set. | Settings saved |

## Day 10 — Data and analysis

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 10.1 Churn analysis | C + D | Home sessions: BSSID counts, estimated physical APs, hotspot share, survival. | E2 draft plots |
| 10.2 Multi-college intake | D | Validate and anonymisation-check received exports. | Table for colleges received so far |
| 10.3 Dataset packaging scripts | D | Folder structure, anonymisation verification, `DATASHEET.md` draft. | Script runs end-to-end |
| 10.4 Prompt-policy offline curves | A | From pilot logs: prompts per walk vs wrong-room rate for each policy. | E7 draft plot |
| 10.5 UI polish | B | Readable instructions, large buttons, dark-corridor contrast. | Usable one-handed |

## Day 11 — Hardening

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 11.1 Bug bash | All | Each member tries to break the app on both phones (low light, rotation, permissions denied, airplane mode). | No P0 left |
| 11.2 Permission edge cases | C | Denied camera, activity or location permission → clear message, no crash. | Handled |
| 11.3 Demo script | A | 3-minute walk: faded sign, missing sign, risky junction prompt, wrong-turn recovery. | Script written |
| 11.4 Figures folder | D | All plots regenerated from scripts, not by hand. | `figures/` complete |

## Day 12 — Buffer, demo, freeze

| Task | Owner | Steps | Acceptance |
|---|---|---|---|
| 12.1 Buffer | All | Finish slipped tasks. | — |
| 12.2 Record demo | All | Screen recording + corridor video. | Video saved |
| 12.3 Guide demo | All | Show Dr. Haneef; collect feedback; confirm trial approval status. | Feedback noted |
| 12.4 Freeze v1.0 | A | Tag `v1.0-build`; write the "Remaining 20–30%" plan. | Tag pushed |

---

## Tools and resources

| Need | Tool / resource |
|---|---|
| AI build partner | GPT-6 Astra in ChatGPT (Codex mode for coding, where available) |
| IDE | Android Studio (latest stable), VS Code or Jupyter for Python |
| Official docs to check versions and APIs | developer.android.com (CameraX, sensors, Wi-Fi scanning, permissions), developers.google.com/ml-kit (Text Recognition v2), ai.google.dev/edge/mediapipe (Image Embedder, models), developer.android.com/jetpack/androidx/releases |
| Measuring | Laser distance meter or 30 m measuring tape; a notebook floor sketch |
| Sensor check | Any free "sensor list" app from the Play Store |
| Public data | UJIIndoorLoc (UCI Machine Learning Repository) |
| OCR models (offline) | PaddleOCR (PP-OCR mobile), Hugging Face `microsoft/trocr-small-printed` / `-handwritten` |
| Project management | GitHub Issues + a Projects board; one branch per member |
| Plots | matplotlib; save as PNG + PDF |
| Writing (later) | Overleaf with the IEEE conference template; Zotero for references (each one opened and read) |
