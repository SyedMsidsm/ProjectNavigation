# Connected phone check — 4 November 2026

Samsung S24 Ultra SM-S928B connected and authorised. Updated RAIN using adb install -r; no app-data clearing/uninstall. Inspected screenshots of home and planning screen. Home actions and selectors readable; start action was buried below the map. Moved Start navigation directly below selectors and removed repeated planning branding.

All 18 Android integration tests passed using scripts/build.ps1 -PhoneTests. Log: .tools/phone-demo-check.log. Includes recorded-map serialization/persistence and native component checks. No physical walk performed by agent. No claim of measured navigation accuracy.

Final layout build/install log: .tools/phone-layout-build.log. Screenshots: tmp/rain-ui.png, tmp/rain-plan.png (before latest layout correction).

Next: short single-floor live walk, mark start and endpoint with a 90-degree turn, save, select saved map/current place/destination, navigate back. Observe live map growth, correct turn direction, endpoint selection and recovery. Phone connection alone cannot perform physical walking.
