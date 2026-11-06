# RAIN demo status — 6 November 2026

## Confirmed before today's UI update
- User reported 22 actual steps and 22 displayed steps in one uninterrupted trial.
- User confirmed two physical right turns; diagnostic trace recorded approximately 98 and 91 degrees.
- Saved `turf` map reloads: bench → kichqd → entrance, with one right-angle corner (14.7 m estimated route).
- Existing larger local Gemma model passed an actual synthetic-catalog inference test.
- These are limited checks, not a measured navigation-accuracy claim.

## Navigation UI update
- Compact instruction card, larger map, actual saved-place names.
- Blue direction arrow with fading heading cone, driven by gravity-projected gyro yaw at the existing sensor cadence.
- Position remains based on navigation progress; phone rotation does not add walking steps.
- User explicitly aligns while facing the route. Alignment clears on backgrounding, scanning, input-mode changes, interaction pauses and replanning.
- Relative heading can drift and requires realignment; it is not an absolute compass or a location measurement.

## Smaller NLP model
Candidate: litert-community/Gemma3-1B-IT, gemma3-1b-it-int4.litertlm (584,417,280 bytes).
Official source: https://huggingface.co/litert-community/Gemma3-1B-IT
Installed from the user's downloaded artifact. Published size matched; repository masks its public SHA256 for gated files, so no independent source-hash claim is made. Local and phone SHA256 matched: 1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be.
Previous 2.59 GB model retained on phone as destination-model.previous for rollback.
Three real device inference tests passed (3.609 seconds for the test class, not a per-request benchmark): borrowing a book → Library; washing hands → Washroom; swimming pool absent from catalog → no suggestion. These synthetic-catalog cases are not an NLP accuracy benchmark. Model stays separate from the APK in private app storage.
Evidence: .tools/small-gemma-inference.log; .tools/models/gemma3-1b-verified.json.

## Must finish for demo
1. PASSED: screen visually inspected on S24 Ultra; user confirmed gentle rotation moves arrow/cone and count stays at 0. Stationary session only, not a field navigation trial.
2. Complete physical start → destination and reverse walks with named checkpoints and arrival confirmation.
3. Rehearse microphone permission, speech-to-text and destination confirmation on the actual demo phone.
4. PASSED: smaller Gemma installed, transfer checksum verified, three actual inference tests passed.
5. Rehearse the single-floor sequence: record → mark places → save → select start/destination → navigate → confirm arrival.

AR, multi-floor claims, research accuracy figures and large-scale benchmarking are outside today's essential demo acceptance.

## Build and verification
App version 0.9.3-navigation-heading, versionCode 14 installed preserving maps.
Core tests and Android lint passed; 18 device integration tests passed on the updated app with the previous model. Following replacement, three targeted local-assistant device tests passed with Gemma 3 1B.
APK: output/RAIN-demo.apk; SHA256 sidecar: output/RAIN-demo.apk.sha256.
Visual evidence: tmp/rain-navigation.png and tmp/rain-navigation-aligned.png.
User confirmed: "Yes, it rotates and stays at 0" for stationary direction-display check.
