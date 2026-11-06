# RAIN demo UI work — 2 November 2026

Remaining: guided recording editor; clear save-to-navigation handoff; active map selection before start/destination; prominent walking map and next action; hidden research controls; permission and interruption review; physical walk acceptance. Earlier automated evidence does not establish field accuracy.

Initial source changes: removed home marketing hero; tucked research/assistant entry points under Extra tools; clearer planning text; removed duplicate destination search panel and collection button from planning; quieter palette; screen stays awake during recording. These are an initial pass, not a completed redesign.

## Optional Antigravity design prompt
Redesign the existing native Kotlin/Jetpack Compose RAIN app for a live indoor trail demonstration. Preserve the existing ViewModel, recorder, graph and persistence contracts. First inspect the existing code. Do not replace it with a website, invented backend or mock navigation. Primary flow: name starting place, hold phone facing the corridor, start recording, see the estimated trail grow while walking, stop to mark a named place, resume, finish and save, choose saved map, select current place and destination, preview route, start guidance, confirm arrival. Include empty, denied-permission, paused, saving, validation-error and tracking-uncertain states. Keep research, AR and local-model features outside the main demo flow. Use warm white, charcoal and one restrained green accent, flat surfaces, no gradients, glowing effects, hero illustrations or promotional slogans. Use a single system sans-serif family, regular body text, medium/semibold titles, italic only for short optional notes. Use an 8dp spacing rhythm, 24dp screen padding and at least 48dp touch targets. One primary action per state. The map and next action should remain prominent while walking. Show honest estimated-position status without raw developer metrics. Do not claim sensor accuracy or invent measurements. Deliver Compose implementations and explain state transitions, then compile and inspect on a phone.

Research: https://www.nngroup.com/articles/progressive-disclosure/ ; https://m3.material.io/styles/typography/applying-type ; https://support.google.com/accessibility/android/answer/7101858

User confirmed single-floor demo; physical phone checks later. Recording now places loop/floor controls behind More recording options, resets mark input on opening, and gives explicit stationary marking/resume guidance. Build/QA log: .tools/demo-ui-build.log. Further work remains; do not label this initial cleanup demo-ready.

## Workflow build, 2 November 2026
- Save success now has a dedicated Navigate on this map action. Opening a fresh recording clears the previous success state.
- Map switching waits for loading to finish before clearing its busy state.
- Route comparison is collapsed by default while the selected route remains identified.
- Walking map moved ahead of secondary controls; AR, simulated motion, full path, exports and observer tools grouped under extra navigation tools.
- Source retained for research tools; no saved user maps deleted.
- Build verification: .tools/demo-workflow-build.log. Physical walk and rendered phone UI checks pending by user preference.

Next acceptance sequence: record a start and two named places on one floor; save; use the direct navigation action; verify active map/name and start/destination; navigate back; confirm arrival; reopen saved map; repeat with interrupted recording and invalid/duplicate names. Actual step distance, turn response and map orientation must be observed on the phone.
