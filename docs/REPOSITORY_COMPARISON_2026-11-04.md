# Repository comparison — 4 November 2026

## Verified versions

| Repository | Inspected revision | Role |
|---|---|---|
| mohammed-ifzal/breadcrumb | 31c847632e8b565777a47613bb9091a0b11787e9 | Original Flutter/Dart navigation reference; local checkout at .tools/reference-navigation |
| Talha-Khan-47/breadcrumb-fyp | 0bbbf5b5c538d752422d938297cb02e57b6ff396 | IndoorNav testathon/urgent APK release copy |
| SyedMsidsm/ProjectNavigation | Local base cfd007f plus uncommitted RAIN work | Native Kotlin/Compose RAIN implementation; Flutter is not embedded |

GitHub's comparison reports the FYP copy seven commits ahead of the original inspected revision. The first is “Prepare IndoorNav FYP testathon release and documentation”; the latest is “ci: longer timeout, arm64 release build”.

## What changed in the urgent APK copy

- App name became IndoorNav; application ID changed from com.teamgroza.breadcrumb to com.fyp.indoornav.
- Release workflow builds an ARM64 release APK, with a longer timeout, release attachments and project documentation.
- Gemma is explicitly disabled by enableOnDeviceAi = false in lib/core/tuning.dart. Loading, AI destination resolution and generated aliases are gated off. On-device speech and saved-name/alias matching remain.
- Documentation, presentation text and related tests changed.
- The inspected step detector, sensing gates, turn detector, map builder, tracker, map screen and navigation screen have identical Git blob hashes across these two revisions. There is no evidence of a different walking engine in this copy.

Both downloaded IndoorNav-v1.0 APK files match the GitHub v1.0-testathon release asset SHA-256 exactly:
ff59a3f55836ed8138d208b2ba47b297331d6c85c0aa93011468891fb2a81b13

This identifies the earlier APK precisely; it is not the native RAIN APK. It also explains the screenshots' Basic matching label and failure to interpret an indirect request such as washing one's face without a matching saved alias.

## RAIN differences and demo priorities

RAIN implements its own native sensor adapter, trail recorder, saved-map storage and UI. It already includes local Gemma inference and offline speech support, sign OCR, route comparison (shortest, simplest and heuristic risk-aware), correction/replanning, calibration and optional AR. Existing software/device tests do not prove physical walking accuracy.

Prioritise: working step events; visible recording and map growth; named landmarks; reliable saving; current-place and destination selection; typed/spoken matching with confirmed Gemma suggestions; map guidance; sign-based recovery. Keep route comparison optional. Leave research tools and AR outside the main demo path.

Remaining parity work: walking/turn traces on S24 Ultra, mark undo, closure correction behaviour, unsaved-trail recovery after process death, physical return-route orientation rehearsal, and measured accuracy. These are not completed by changing the theme.

## Evidence links

- https://github.com/Talha-Khan-47/breadcrumb-fyp/compare/31c847632e8b565777a47613bb9091a0b11787e9...0bbbf5b5c538d752422d938297cb02e57b6ff396
- https://github.com/Talha-Khan-47/breadcrumb-fyp/releases/tag/v1.0-testathon
- https://github.com/mohammed-ifzal/breadcrumb/tree/31c847632e8b565777a47613bb9091a0b11787e9

## This repair

RAIN 0.9.0-dark-demo restores typed and spoken destination search to the main planner and applies dark surfaces with a teal action colour. Sensor start no longer resets an already running sensor session; failed hardware registration now attempts accelerometer fallback. A paused recording offers an explicit motion-detection alternative for a silent hardware detector. Switching never combines two step sources, and does not add missed steps retrospectively. Accelerometer estimates can count handling motion and need physical validation.

The connected S24 Ultra has ACTIVITY_RECOGNITION permission and reports successful hardware step-detector registration. That excludes a missing permission as the observed cause, but does not establish which physical event was lost. A measured walk is still required before declaring the zero-step issue resolved.
