# AI collection verification — updated 6 October 2026

Version: 0.5.0-ai-collection.

Installation completed on 6 October 2026 after renewed USB approval. Android reported install success, MainActivity launched successfully, and the installed package reports versionCode 5 / 0.5.0-ai-collection. The user requested installation only while in class; live camera checks are deferred. No capture or campus reference was created during installation.

| Check | Result |
|---|---|
| Kotlin core regressions and new visual-evidence tests | 53 passed |
| Python regressions, dataset leakage guards and synthetic fusion-training pipeline | 29 passed |
| Main APK compilation and lint | Passed; zero lint errors |
| Android instrumentation APK compilation | Passed after retrying a restricted cache-file access failure |
| Actual model inference on S24 Ultra | Passed in the completed 13-test connected run |
| Dataset persistence/export integration on phone | Passed using isolated synthetic test data |
| Live camera capture/review/discard | Deferred at the user's request while in class |
| Saved campus references or trained fusion model | None; data collection requested first |

The completed connected run contains 13 tests, zero failures and zero errors. New tests check deterministic finite model outputs, preservation of the original bitmap after embedding, isolated study storage, session separation, reference freezing, duplicate capture rejection and ZIP contents. Earlier AR/OCR/sensor tests remain in the suite. The successful log and XML report were inspected on 6 October; these supersede the earlier pending statuses. No fresh campus accuracy result is implied.

After USB approval, run `scripts/build.ps1 -PhoneTests`, then `scripts/install.ps1`. Open **AI place collection**. For a smoke check, capture a non-sensitive sign, verify a review image and scores appear, then discard it. Do not save a printed demo or incidental scene under a real campus-room label. Actual references need the correct physical location and an explicit save action.

Main/final log: `.tools/ai-collection-final-build.log`. Successful connected test log: `.tools/ai-collection-phone-final.log`. Deliverable: `output/RAIN-ai-collection.apk`, with checksum. On 6 October, the tested main APK and build-output APK had identical SHA-256 hashes. Installation/live-camera follow-up requires renewed USB authorisation. No on-phone timing benchmark or campus accuracy result is claimed.
