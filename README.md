# SereneFlow 🌸
### Mindful Routine & Animated Visual Timer (Compose Multiplatform)

**SereneFlow** is an elegant, tranquil routine and timer app built with **Compose Multiplatform (CMP)** targeting **Android** and **iOS**. 

Designed as a modern, calming alternative to cluttered and rigid timer apps, SereneFlow transforms everyday habits and meditation routines into a relaxing, aesthetically harmonized experience featuring real-time generative animations and soothing audio reminders.

---

## ✨ Key Features

### 🌊 Animated Visual Timers
Choose from 5 distinct visual effects rendered dynamically with Compose Canvas:
1. **Serene Water**: Fluid liquid simulation with multi-harmonic sine waves, surface ripples, and buoyant rising bubbles with Stokes drag & Brownian wobble.
2. **Living Bonsai**: Procedural branching tree with natural harmonic wind sway physics, blooming cherry blossom clusters, and falling petals drifting with 2D gravity.
3. **Celestial Orbit**: Multi-tier orbital mechanics obeying Kepler's laws, drifting stardust particles, and a radiant pulsating celestial core.
4. **Breathing Lotus**: Sacred geometric petals unfolding and contracting to a meditative 4-4-6-2 pranayama breathing rhythm.
5. **Zen Hourglass**: Flowing ballistic stream of glowing sand grains with bounce scatter and dynamic accumulation mound physics.

### 🔔 Mindful Audio & Spoken Guidance
- **Step sounds**: Real-time synthesized tones — Tibetan Bowl (432 Hz fundamental with overtone shimmer and warm exponential decay), Zen Chime, Crystal Bowl, Temple Gong, Kalimba, Wood Block and Koshi Chimes — or no sound.
- **Zen Chimes**: Soft bell notification on milestones and countdowns.
- **Voice Guidance**: Clear, soothing voice announcements powered by native platform Text-to-Speech (`android.speech.tts.TextToSpeech` on Android, `AVSpeechSynthesizer` on iOS).
- **Per-routine choices**: Each routine picks its sound, voice guidance (on, off) and visual effect, or leaves them at *Default* to follow Settings when it starts.

### 🎨 Harmonized Color Themes
Four curated palettes designed for tranquility and visual peace:
- 🌿 **Zen Sage**: Earthy matcha green, soft eucalyptus, warm stone, pale mist.
- 🌊 **Ocean Mist**: Deep indigo ocean, tranquil navy, aurora teal, crystalline foam.
- 🌅 **Sunset Clay**: Rich terracotta, soft apricot, desert sand, warm espresso.
- 🌌 **Midnight Starlight**: Cosmic obsidian, celestial lavender, radiant aqua starlight.

### ⏱️ Routine Flow & Management
- **Routine Gallery**: Beautiful cards displaying routine durations, step tags, mini-timeline previews, and quick play triggers.
- **Interactive Timeline Editor**: Add, delete, and reorder steps with smooth timeline connectors and duration steppers (-1m, -15s, +15s, +1m).
- **Routine Duplication**: Duplicate and customize routines with a single tap.
- **Starter Routine**: *Morning Awakening* (15 min), shown on first launch.
- **Adaptive Layout**: a routine grid and centred forms on iPad, iPhone Duo and tablets; the timer
  puts its controls beside the dial in landscape.

---

## 🛠️ Project Architecture

```
Timer/
├── composeApp/                 # Shared Kotlin Multiplatform Compose UI & Logic
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/sereneflow/timer/
│       │   ├── model/          # Routine, RoutineStep, TimerState, Enums
│       │   ├── theme/          # Harmonized Palettes, Material 3 Theme, Typography
│       │   ├── physics/        # Water, Bonsai, Orbit, Lotus, Hourglass Canvases
│       │   ├── audio/          # AudioController interface & tone synthesizers
│       │   ├── repository/     # RoutineRepository, PreferencesRepository
│       │   ├── engine/         # TimerEngine (state machine, ticker, announcements)
│       │   ├── ui/             # RoutineListScreen, RoutineEditorScreen, ActiveTimerScreen, SettingsScreen
│       │   └── App.kt          # Root navigation and theme host
│       ├── androidMain/        # Android AudioTrack & TTS implementation
│       ├── iosMain/            # iOS AVSpeechSynthesizer & AudioServices implementation
│       └── commonTest/         # Unit tests for TimerEngine and Repositories
├── androidApp/                 # Android native application launcher
│   └── src/main/
│       └── AndroidManifest.xml
│       └── kotlin/com/sereneflow/timer/SereneFlowActivity.kt
└── iosApp/                     # Native iOS SwiftUI project embedding ComposeApp.framework
    ├── iosApp/
    │   ├── iosApp.swift        # SwiftUI @main entry point
    │   ├── ContentView.swift   # UIViewControllerRepresentable bridging Compose
    │   └── Info.plist
    ├── project.yml             # XcodeGen configuration
    └── iosApp.xcodeproj        # Generated Xcode project
```

---

## 🚀 Building & Running

### Prerequisites
- JDK 17 or 21 (configured in `JAVA_HOME`)
- Android SDK (API 35-37)
- Xcode (for iOS builds and simulator testing)

### Android
```bash
./gradlew :androidApp:assembleDebug
```
The resulting APK is located at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

In Android Studio, run the shared **androidApp** configuration. It starts `SereneFlowActivity`
directly, because the launcher entry Android Studio picks by default (`.MainActivity`) is turned off
whenever a theme other than Zen Sage is chosen: the app icon follows the theme.

### iOS
To build for a device or archive, give Xcode your Apple Developer team: copy
`Secrets.xcconfig.example` to `Secrets.xcconfig` (git-ignored) and set `TEAM_ID` in it.
`Version.xcconfig` includes it, so every target signs with that team. Simulator builds are ad-hoc
signed and work without it.

1. Build the shared framework:
   ```bash
   ./gradlew :composeApp:compileKotlinIosSimulatorArm64
   ```
2. Build with Xcode or command line:
   ```bash
   xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "generic/platform=iOS Simulator" build
   ```
   Leave out `-sdk iphonesimulator`: it would build the Apple Watch app against the iOS SDK too,
   which fails. `-destination` picks the right SDK for each target.
3. Or open `iosApp/iosApp.xcodeproj` in Xcode and hit **Run** on any iPhone simulator!
4. Or, in Android Studio (with the Kotlin Multiplatform plugin), run an Xcode Application
   configuration with project and scheme `iosApp`, configuration Debug, on an iPhone simulator.

### Running Unit Tests
```bash
./gradlew :composeApp:testAndroidHostTest
```

---

## 🏪 Releasing

Merging to `main` publishes nothing; CI (`.github/workflows/ci.yml`) only tests and builds it. A
release goes out in steps:

1. **Start it**: `scripts/release` (add `--dry-run` to try it without changing anything). It lists
   the commits on `origin/main` since the last `vX.Y.Z` tag and asks for a patch, minor or major
   bump. Then it has the Claude CLI write store release notes (en-US) for you to accept, edit or
   regenerate. It creates `release/vX.Y.Z` with the version bump in `Version.xcconfig` and
   `release-notes/vX.Y.Z/en-US.txt`, pushes it and opens a PR.
2. **Internal testing**: CI publishes every push to `release/v*` to the Google Play internal track
   and to the internal TestFlight testers, with those notes. Fixes for the release go on the
   branch; each push becomes a new internal build.
3. **Play closed testing and the App Store**: merging the PR promotes the newest internal Play
   build of that version to closed testing (`alpha`). It also puts the newest TestFlight build of
   the version on its App Store version, with the notes as "What's New", and submits it for App
   Review, along with any in-app purchases that are ready to submit; it goes live as soon as Apple approves it (set the repository variable
   `APP_STORE_RELEASE_TYPE` to `MANUAL` to release it from App Store Connect yourself). Then it
   tags `vX.Y.Z` and creates a GitHub release.

**First release (1.0.0)**: there is no tag to count from yet, so tag the commit before the release
work as `v0.0.0` (`git tag v0.0.0 <commit> && git push origin v0.0.0`), then run `scripts/release`
and choose **keep 1.0.0**. Play takes only drafts before its first review, so the repository
variable `PLAY_RELEASE_STATUS` is `draft` until then (see `store-listing/google-play/README.md`).

**Version**: `Version.xcconfig` holds the one version of both apps. Gradle reads it for Android's
`versionName`, and it is the Xcode project's base configuration, so the app and the widget get it
as `MARKETING_VERSION`. CI sets Android's `versionCode` (`-PversionCode`) and the iOS build number
(a UTC timestamp).

**Secrets**: the repository's Actions secrets hold the Android upload key (`ANDROID_*`), the Play
service account (`PLAY_SERVICE_ACCOUNT_JSON`), the App Store Connect API key (`ASC_*`) and the Apple
team ID (`DEVELOPMENT_TEAM`), which the TestFlight job writes into `Secrets.xcconfig`. Local copies
live outside the repo in `~/.config/sereneflow/`; `source ~/.config/sereneflow/store.env` before
running `scripts/ios_release.sh`, `scripts/testflight.py` or `scripts/play_upload.py` by hand.
`scripts/ios_release.sh` also needs `Secrets.xcconfig` at the repository root (see iOS above) and
stops with an error without it.

### Release signing

`androidApp/build.gradle.kts` signs release builds when `androidApp/keystore.properties`
(git-ignored) exists:

```properties
storeFile=/Users/<you>/.config/sereneflow/upload-keystore.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Without it, `assembleRelease` builds an unsigned (still minified) APK.

### Store listing

`store-listing/app-store/` and `store-listing/google-play/` hold the texts, screenshots and art,
with a README each listing every console field to fill in. The privacy policy and support page are
in `store-listing/privacy-site/`, published from the public repository
[sereneflow-privacy](https://github.com/daniil-pastuhov/sereneflow-privacy) at
https://daniil-pastuhov.github.io/sereneflow-privacy/.

Screenshots come from the running debug app: `scripts/ios_screenshots.sh` and
`scripts/android_screenshots.sh` open each screen in `scripts/demo/shots.txt` with the demo
routines in `scripts/demo/routines.json` (through debug-only launch arguments, see `DemoLaunch.kt`),
`scripts/frame_screenshots.py` adds captions and a device frame, and `scripts/store_art.py` makes
the Play feature graphic and the App Store header images.
