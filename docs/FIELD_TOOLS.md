# Field tools — version 0.3.0

23 September 2026. This release prepares RAIN for measured field work. The supplied floor remains synthetic; no campus accuracy claim is supported yet.

## What changed

- Guided walking calibration counts physical sensor steps over a user-measured 5–100 m distance. It rejects interrupted trials, fewer than eight steps and implausible step lengths. Review and explicitly save the result; verify on a second measured walk. Turn readings are diagnostic, not a calibrated turn model.
- Walking length, route risk weight, prompt policy, and optional voice/vibration preferences survive app restarts. Voice uses an installed English voice that requires no network. Missing voices are reported; text guidance remains available.
- Four prompt policies are selectable: cost-sensitive, eligible-landmark checks, fixed interval and never. Readable-landmark eligibility and the 15 m cooldown still apply. These settings do not implement every research baseline from the original proposal.
- Floor maps can be imported through Android's file picker. RAIN checks size, schema, coordinates, edges and directed connectivity, shows a preview, then saves the approved map atomically. Invalid imports cannot replace the active map. Restore sample map is available.
- Observer tools record checkpoints and observed outcomes during a trial. They are annotations, not independent proof of the phone's position. Session starts now record the map digest and prompt policy.
- The session-summary utility distinguishes physical and simulated steps, detector events and observer annotations, and verified versus unverified arrivals. Its elapsed wall time includes pauses.

## Prepare a floor without manual CSV editing

Open `docs/floor-editor.html` in a browser; it works locally without a server or account. Start from a measured sketch, add each room and junction, and connect adjacent locations. Coordinates and corridor lengths are metres. Bearings are clockwise from the building north you chose.

Choose the correct source: **Synthetic demonstration** for invented examples, **User-provided measurements** for surveyed data. The latter label is provenance, not certification. Save an editable draft regularly; there is no automatic browser persistence. Export the floor JSON, transfer it to the phone, then choose **Floor maps → Choose floor file**, review and activate it. Stairs are recorded, but this release does not guarantee step-free routing.

For existing CSV files:

```powershell
python python/rain_offline/build_floor_pack.py --rooms rooms.csv --edges edges.csv --name "College first floor" --source user_provided_floor --out output/college-floor.json
```

`output/demo-floor-pack.json` is an importable synthetic example. Do not relabel it as measured campus data.

## Collect and review a trial

1. Measure a straight, unobstructed distance. Open **Calibrate walking**, enter the actual distance, count steps while walking between the marks, stop, review and save. Do not use the prefilled distance unless you measured it.
2. Verify the saved step length on a separate walk in the same phone pose.
3. Choose a surveyed start and destination. An observer should independently record actual turns, wrong turns and arrival. Use observer tags as supplemental notes.
4. Export the session JSONL using the existing session export control. Keep route labels and independent ground truth with the trial protocol.
5. Validate and summarize exports:

```powershell
python python/rain_offline/check_exports.py output/phone-session.jsonl
python python/rain_offline/summarize_sessions.py output/phone-session.jsonl --out output/session-summary.csv
```

The supplied summary uses older synthetic demonstration sessions. It is an example, not an experimental result.

## Verification and limits

The field-tools build passed 50 Kotlin core tests, 23 Python tests and eight Android integration tests on the S24 Ultra. Integration coverage includes isolated preference persistence, valid floor activation/reload, invalid and oversized floor rejection, existing OCR/sensors/logging and AR configuration/rendering checks. The desktop editor rejected an empty map and exported a connected two-room synthetic map; its layout was visually checked.

The installed app's calibration screen used the hardware step detector. A stationary zero-step trial was rejected with no save action and the previous setting unchanged. The map-management screen opened correctly. No measured calibration was saved. Audible speech, vibration perception, a complete physical imported-map route and AR visual stability still require hands-on acceptance.

## Work remaining to complete the project

1. **Campus data and physical validation:** measured floor graph, actual room labels/sign conditions, reference captures, separate test captures, repeated walking and turn checks, reverse-direction routes, independent arrival ground truth, and AR stability/tracking-loss checks.
2. **Recognition improvements:** appearance descriptors and OCR/appearance fusion, rejection of ambiguous matches, and validation under varied lighting. Current recognition is conservative OCR matching; it does not include appearance fusion.
3. **Learned risk model:** collect labeled outcomes, split train/test routes without leakage, train/calibrate the proposed model, compare it with the current hand-weighted heuristic, and package validated parameters.
4. **Optional Wi-Fi evidence:** implement and validate a consented fingerprint survey/local matching module only if it improves the project after the core walking tests. No Wi-Fi positioning is implemented in this release.
5. **Research evaluation:** finalize baseline definitions and implement any missing policies, define the trial protocol and metrics, run controlled comparisons, report failures/uncertainty, and avoid treating simulated runs as physical accuracy evidence.
6. **Release and academic handoff:** complete accessibility/usability checks, performance/battery measurements, a reproducible release and backup, final report, presentation and demo rehearsal.

AR remains a manually aligned local route preview. Continuous, globally aligned AR navigation is a separate unsolved feature; the current preview pauses normal motion navigation. Multi-floor guidance and step-free route guarantees are also not implemented.
