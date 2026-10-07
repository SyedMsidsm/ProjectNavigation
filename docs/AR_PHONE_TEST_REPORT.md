# AR phone verification

Date: 23 September 2026. Device: Samsung Galaxy S24 Ultra SM-S928B, Android 16. App: 0.2.0-ar-preview. Device identifiers and camera imagery are omitted.

## Build and automated checks

- APK assembly and Android lint passed after the camera-permission recovery fix; zero lint errors.
- All 44 core tests passed or remained up to date against unchanged core sources.
- All five Android integration tests passed on the physical S24 Ultra. Three cover offline OCR on generated signs, graph/model/sensor availability and session writing. Two new tests cover OpenGL camera/arrow shader compilation on the phone GPU, and the optional AR manifest/internal activity configuration.
- Google Play Services for AR was already installed (reported version 1.56.262080393). RAIN installed and launched successfully.
- Evidence: `.tools/ar-phone-preflight-build.log`, `.tools/ar-phone-tests.log`, and `android/app/build/outputs/androidTest-results/connected/`.
- After the activity-stack fix, build/lint and all five connected tests passed again (`.tools/ar-phone-final-check.log`). The final test also asserts singleTask launch configuration.

## Observed activity flow

- Selected Room 203 to CSE Lab 204, shortest route: 10 m total, right turn after 5 m.
- Started navigation at zero steps and opened the AR preview. The AR screen showed the same instruction, 10 m remaining at launch and navigation paused.
- Camera permission was requested. Granting it opened the AR session. The UI advanced from tracking paused to searching for the floor; the alignment button remained disabled until a valid floor hit.
- No RAIN fatal error was observed in the app-filtered check during this launch.
- The live UI subsequently reported **Arrows aligned**, establishing that a valid floor hit and local anchor creation completed. Visual arrow appearance and geometric accuracy still require the user's observation.
- Returning from AR preserved the 10 m route and zero steps. Reopening AR cleared the old anchor and required fresh alignment.
- AR to CameraX sign scanner to AR handoff completed. Android's camera service showed RAIN owning the camera during the scanner check. No camera frames were saved.
- The three-record session (start, route, end; no motion events) was exported as `output/ar-phone-session.jsonl` and passed the schema/identifier checker. This is a stationary UI test, not a walking trial.
- Camera denial/retry passed on the final build: dismissing the permission request showed the disabled-camera message and App settings control; Retry followed by granting permission started AR and removed the settings control.
- Background/launcher regression passed on the final build: Home followed by the launcher intent returned to the original navigation activity, preserving Room 203 to CSE Lab 204, 10 m remaining and zero steps. The task contained one MainActivity and no duplicate or stale AR child.

## Remaining live checks

Visible arrow appearance, anchor stability, covered-camera tracking loss, and physical rotation recovery remain to be confirmed by direct observation. Successful shader compilation and an aligned status do not by themselves establish geometric accuracy. Background relaunch exposed an activity-stack issue; the fixed behaviour passed the regression described above.

## Fix made during verification

The AR screen now offers App settings when camera access is denied, so it can recover when Android no longer offers another permission prompt. Permission requests are tracked to prevent automatic repeated prompts on activity resume. Retry AR remains available for an explicit new attempt.

The background/launcher test exposed a duplicate MainActivity above the existing AR activity. The original navigation session remained underneath, but the new home screen made it appear lost. MainActivity now uses singleTask launch behaviour, and both install scripts use the launcher action/category. Relaunch should return to the original map activity, preserving its ViewModel and closing the AR child.

No measured campus localisation, walking accuracy or arrival accuracy is claimed by these checks.
