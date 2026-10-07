# S24 Ultra verification

Device: Samsung SM-S928B (Galaxy S24 Ultra), Android 16 / API 36. Checked on 21 September 2026 over authorised USB debugging. Device serial is deliberately omitted.

## Observed on the phone

| Check | Result |
|---|---|
| APK install and activity launch | Passed: streamed install succeeded; `in.mite.rain.MainActivity` launched and rendered the graph-backed home screen |
| Packaged floor and Room loading | Home screen loaded all sample labels; planner comparison rendered |
| Default route comparison | Shortest 35 m / 2 turns / 0.76 heuristic score; risk-aware 40 m / 1 turn / 0.18; weighted simplest also 40 m |
| Navigation start | Correct initial instruction: walk 6 m toward Library; labelled demo mode and zero steps shown |
| Wrong-turn recovery | After five simulated steps, a simulated left turn raised `unexpected_turn` and a scan prompt |
| Text anchor and replan | Typed sample LIBRARY matched; explicit confirmation replanned to CSE Lab 204 with 34 m remaining and reset displayed drift |
| Navigation log | Retrieved the actual private app session through ADB; all 16 records passed the Python schema/identifier checker |
| Camera preview | Opened on the physical phone. A SurfaceView overflow was observed; changed to CameraX COMPATIBLE/TextureView mode with clipping for the scrollable Compose dialog |
| Live camera recognition | User aimed the phone at the sample ROOM 201 sign and reported “room 201” after being asked to match detected text. This is one successful user-observed sign check, not an accuracy benchmark |

The recovery session intentionally ended without arrival (`completed=false`). Its recorded events include five simulated steps, a simulated turn, off-route warning, prompt, typed recognition, explicit manual location confirmation and replan. It is not a real walking trial.

## Automated phone checks

`PhoneSmokeTest` exercises bundled ML Kit against three generated bitmap signs while the app has no Internet permission; Room + asset model integration; hardware step/relative-rotation sensor availability; and the ordered session writer with nullable unverified arrival. Run with:

```powershell
./scripts/build.ps1 -PhoneTests
```

All three tests passed on the S24 Ultra on 21 September 2026 (`.tools/phone-integration-retry.log`, connected task completed in 21 seconds). Core tests and Android lint also passed in that build. The verified APK and SHA-256 checksum were refreshed in `output/`.

The tests use generated labels, not photographs or a camera feed. Passing them does not establish camera focus quality, degraded-sign accuracy, step-distance calibration or indoor localisation accuracy.

## Remaining physical acceptance

- Check preview layout visually and test additional real-camera signs under different lighting.
- Walk a measured distance with phone motion enabled; compare step count and calibrate step length.
- Test actual left/right turns with a consistent phone pose and then a 30 m / two-turn mapped route.
- Replace the synthetic floor with a measured sketch before assessing campus arrival or wrong-turn performance.

No raw camera frames or OCR text belong in the retained evaluation logs. Only synthetic test artefacts and redacted app UI screenshots should be kept with this report.

The initial connected-test attempt failed during APK installation because earlier builds used different debug signing keys. The test runner removed the earlier app during cleanup; the already-exported recovery session remains in `output/phone-session.jsonl`. The updated app was successfully installed. Debug signing now uses the ignored project-local `.tools/debug.keystore` to keep subsequent local builds compatible. Do not delete this key if you need to update an installed demo without reinstalling.
