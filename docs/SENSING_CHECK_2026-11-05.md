# Reference sensing check - 5 November 2026

Installed and verified version 0.9.2-reference-sensing on the authorized Samsung SM-S928B. Build/lint succeeded; 89 core tests passed, including historical trace replay with labelled turn-angle assertions. All 18 phone instrumentation tests passed. In-place installation preserved private data.

Historical reference replay (not new S24 field results):

| Trace | Hardware steps preserved | Reference fallback peaks | Clockwise-positive confirmed turns |
|---|---:|---:|---|
| straight_walk_talha | 40 | 40 | none |
| turns_walk1_talha | 54 | 31 | -92.91, 84.54, 79.25 |
| turns_walk2_talha | 64 | 50 | -83.62, 88.96, 81.77 |
| spike_walk_talha | 38 | 34 | 87.74, -92.48, 87.52 |

Hardware events are preserved by the pipeline; turns match the reference's independent labels within 15 degrees after sign conversion. Fallback undercount in several historical traces means a direct port alone does not establish accurate step detection. No new S24 walking success is claimed yet.

Current physical protocol: five seconds stationary, exactly 20 straight steps, three seconds stationary, then pause. Keep the current source unchanged and record its visible label. Only RAIN motion tags are captured in tmp/s24-reference-live.log for analysis. The user has been asked for observed count and map growth; result pending.

Evidence: .tools/reference-sensing-phone-final.log, .tools/reference-sensing-resume.log, android/core/build/test-results/test/TEST-in.mite.rain.core.ReferenceTraceReplayTest.xml.

## Physical results confirmed on 6 November

The user confirmed that the final test included two actual right turns. The saved trace contains 20 steps and clockwise turns of 98.4758 and 90.7447 degrees. Both map corners snap to 90 degrees. The user reported that this recording worked. Evidence: tmp/s24-corner-success-events.jsonl and tmp/s24-corner-success-motion.csv. This is one successful short trial, not general accuracy validation.

An earlier uninterrupted walk was reported as 22 actual/22 counted steps. Another 28-step trial counted 22, but the user confirmed pausing or switching apps; sampling gaps prevent treating that as a continuous-detector accuracy measurement. An earlier L-shaped test recorded about 187 degrees and needs separate handling/orientation investigation; the successful two-corner test does not explain that earlier observation.

Selected working source is the reference accelerometer detector, persisted in rain_motion preferences. Samsung hardware-step mode previously delivered zero events despite active motion samples. Do not silently switch back to hardware mode.

Next acceptance: save the physical trail, reload it from Saved maps, select actual start/destination, verify forward and return instructions and physically follow the route. On resuming 6 November, ADB reported no connected devices. No new device acceptance was performed that day yet.

## Saved-map check after reconnecting on 6 November

The phone was subsequently connected. RAIN reopened on Home; the library already contained turf with three named places. Reloaded this existing map without overwriting it. The planner displayed bench as current place and entrance as destination, with the local model imported.

Read-only inspection of active-floor.json found bench -> kichqd -> entrance, two bidirectional segments of approximately 5.6 m and 9.1 m, bearings 0 and 90 degrees. Coordinates agree with edge lengths. Forward geometry requires a right corner; reverse geometry requires a left corner. These are graph checks, not proof of physical guidance. This saved map differs from the earlier 20-step/two-corner trace and is not assumed to be that same test.

The user is not currently at the recorded location. Physical navigation and arrival verification are deferred. No live journey was started remotely. Resume with the user physically at a known saved endpoint, confirming initial facing direction before starting.
