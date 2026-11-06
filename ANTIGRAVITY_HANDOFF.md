# RAIN Android handoff | 6 November 2026

Start here for the app. The academic report handovers describe a separate deliverable. Latest implementation is 0.9.6-guided-setup (Android version code 17). The Android app is Kotlin/Jetpack Compose, with a JVM core. The demo uses one floor, a real recorded walk and offline place search on a Samsung S24 Ultra.

## Open this exact workspace

On this computer attach C:\Users\Musid\Desktop\RAIN as a local project folder in Antigravity. This preserves the Android SDK, Gradle cache, local signing key, model download, prior screenshots, traces and build outputs. On another computer clone https://github.com/SyedMsidsm/ProjectNavigation and check out the demo handoff branch codex/demo-handoff. Verify branch, git log -1 and git status before editing. Re-read this file if the local checkout has advanced. Earlier design docs and demo PDFs are historical. See docs/ANTIGRAVITY_PROMPT.md for the first message.

## Product and evidence

- Record a trail with a named starting point, optional room-sign OCR, measured steps and turns, live schematic, named places, pause/resume and save. Back at the starting place closes an explicitly confirmed return. Close only when physically there. The T9 case required splitting an existing entrance corridor and reusing its marked points. A corrected map copy is on the demo phone. Private home maps stay on the phone.
- Saved maps use UUID files and an atomic active-floor snapshot. New saves activate the recording. Use this map waits for activation and graph load, then opens the location question. The map name at the top opens the library. Check same-name maps, failed writes, active rename/delete and recreation. Deleting a library file currently retains the active snapshot until a different map is selected.
- In Navigate, optional sign scan → Where are you now? → Where do you want to go? → guidance. Skip or dismiss scanning freely. Both questions accept text, speech or saved points. The user confirms a match. The destination supports offline Gemma when name/alias/fuzzy lookup has no answer. The ordinary workflow uses the selected graph and no longer embeds the older planner's preview. Advanced campus tools retain the legacy planner.
- Local Gemma 3 1B int4 uses LiteRT-LM CPU. Device tests cover book → library, hands → washroom, and absent pool → no suggestion. Speech recognition is Android on-device recognition and needs an installed language pack. Spoken guidance is independent Android TTS. User confirms actual arrival. Model weights (584,417,280 bytes) are separate from Git and APK. Existing phone has the model. See scripts/prepare-assistant.ps1 for trusted local artifacts. For another phone obtain authorized weights from litert-community/Gemma3-1B-IT.
- A short user trial counted 22/22 steps. Another trial had two confirmed right turns. Later full-map trial worked with some measurement error. These are individual observations. Regression playback of a difficult paused walk with cadence filtering gives 24 steps, ~89° and ~90°; ground-truth step total for this trace is unknown. User verified turning phone in place rotated the arrow with zero steps on an earlier build.
- Estimated position follows route progress. The raised faceted arrow, translucent cone and tilted corridor drawing are schematic. Align facing along the route for relative gyro heading. Phone yaw can differ from body heading. A fast pitch/roll filter briefly suppresses events after abrupt handling. Pocket/bag navigation, slow turns, device repositioning and real arrival accuracy still require measured trials.
- RAIN-specific features include a risk-aware planner, prompted sign verification, typed/voice local destination matching and optional AR and research tools. Use camera and experimental features only where useful. No GPS or backend is required for the recorded-trail demo.

## Code and commands

- android/app/.../ui/RainApp.kt routes screens; WalkSetup.kt owns staged questions and sign offer; TrailScreens.kt handles recording/library; RainViewModel.kt owns session, map, model and sensor state. FloorMap.kt and NavigateScreen.kt draw the map and guidance. platform/RoomScanner.kt, OfflineDestinationSpeech.kt, GuidanceFeedback.kt and LocalDestinationAssistant.kt are separate input/output services.
- android/core/src/main/kotlin/in/mite/rain/core/TrailRecorder.kt, ReferenceMotion.kt, ReturnToStart.kt and Navigation.kt govern geometry, matching, cadence, turns and route progress. MotionSource.kt adapts Android sensor events. Replay fixtures are under android/core/src/test/resources/reference-motion. User logs are local under tmp and .tools.
- PowerShell in project root: ./scripts/build.ps1 -CoreOnly for JVM tests; ./scripts/build.ps1 -Install -PhoneTests for core, lint, APK, instrumentation and in-place install. ADB: ./.tools/android-sdk/platform-tools/adb.exe devices. Unlock and authorize USB on the S24. Never uninstall or clear app data to update this phone. Preserve .tools/debug.keystore. On a new checkout bootstrap tools with ./scripts/bootstrap.ps1; a differently signed APK cannot update the demo phone. Use original workspace/key for that phone.
- APK: output/RAIN-demo.apk. Current check log: .tools/handoff-final.log. For historical context read docs/SENSING_CHECK_2026-11-05.md, docs/REPOSITORY_COMPARISON_2026-11-04.md and docs/DEMO_STATUS_2026-11-06.md. Talha's reference Flutter fork is Talha-Khan-47/breadcrumb-fyp. The shipped RAIN app is native Android.

## Finish the demo

1. Connect phone and confirm the installed version. Exercise scan, skip, deny camera/mic, no readable text, wrong OCR and re-entry. Use a real sign. Verify a spoken start and an indirect spoken destination; reject ambiguous requests, same start/end, unavailable places and stale model results.
2. Check save/open/rename/delete on a disposable recording, switching between two distinctly named maps, then restarting. Keep all user maps and the working Gemma model. Record concrete failing examples and inspect real UI state.
3. At the venue record a counted straight segment, mark a place, turn, return along the original corridor and confirm Back at the starting place. Navigate both directions and compare spoken cue timing and confirmed arrival with physical position.
4. Record separate labelled trials: 20 seconds stationary, deliberate grip changes, rotating in place, abrupt pickup, 20 measured steps held in hand, pocket and bag, and slow/fast 45°, 90° and 180° turns. Note actual steps, phone orientation, estimated steps, angles, unwanted steps and missed steps. Keep carry changes separate from walking. Tune only against measured traces and replay old successful cases too. If pocket/bag cannot supply reliable body heading, explain the supported carry mode and provide recovery at a known place.
5. Keep the dark one-floor workflow simple. Test small window, large text, keyboard, back gesture, screen rotation, voice errors and duplicate names across floors. Use real screenshots for visual review. Update this handoff after every material verified change, with version, commands and physical evidence.

## Handoff on another computer

    git clone --branch codex/demo-handoff --single-branch https://github.com/SyedMsidsm/ProjectNavigation.git RAIN
    cd RAIN
    git status -sb
    git log -1 --oneline

Attach that directory to an Antigravity project with Add Folder. On the original computer attach the existing directory instead. Start a chat with docs/ANTIGRAVITY_PROMPT.md. The Git repository carries source and handoff evidence. The original machine carries the configured build tools, matching debug key and APK. The demo phone carries its private maps and local Gemma model.

## Latest build evidence

Version 17 installed in place. JVM suite: 93 tests. Android lint passed. Instrumentation: 20 tests on the S24 Ultra including offline model inference. The matching APK checksum is in output/RAIN-demo.apk.sha256. An actual screen test uncovered an incorrect free-text washroom suggestion on a map containing personal place names. For face/hand-washing requests, constrain the model catalog to plausible saved washroom/sink labels or aliases and return no match if none exists. Other open-ended requests still use the full eligible catalog and user confirmation.


A stationary check through the normal phone UI verified both scan prompts can be skipped, opening a saved map enters the current-place question, typing “entrnace” confirms “entrance,” and the indirect request “I want to wash my face” now suggests the saved Washroom. Accepting that suggestion starts a route on the selected map. This check used typed input and skipped the camera. A physical microphone, sign and walking rehearsal is still needed. Ending a walk now returns to Home so a completed journey does not reopen the sign prompt.
On the installed S24, the library UI successfully switched to the corrected map, renamed a disposable copy, and deleted that copy after confirmation. Repeat rename/delete only with disposable recordings. The active snapshot has its own lifetime.
