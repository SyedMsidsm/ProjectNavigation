# Reference parity audit — 2 November 2026

Baseline found locally: .tools/reference-navigation, upstream documented as mohammed-ifzal/breadcrumb, revision 31c847632e8b565777a47613bb9091a0b11787e9. Downloads also contains IndoorNav-v1.0.apk; no proof yet that this binary matches that source revision. User reports teammate tested the original walking/map workflow. Do not transfer that physical acceptance to RAIN.

Architecture: original Flutter application was not embedded. Native Kotlin recording, sensing and UI were reimplemented. Treat the original as a behaviour baseline, not equivalent code.

Confirmed difference fixed: original record_screen.dart adds a selectable End of walk if stopping after the last mark. Native finish previously flushed an unnamed junction, making that endpoint unavailable to destination selection. Native finish now names an unnamed endpoint, avoids same-floor name collisions, preserves a previously marked endpoint, and remains idempotent on retries. Three regression tests added.

Still to audit: mark undo, loop closure correction/confirmation, sensor gates and raw-event traces, persistence on interruptions, orientation instructions when reversing routes, original APK/source match. No claim that all merger issues have been reproduced. Phone testing deferred by user. Build log: .tools/reference-parity-build.log.
