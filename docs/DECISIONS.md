# RAIN architecture audit and decisions

## Authority and scope

The current user request authorises optimisation for a two-day demo and the fastest useful follow-up. The user subsequently approved synthetic floor data and identified a Samsung S24 Ultra. These directions supersede the older prompt's rigid 12-day sequencing. Native Android, offline operation, privacy at capture and honest evaluation remain unchanged.

The six root Markdown files are byte-identical to the copies in `RAIN_Build_Pack/RAIN_Build_Pack`. The two PDF binaries have different hashes but identical extracted text across all 12 pages; the architecture diagram was also inspected visually. The original pack is preserved as background. Its sample graph is a six-node chain with three doors: there are no alternate routes, and its “four-way” J02 has degree two. It cannot demonstrate route choice, so the app contains a clearly labelled synthetic alternative-corridor map.

## Corrections carried into the prototype

| Issue in original plan | Decision and consequence |
|---|---|
| Dijkstra state `(node, incomingEdge)` omits history, but `landmark_gap_m` depends on the chosen path | v0 uses static local features: junction degree excess, turn angle, next unambiguous landmark readability, similar doors and outgoing edge length. It does not claim to optimise path-dependent drift. Live drift belongs to navigation state. A future exact history-dependent planner needs expanded state or a documented approximation. |
| Asking always removes 70% risk, even where no sign exists | A planned scan only receives benefit at a registered readable sign. Otherwise effectiveness is zero. Default scan effectiveness remains an unmeasured assumption; camera success must eventually calibrate it. |
| `100 × turns + 20 × slight turns + metres` is called “fewest turns” | Retain the specified cost for compatibility and label the baseline **weighted simplest**. It is not lexicographic fewest-turn routing; a sufficiently long detour can reverse its preference. Use a true lexicographic baseline before claiming to beat fewest turns in a paper. |
| Similar room labels can score highly despite contradictory digits | Matching includes a numeric-conflict penalty, score/margin rejection and explicit location confirmation. Typed text is visibly a matcher demonstration. Camera recognition and typed input must remain distinguishable in logs. |
| A hand-weighted sigmoid is called an error probability | v0 produces an uncalibrated heuristic score. The legacy `expected_errors` field is a score sum for synthetic sessions, not an observed error count or validated expected value. Route cards must not present it as measured accuracy. |
| Route-distance completion can be mistaken for correct arrival | Display distance completion separately from location verification. `arrival.correct` may be null when `ground_truth_verified=false`; explicit observer verification is required for a true/false correctness outcome. This is an intentional amendment to the original boolean-only schema. |
| Original text implies all functionality is needed before a first demo | Deliver OCR + route comparison + guidance + recovery + logging first. Door appearance, learned models, Wi-Fi survey and large offline experiments are follow-up modules. Missing signs cannot be visually recognised by the text-only prototype. |
| The architecture mixes Android-dependent perception with algorithms | Keep core logic in the JVM `android/core` module. Android adapters and Compose are in `android/app`. This makes algorithms testable without a phone while reserving physical tests for sensors/camera. |

Initial settings are λ = 40 metre-equivalents per unit of heuristic risk, ask cost = 6 metre-equivalents and assumed scan effectiveness = 0.7. λ = 0 must yield the same path and tie-breaking as shortest. Reverse traversal uses a bearing rotated by 180 degrees. Bearings follow a building-local clockwise convention; they are not magnetic compass measurements.

## Data, logs and research corrections

- Safe session metadata may include `schema_version`, `demo_mode`, `data_source` and `model_version`. Prefer schema version `0.2` for the nullable correctness amendment. Simulated actions must remain identifiable even when a session switches to phone sensors.
- Never train a human-error predictor directly on the detector's own output as if it were independent ground truth. Record human observations separately; mark weak labels and report their limitations. Split by walker and capture session, with room/route leakage considered explicitly.
- Camera softmax match scores are relative scores, not calibrated probabilities. A high score alone does not establish accuracy. Use separate enrolment, validation and test capture sessions.
- The PDF asks for zero-AP scarcity results; the technical experiment table stops at one. Add a zero-input majority/prior baseline. Zero-AP accuracy is not necessarily uniform chance when classes are imbalanced. Do not place campus camera accuracy and public-dataset Wi-Fi accuracy on one axis as if the datasets or tasks were identical.
- An LA bit is not a hotspot label. Count observed BSSIDs separately from verified physical APs. Prefix similarity alone cannot establish physical AP count. The “86% hotspots” and “1–2 APs” numbers remain unverified background, not demo results.
- A salt embedded in a distributed APK is recoverable. Do not describe it as a secret. Before survey deployment, choose a per-study pseudonymisation scheme and record hash scope; avoid cross-device merging unless scopes are intentionally compatible. The demo collects no Wi-Fi data.
- A regex/known-SSID check is a useful gate, not proof of anonymity. Arbitrary names in free text or photos need separate review. No references or unsupported novelty claims have been added to academic material.

## Engineering sources checked

These are vendor engineering documentation, not academic references. The build catalog pins a compatible baseline rather than automatically upgrading to every newest release.

- Bundled ML Kit embeds the recognition model in the APK; CameraX rotation must be passed into the input image. [ML Kit Android text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- Wi-Fi scan completion can return cached results. Permissions, location state and throttling must be handled when the survey adapter is added; “Nearby Wi-Fi” is not a blanket replacement for the location requirements of scan APIs. [Android Wi-Fi scanning](https://developer.android.com/develop/connectivity/wifi/wifi-scan)
- Physical sensor availability and permissions must be checked at runtime. [Android motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)
- The appearance follow-up requires a compatible pretrained model asset and correct embedder options. [MediaPipe Image Embedder for Android](https://developers.google.com/edge/mediapipe/solutions/vision/image_embedder/android)
