# RAIN AR route preview

> Historical record for versions 0.2–0.5. Version 0.6 replaces this preview with [continuous AR walking navigation](AR_WALKING.md). Use that guide for current behaviour; the old pause/realign instructions below no longer apply.

Added 22 September 2026 as the first approved AR stage. This is a manually aligned preview of a short section of the selected synthetic route. It does not establish the phone's position in the college or update navigation progress from AR motion.

## How to use it

1. Select or scan and confirm the starting location, choose a destination and start navigation.
2. Tap **AR route preview** below the sign-scan button. Navigation sensors pause while the AR activity is open. The instruction and remaining distance are the values at launch.
3. Allow camera access. If prompted, install or update Google Play Services for AR and return to RAIN. That first service installation may need connectivity; the preview uses local tracking and has no Cloud Anchors or Geospatial service.
4. Stand at the chosen start position and face along the next corridor. Slowly move the phone while pointing diagonally down at a textured, well-lit floor. Centre the crosshair on the floor.
5. When **Floor found** appears, verify the detected surface really is the floor and tap **Align arrows with corridor**. The camera's horizontal direction establishes the route's initial heading; the camera's projected floor position establishes its origin.
6. Inspect the green arrows while standing still. Arrow centres are spaced 0.75 m apart over at most the next 6 m, following route bearings and the current edge offset. A turn beyond that preview distance is represented only by the map guidance.
7. Tracking loss hides and removes the arrows. Find the floor and align again. Backgrounding or rotating the activity also requires a fresh alignment. **Return to map** releases the AR camera and resumes ordinary navigation.

For a turn within the preview window, choose **Room 203 → CSE Lab 204**, select **Shortest path**, and open AR immediately after starting. That synthetic route goes 5 m ahead to Lab junction and then turns right. The 6 m preview therefore includes the corner. Use it as a stationary visual demonstration until a matching measured test space is available.

## Implemented scope

- Optional ARCore 1.54.0 dependency and internal Android activity. Unsupported devices can continue with map navigation.
- Runtime camera permission and AR service installation handling, with actionable failure messages.
- OpenGL camera background using ARCore display coordinates, plus one local anchor for the arrow sequence.
- Upward-facing horizontal plane hit test, manual heading alignment, relative route geometry and visible floor crosshair.
- Dedicated camera ownership: CameraX sign scanning and ARCore do not run together.
- Anchor removal on tracking loss or pause, explicit session close on activity destruction, and no camera recording in RAIN.
- Route preview geometry tests for straight paths, left and right turns, bearing wraparound, remaining-edge offset, distance limit and invalid input.

## Acceptance and limits

Build/test results are recorded in PROGRESS.md. On 23 September the AR build was installed on the S24 Ultra: all five connected tests passed, the live UI reached Arrows aligned, and camera handoff, permission recovery and background relaunch were checked. See [the AR phone report](AR_PHONE_TEST_REPORT.md) for exact evidence and the remaining visual checks. The earlier OCR and navigation results from version 0.1.0-demo are retained separately.

Physical checks still required: user-confirmed arrow appearance and orientation, stable arrows while viewing from another angle, obscured-camera tracking loss, and rotation recovery. Live sensor pause also needs checking in phone-motion mode; the completed route-preservation check used demo mode with zero steps. First-time AR service installation was not exercised because the service was already installed.

This first version has no obstacle detection, wall occlusion, automatic room-to-world registration, persistent anchors, AR-based progress, or arrival verification. A detected horizontal surface may be a table; the user must identify the floor. The sample map is not a measured representation of the surrounding room. A measured college map and a validated pose-alignment method are prerequisites for continuous AR walking guidance.

## Engineering references

- [Enable ARCore and optional installation](https://developers.google.com/ar/develop/java/enable-arcore)
- [Configure and close sessions](https://developers.google.com/ar/develop/java/session-config)
- [Plane hit tests](https://developers.google.com/ar/develop/java/hit-test/developer-guide)
- [Anchor behaviour](https://developers.google.com/ar/develop/anchors)

The previous verified phone build is retained locally as `output/RAIN-demo-pre-ar.apk`. Build the AR version with `./scripts/build.ps1` and install with `./scripts/install.ps1` when the phone is connected. Downgrading to the old APK may require an explicit downgrade or reinstall; do not delete app data just to switch versions.
