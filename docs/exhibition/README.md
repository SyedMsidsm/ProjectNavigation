# RAIN exhibition kit · 7 November 2026

Use [the Gemini prompt](GEMINI_SLIDE_PROMPT.md) to create the eight-slide presentation. Attach the [route confirmation](media/01-route-choice.png), [college guidance](media/02-campus-guidance.png), [return prompt](media/03-return-prompt.png) and [mark-place screen](media/06-mark-place.png). Review the exported PowerPoint offline on the exhibition laptop. Use [the team field guide](JUDGES_AND_DEMO.md) for the pitch, live walk and judge questions.

Keep [the 64-second captioned backup](media/RAIN-college-demo.mp4) on the laptop. This is a real Android screen recording of the college map with simulated footfalls. It shows route choice, guidance, overshooting the turn, counting return steps, manual confirmation and replanning. It does not document physical positioning accuracy. The video is silent so a presenter can narrate it.

## Verification for build 0.10.1-exhibition

- Core suite: 101 tests passed. Android instrumentation: 21 passed on the S24 Ultra. Android assembly and lint completed.
- The phone's saved College third floor map contains 14 named places. Both planners produced finite, connected routes for all 182 distinct ordered pairs, giving 364 planning checks. Loading the recorded graph corrects three segments associated with retraced corridors. The stored source map is preserved.
- The water filter → Research Centre route is 55.3 estimated metres under both policies. The phone showed approximately 31 m to the first left turn; after confirming the corner it displayed approximately 24 m remaining. The room-side marks along the initial corridor stay selectable.
- The missed-turn popup and return count were exercised on the S24 with demonstration inputs. The counter fell from ten steps to five after five steps with no simulated turn. Manual landmark confirmation replanned from the corner. The JVM suite also exercises passage beyond the final destination and a return to it.
- Physical floor geometry, pace-dependent accuracy, backwards/sideways gait sensitivity, locked doors, live speech and real signage need an on-site rehearsal. At the stall a preview may start at any saved place. During a live walk start only from the place where the group is actually standing. Record a physical walkthrough separately if available.
