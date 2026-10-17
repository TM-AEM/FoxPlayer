# FoxPlayer 🦊

FoxPlayer is a modern, privacy-first local video player for Android built with **Jetpack Compose**, **Material 3**, and **AndroidX Media3 (ExoPlayer)**.

## ✨ Features

- **High-Performance Playback:** Powered by AndroidX Media3 1.5.1 with hardware-accelerated decoding.
- **Background & PiP Support:** Native `MediaSessionService` support with lock screen controls and Picture-in-Picture mode.
- **Smart Touch Gestures:**
  - Double-tap to seek (configurable 5s, 10s, 15s, 30s).
  - Vertical swipe on the left for Brightness control.
  - Vertical swipe on the right for Volume control.
  - Long press for temporary 2x speed boost.
- **Track & Subtitle Selection:** In-player bottom sheet for audio stream selection and embedded/external subtitle tracks.
- **Playback History & Resume:** Seamlessly resumes playback from the last saved position using a local **Room Database**.
- **Physical LTR Timeline:** Playback progress always advances left-to-right (00:00 to end) with stable Western digits (`01:25`), even under full **Arabic (RTL)** interface layouts.
- **Configurable Settings:** Application preferences (Language, Theme, Resume position) persisted safely via Jetpack **DataStore**.

---

## 🛠 Tech Stack & Architecture

- **Language:** Kotlin 2.2.10 (with Coroutines & Flow)
- **UI Framework:** Jetpack Compose with Material 3 (M3)
- **Media Engine:** AndroidX Media3 (ExoPlayer) 1.5.1 & MediaSession
- **Local Database:** Room 2.7.0 (with KSP)
- **Preferences:** Jetpack DataStore Preferences
- **Build System:** Gradle 9.3.1 (Kotlin DSL)
- **Target Platform:** Android SDK 36 (`minSdk = 24`, `targetSdk = 36`)
- **Architecture:** Clean MVVM with Repository Pattern

---

## 🚀 Building & Testing

### Prerequisites
- **JDK:** OpenJDK 21
- **Android SDK:** API Level 36 with Build-Tools

### Build Debug APK
```bash
./gradlew :app:assembleDebug
```
The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit & Robolectric Tests
```bash
./gradlew :app:testDebugUnitTest
```

### Build Minified Release APK
Release builds use R8 code shrinking and resource optimization:
```bash
KEYSTORE_PATH="/path/to/keystore.jks" \
STORE_PASSWORD="your-password" \
KEY_PASSWORD="your-password" \
./gradlew :app:assembleRelease
```

---

## 🔢 Versioning

The app version is defined in exactly one place: the root-level **`version.properties`** file.

```properties
VERSION_NAME=1.0.0
VERSION_CODE=2
```

- **`VERSION_NAME`** is user-facing and follows [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`).
- **`VERSION_CODE`** is the internal, strictly increasing positive integer required by Android and Google Play.

`app/build.gradle.kts` reads this file at configuration time and fails the build with a clear error if the file is missing, either value is absent, `VERSION_CODE` is not a positive integer, or `VERSION_NAME` is blank.

### Bumping the version

| Change type | Rule | Example |
| --- | --- | --- |
| Bug fix | Increment `PATCH` | `1.0.0` → `1.0.1` |
| Backward-compatible feature | Increment `MINOR`, reset `PATCH` | `1.0.1` → `1.1.0` |
| Breaking product change | Increment `MAJOR`, reset `MINOR` and `PATCH` | `1.1.0` → `2.0.0` |

**Every new release must also increment `VERSION_CODE`** to a positive integer that is greater than every version code previously published for `com.aistudio.foxplayer.vqxlyt`. Never reuse or decrease it — Google Play rejects a build whose version code has already been used, and a lower code cannot update an installed build.

`VERSION_NAME` and `VERSION_CODE` are independent values. They are **not** derived from one another; bump both deliberately on each release.

---

## 🔄 CI/CD & Automated Workflows

The repository includes a hardened GitHub Actions workflow (`.github/workflows/android-build.yml`):
- **Automated Verification:** On every push and pull request to `main`, runs JDK 21 setup, executes all unit tests and verifies Compose screenshot tests against committed golden images (`:app:testDebugUnitTest :app:verifyRoborazziDebug`), runs Android Lint (`:app:lintDebug`), builds the Debug APK, and uploads `FoxPlayer-debug-apk`. Unit test reports, screenshot diff reports, and the lint HTML report are uploaded as artifacts to simplify diagnosing a broken build. The reference screenshots under `app/src/test/screenshots/` are declared as Gradle task inputs, so changing a golden invalidates the task and verification can never be silently skipped by the build cache.
- **Manual Release Packaging:** On manual trigger (`workflow_dispatch`), safely generates an isolated temporary test keystore in `runner.temp`, builds a minified Release APK, verifies signature via `apksigner verify --verbose`, and uploads `FoxPlayer-release-manual-test-apk` for direct on-device testing.

---

## 📄 License & Notes
- This application does not collect analytics or track personal user data. All video indexing and playback history remain strictly on-device.
