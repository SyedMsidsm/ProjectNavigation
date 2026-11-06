# RAIN demo: record, save, navigate

RAIN estimates indoor movement from phone steps and turns, draws a trail while you walk, and saves named places. You then choose a known starting place and destination and follow that recorded route. It does not discover your absolute indoor location automatically or produce a surveyed architectural floor plan.

## One-floor demonstration

1. Open Record a trail. Enter a recognisable starting place, such as Entrance. Hold the phone normally in your hand and face along the first corridor.
2. Start recording. Walk slowly and normally. Show the step count and growing trail. If no steps arrive, stop, select the motion-detection alternative, and Continue walking. Do not switch sources while moving. This does not recover already missed steps.
3. Stop at a place. Select Mark this place, enter Library (or the actual name), save it, and Continue walking without changing position or heading. Repeat for two or three places. Take clear corridor turns rather than moving the phone independently.
4. Stop and save, enter a map name, then Navigate on this map. An unnamed final walking segment receives an End of walk destination.
5. Set Current place to where you physically are. Type or speak a destination, or select a saved place. For an indirect request such as “Where can I borrow a book?”, Gemma may suggest an existing Library; confirm the suggestion. An incorrect/absent suggestion must not start navigation automatically.
6. Face along the route's first corridor and start navigation. Follow the estimated route; show remaining distance and step progress. Phone motion cannot determine the initial absolute heading.
7. At a known sign, use sign scanning and confirm the correct match to reset the position estimate and replan. Confirm arrival at the actual destination.

## RAIN-specific features to explain

- Shortest, simplest and risk-aware route options. Risk is currently heuristic, not a trained prediction or a measured probability.
- Sign recognition and explicit position correction/replanning to recover from drift.
- Offline saved maps, local destination suggestions and on-device speech where the required speech language is installed.

Do not present a straight, single-path trail as evidence that risk-aware routing is superior: alternative paths are needed for a meaningful comparison. Do not claim field accuracy from unit or instrumentation tests. AR and research collection remain outside the main demonstration.

## Validation of 0.9.0-dark-demo

Build, lint and 81 core tests passed. All 18 connected S24 Ultra integration tests passed, including the real local-model test. Installed in place, preserving maps and model. Dark home screenshot inspected at tmp/rain-dark-home.png. Physical step count, turns, route following and spoken recognition in the user's environment still require rehearsal.

The phone has activity recognition permission. Hardware sensor registration was successful in the prior session, so zero steps cannot honestly be attributed to a missing permission. The repair handles registration failure, avoids unnecessary sensor restarts, and offers a single-source accelerometer alternative. A walking trace is required to establish whether this addresses the observed failure.
