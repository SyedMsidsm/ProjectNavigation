# Ground-floor integration — 24 September 2026

The user-supplied architectural plan is now bundled in RAIN 0.4.0-ground-floor-draft. It is a **plan-derived draft**, not a measured or doorway-verified navigation map.

## Available now

- **Floor maps → College ground floor → Activate reviewed map** selects the offline draft. The synthetic demo remains available through Restore sample map.
- **View supplied plan** opens the original drawing with pinch zoom, pan and reset.
- The graph contains 74 nodes and 74 bidirectional segments, with 33 named room destinations plus the main lobby entrance. Classroom codes LH-A001 through LH-A007, labs, library, examination centres and offices are searchable/matchable by their transcribed names.
- The existing three planners, route display, OCR matcher, session export and optional AR preview consume the selected graph. They do not independently validate its geometry.
- Exact classroom-code tests distinguish all seven rooms. The duplicate label “Examination Centre” requires confirmation rather than choosing one of the two locations silently.
- Source classification `plan_derived_floor` is preserved in session logs. Draft warnings appear during planning and navigation.
- All physical sign conditions remain unknown. The schema conservatively represents these as missing/unverified signs with no registered sign text, so automatic scan prompts are not scheduled from invented sign-readability evidence. Manual OCR still compares the map's room names and aliases.

## What the drawing does not establish

The room dimension strings are transcribed into `data/ground-floor/survey-checklist.json`, not treated as corridor lengths. The raster is not uniformly scaled: current metre coordinates use nominal horizontal/vertical scales of 0.07/0.10 m per pixel. Distances are therefore estimates, unsuitable as calibrated walking or AR ground truth. The user has not explicitly confirmed the printed units or building north.

Door and approach positions are provisional. In particular, confirm the northern classrooms, both examination centres, toilet vestibule, PE/NCC access and lobby connection. Room nodes are terminal approaches, so the graph cannot use another room as a corridor shortcut. This does not prove the inferred doorway itself exists.

The user confirmed continuous corridor access and free movement in the large courtyard, but clarified that doors are not on both sides. **Courtyard shortcuts are intentionally not connected until its actual corridor openings are marked.** This is a geometry dependency, not a claim that the courtyard is inaccessible.

Strong room, VIP sitting room, fees counter, the smaller ELE. room, lift and stair transitions are inventoried but excluded from routing until their approaches and access are confirmed. Other listed rooms may also require access restrictions. This release does not guarantee step-free or unrestricted routes.

## Mark up the plan next

On a copy of the supplied image, draw:

1. Blue lines along the actual walkable corridors.
2. Red marks at every usable room doorway; one mark per real doorway, including multiple doors where present.
3. Green marks at openings into the large courtyard and at the building entrance(s).
4. Crosses over locked/restricted passages or places visitors cannot use.

Keep room labels readable. Also confirm whether dimensions are in metres. Exact drawing quality is unnecessary; the connections matter. We can then replace provisional approaches, add courtyard paths and measure a few representative corridor segments before walking calibration.

## Files and reproducibility

- `data/ground-floor/source-plan.png`: untouched supplied image.
- `data/ground-floor/rooms.csv`, `edges.csv`: draft graph.
- `data/ground-floor/survey-checklist.json`: room dimension transcription and outstanding access checks.
- `output/ground-floor-pack.json`: importable map pack.
- `docs/ground-floor-review.html`: original plan with toggleable route overlay; open locally with the repository folders in place.
- `output/ground-floor-review/index.html`: portable review page alongside its image. Only this two-file folder needs serving for a browser preview.
- `scripts/build_ground_floor.py`: rebuilds draft CSVs, pack, bundled JSON and review overlay. It does not replace the source image. The copied portable review page should be refreshed after regeneration.

## Validation and remaining project work

Final verification on 24 September: 50 Kotlin core tests, 25 Python tests and 11 Android integration tests passed; APK assembly and lint passed. Log: `.tools/ground-floor-final-build.log`. Installed the verified build on the S24 Ultra, activated the ground-floor draft, verified Main lobby entrance → Library as the initial selection, and opened/visually checked the original-plan viewer. Pinch accuracy, walking distances, doorway correctness and physical AR alignment were not validated by these checks. Deliverable APK: `output/RAIN-ground-floor.apk` and its SHA-256 file.

The graph/schema tests check connectivity, source provenance, unchanged bundled data and terminal room approaches. Phone integration tests cover all named destinations in both directions for all three planners, no intermediate room traversal, distinct classroom recognition and ambiguous exam-centre rejection. These are software checks, not surveyed-route acceptance.

After the marked plan: correct and measure the graph, verify sign labels, calibrate walking, test routes/AR physically, collect separate reference/test captures, implement and evaluate appearance fusion and a learned risk model, then run controlled baseline trials and finish the academic report. Wi-Fi evidence remains optional and unimplemented. The map image alone supplies none of the training labels or physical accuracy results required for those stages.
