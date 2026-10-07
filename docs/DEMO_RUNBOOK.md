# Three-minute presentation runbook

Target: Samsung S24 Ultra. The supplied floor and signs are synthetic. This runbook is an acceptance checklist; it does not assert that physical-phone testing has already passed.

## Before the presentation

1. Run `./scripts/build.ps1` from the repository root. Connect the phone with USB debugging, authorise the computer, then run `./scripts/build.ps1 -Install`. The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.
2. Open RAIN. Grant Camera when using **Scan a sign**. Grant physical activity permission when enabling **Use phone motion**. Keep the app in the foreground during motion checks.
3. Open `docs/demo-signs.html` on a laptop or print it. Display a single sign at a time, fill the camera view with that sign, and avoid glare. The display is a visual test target, not a surveyed campus landmark.
4. Keep **Main entrance → CSE Lab 204** and λ = 40. Confirm the route comparison works. In Settings, briefly set λ = 0 and verify risk routing matches shortest; restore 40.
5. Rehearse the sequence below twice. Keep a screen-recorded backup and a copy of the installed APK. Test airplane mode after installation to verify bundled OCR works without first-run model downloads.

## Live sequence

| Time | Action | Say |
|---|---|---|
| 0:00–0:30 | Show floor and route cards for Main entrance → CSE Lab 204 | “RAIN is an offline phone-only navigation prototype. This synthetic floor lets us demonstrate the routing logic before the campus survey.” |
| 0:30–1:00 | Switch between Shortest path, Simplest path and Risk-aware path; show Settings λ | “The planner trades walking distance against a hand-weighted risk score. These are heuristic choices; reliability gains still need participant measurements.” |
| 1:00–1:35 | Scan **ROOM 201** using the actual camera, inspect the suggested room and confirm start | “Bundled OCR reads a known sign locally. The app asks us to confirm the location.” |
| 1:35–2:10 | Start navigation; leave phone motion off; use **Advance 5 simulated steps** and turn controls as needed | “These labelled rehearsal controls exercise the guidance logic. Physical motion uses step and relative-turn sensors and needs calibration.” |
| 2:10–2:40 | At an inappropriate point, trigger a simulated turn; show warning; scan **LIBRARY** or use clearly labelled sample text and confirm **Anchor & replan** | “If position is uncertain, a confirmed sign gives us an anchor and the app calculates a fresh route.” |
| 2:40–3:00 | Show export action and limitations footer | “The app logs its inputs for evaluation. The next milestone adds the surveyed floor, doorway appearance and measured trials.” |

If the deliberately wrong turn is near a valid junction, it may be consistent with the route. Rehearse the point that produces the expected warning before presenting. Do not claim automatic localisation from the synthetic map while physically walking another building.

## Phone acceptance checklist

- [ ] Fresh launch and map rendering on S24 Ultra; note Android version and APK version.
- [ ] Camera permission denied: clear recovery path and no crash; grant later and scan works.
- [ ] Live OCR recognises at least three distinct synthetic labels; unknown/ambiguous input is rejected or requires explicit manual confirmation.
- [ ] Closing/reopening scanner repeatedly does not freeze preview; portrait orientation remains correct.
- [ ] λ = 0 route equals shortest; forward and reverse instructions turn in the expected directions.
- [ ] A rehearsal walk can advance, show a prompt/warning, accept a sign anchor and replan.
- [ ] Physical step count responds when motion is enabled; deny activity permission without crashing. Calibrate step length before assessing metres.
- [ ] Export includes synthetic/demo provenance; typed input and real camera input remain identifiable.
- [ ] Two complete walkthroughs and offline OCR trial pass; save observations and failures to `docs/PROGRESS.md`.

Only tick what was actually observed. A desktop test pass does not satisfy a camera/sensor item.

## Export validation

After using **Export session · JSONL**, save the file locally through the Android share sheet. From the repository root:

```powershell
python python/rain_offline/check_exports.py data/exports/session_example.jsonl
python -m unittest discover -s python/tests -v
```

The checker rejects malformed schemas and common identifier leaks without printing leaked values. It accepts `arrival.correct=null` only when `ground_truth_verified=false`; unverified completion must not enter arrival-accuracy calculations. No raw OCR text, SSIDs, MACs or images should be in navigation events.

## Recovery options

| Problem | Immediate response |
|---|---|
| OCR struggles with monitor glare | Show one large printed sign, move closer, hold still. Use **Try sample text** only while explicitly saying it tests the matcher, not the camera. |
| Motion stalls or turns drift | Turn phone motion off and use the clearly labelled demo controls. Record the device issue for calibration; do not describe simulated movement as live tracking. |
| Routes happen to coincide after a new start | Restore Main entrance → CSE Lab 204 and λ = 40. Equal routes can be a valid planner result. |
| No share target installed | Keep the private session in the app and retry with an available file-sharing app. Do not paste private logs into presentation slides. |
| App failure during presentation | Use the rehearsed recording from the same build; state that it is a recording and log the failure afterward. |

## Five honest viva answers

The optional AR addition has its own [AR preview guide](AR_DEMO.md). Rehearse its physical acceptance checks before including it in the presentation. It shows a manually aligned synthetic route and pauses normal navigation; continuous college AR guidance is a later stage.

- **Is the risk model trained?** No. This build uses transparent hand-set weights; grouped pilot data will determine whether a learned model adds value.
- **Can it handle missing signs now?** Text-only recognition cannot. The planned appearance embedder and enrolment module are the next perception step.
- **Is it better than shortest path?** The sample demonstrates a different decision. Improvement in arrival success or time requires controlled field trials.
- **Why does it work without Wi-Fi?** Sign anchors and motion drive this prototype. Wi-Fi is an optional later feature and separate measurement tool.
- **Is arrival verified?** Distance completion is an estimate. An independently checked room outcome is needed before counting an accurate arrival.
