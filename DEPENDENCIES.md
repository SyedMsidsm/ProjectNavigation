# Verified build baseline

This demo deliberately pins a compatible set rather than upgrading every library during the presentation deadline. Versions live in `android/gradle/libs.versions.toml`; the Gradle distribution is pinned and checksum-verified by the wrapper. These are implementation references, not academic citations.

| Component | Pinned version | Primary documentation |
|---|---|---|
| Android Gradle Plugin | 8.13.2 | [AGP 8.13 compatibility: Gradle 8.13, JDK 17, SDK up to 36.1](https://developer.android.com/build/releases/agp-8-13-0-release-notes) |
| Gradle | 8.13 | [Gradle 8.13 release notes](https://docs.gradle.org/8.13/release-notes.html) |
| Java | JDK 17 | [Android Java build versions](https://developer.android.com/build/jdks) |
| Android SDK | compile/target 36; minimum 26 | [Android 16 setup](https://developer.android.com/about/versions/16/setup-sdk) |
| Build Tools | 35.0.0, AGP default | [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes) |
| Kotlin + Compose compiler plugin | 2.2.21 | [Kotlin releases](https://kotlinlang.org/docs/releases.html) |
| KSP | 2.2.21-2.0.4 | [Official KSP release](https://github.com/google/ksp/releases/tag/2.2.21-2.0.4) |
| Compose BOM | 2025.04.01 | [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) |
| Activity Compose | 1.10.1 | [Activity releases](https://developer.android.com/jetpack/androidx/releases/activity) |
| Lifecycle | 2.9.0 | [Lifecycle releases](https://developer.android.com/jetpack/androidx/releases/lifecycle) |
| Core KTX | 1.16.0 | [Core releases](https://developer.android.com/jetpack/androidx/releases/core) |
| CameraX | 1.4.2 | [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera) |
| ML Kit bundled Latin text recognition | 16.0.1 | [Official Android setup and model options](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) |
| Room + compiler | 2.7.2 | [Room releases](https://developer.android.com/jetpack/androidx/releases/room) |
| Coroutines | 1.10.2 | [Official coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases) |
| JUnit | 4.13.2 | [JUnit 4 project](https://github.com/junit-team/junit4) |
| Android test runner (test APK only) | 1.7.0 | [AndroidX Test releases](https://developer.android.com/jetpack/androidx/releases/test) |
| ARCore optional route preview | 1.54.0 | [Official ARCore Android SDK](https://github.com/google-ar/arcore-android-sdk) |

The bundled OCR model avoids a first-run model download. The merged app explicitly removes the `INTERNET` permission contributed by transitive libraries, so the demo cannot make network requests. Ordinary OCR/AR camera frames remain transient. In the separate AI collection workflow, explicitly confirmed photos and numerical features are stored privately and included only in the user-requested dataset ZIP; raw OCR text is not stored there.

The AI collection release adds `com.google.mediapipe:tasks-vision:0.10.32` and Google's official pretrained MobileNetV3 Small embedding asset. Source, SHA-256 and the distinction between pretrained embeddings and untrained campus fusion are documented in `docs/AI_COLLECTION.md`. This does not change the app's network-permission removal.

The optional AR preview requires Google Play Services for AR on a supported device. Installing or updating that separate service can require connectivity. The preview uses local anchors with no Cloud Anchors, Geospatial API or RAIN camera recording. See `docs/AR_DEMO.md` for alignment and physical acceptance requirements.

`scripts/bootstrap.ps1` downloads official Eclipse Temurin JDK 17, Gradle 8.13 and Google Android command-line tools into `.tools/`. JDK/Gradle archives are checked against the provider SHA-256; Google's command-line archive is checked against the official repository manifest checksum. SDK Manager installs the Android platform and build tools. SDK licence acceptance is explicit in interactive setup, or selected with `-AcceptSdkLicenses`.

Do not upgrade the baseline during the demo rehearsal. Make later upgrades in a separate change and rerun core tests, assembly, lint, and camera/motion tests on the S24 Ultra. No SDK, signing key, Gradle cache, raw dataset or local path belongs in source control.
