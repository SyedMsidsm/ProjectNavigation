# RAIN execution plan

Updated 21 September 2026. Target phone: Samsung S24 Ultra. The user approved a synthetic floor for the presentation; the campus floor walk has not happened. Original Claude source files remain unchanged.

## Approach

Build one offline native Android journey: choose a start and destination, compare three routes, scan a sign, follow instructions, recover with another scan, and export a session. Keep the navigation contribution visible from the first build. CameraX, bundled ML Kit, Compose, Room/KSP and pure Kotlin logic remain the architecture. No server, API key, cloud OCR or Wi-Fi setup is required for this slice.

The supplied map is a deliberately constructed example with 15 nodes, 15 corridor segments and 10 rooms. It demonstrates choices; it is not a surveyed map or evidence that risk-aware routes improve real outcomes. Pinned dependencies form a compatible baseline, not a claim to be the latest release.

## The next 48 hours

| Window | Owner | Deliverable | Gate |
|---|---|---|---|
| Hours 0–4 | A: app integration | Build APK; install on S24 Ultra; verify permissions and launch | Fresh install opens offline, map and three route cards render |
| Hours 0–6, parallel | D: data / B: camera | Validate sample graph; print or display demonstration signs | CSV validation passes; real camera reads at least three distinct sample labels |
| Hours 4–12 | A + B | Complete route selection, scan confirmation and replan journey | Main entrance → CSE Lab 204 works; λ = 0 matches shortest route |
| Hours 8–18 | C: motion | Check foreground steps and relative turns on phone; retain labelled demo controls | Permission denial does not crash; step counter works or clear fallback status appears |
| Hours 12–24 | A + C | Exercise scan prompts, wrong-turn warning and session export | Rehearsed warning → scan → replan flow; exported JSONL passes checker |
| Hours 24–36 | All | Rehearse three-minute runbook; fix only demo blockers | Complete walkthrough twice without app restart; record device/OS and observations |
| Hours 36–48 | All | Freeze presentation build; record screen/corridor backup | APK, backup recording, runbook and known limitations saved together |

Treat the field map, image embedding, survey logger and experiment suite as separate follow-up work. Do not install the original large Python ML stack before a dataset requires it. Demo validation uses the standard library only.

## What “50%” means

“50% demo” is a planning milestone for the high-impact engineering slice. It is achieved only when the gates above pass on the phone. Code generation, an APK build and simulated motion do not meet camera, motion or campus-validation gates by themselves.

The old 12-day table weights its own build scope, which it calls roughly 75–80% of the whole project. Those weights cannot honestly be reused to claim 50% of the entire research project is complete. Record each capability as implemented, desktop-verified, phone-verified or field-evaluated in `docs/PROGRESS.md`; give evidence rather than an invented completion percentage.

## Immediate follow-up toward 80%

Run these in order, parallelising only independent data collection and adapters. The target is the functioning system plus reproducible initial evaluations; elapsed time is a forecast, not an acceptance criterion.

| Priority / likely window after demo | Work | Acceptance evidence |
|---|---|---|
| 1 / days 1–2 | Walk a single campus floor; assign stable nodes; measure lengths; capture enrolment and separately timed test photos | Validated CSV and plotted schematic match a manually checked sketch; five routes checked in both directions |
| 2 / days 1–3 | Calibrate step length and relative turns; verify snapping and scan reset on the measured map | Logged 30 m walk with two turns; correct edge at independent checkpoints; drift reported in metres |
| 3 / days 2–4 | Add pretrained MediaPipe embedder, enrolment UI and Room vector storage; combine image/text/prior | Same model in Python and Android; text/image/fused results from held-out sessions, including confident-wrong rate |
| 4 / days 2–4 | Add Wi-Fi survey behind a feature flag; per-study hash scope, scan age, throttle setting and classified SSID metadata | No raw identifiers written; checker passes; phone permissions and cached scans tested; no cross-device joins without documented compatible hash scope |
| 5 / days 3–5 | Wire full prompt baselines, observer tags, detection-delay labels and comparison exports | Scheduled/always/fixed/never policies reproducible; wrong-turn delay and false alarms measured, not just scripted |
| 6 / days 4–6 | Collect at least 12 pilot walks; train only if enough independent labels exist | Human-observed errors distinct from detector predictions; grouped evaluation by walker and session; keep v0 if v1 does not justify replacement |
| 7 / days 4–7 | Add lean offline evaluations: ML Kit versus one OCR baseline; k-NN scarcity baseline; churn summaries | Fixed splits/seeds; tables regenerated from files; zero-AP prior baseline defined; model/phone timing measured locally |
| 8 / days 6–8 | Hardening and second-device smoke test where available; freeze documented build | No demo-blocking defects, privacy review, parity tests for any learned model and updated datasheet |

These eight gates define the proposed “80% engineering” milestone. Do not mark it achieved until each is evidenced. Participant trials, final research claims, publication, public dataset release and any neural fine-tuning remain outside that milestone. Guide/department approval and consent are prerequisites to participant recruitment, not prerequisites to developing the synthetic demo.

## Team handoff

- A owns integration, planners, route UI and daily working APK.
- B owns camera, conservative recognition, image enrolment and held-out photo splits.
- C owns motion, session events, later survey collection and device checks.
- D owns graph validation, measured map, offline analysis and research evidence ledger.

Merge only after build and relevant checks pass. Freeze a known-working APK before changing dependencies. Ask the team only for facts software cannot create: room sketch, measured distances, device observations and approved trial arrangements.
