# SereneFlow 🌸
### Mindful Routine & Physics-Based Visual Timer (Compose Multiplatform)

**SereneFlow** is an elegant, tranquil routine and timer app built with **Compose Multiplatform (CMP)** targeting **Android** and **iOS**. 

Designed as a modern, calming alternative to cluttered and rigid timer apps, SereneFlow transforms everyday habits and meditation routines into a relaxing, aesthetically harmonized experience featuring real-time physics-based generative animations and soothing audio reminders.

---

## ✨ Key Features

### 🌊 Physics-Based Animated Timers
Choose from 5 distinct visual simulations rendered dynamically with Compose Canvas and mathematical physics:
1. **Serene Water**: Fluid liquid simulation with multi-harmonic sine waves, surface ripples, and buoyant rising bubbles with Stokes drag & Brownian wobble.
2. **Living Bonsai**: Procedural branching tree with natural harmonic wind sway physics, blooming cherry blossom clusters, and falling petals drifting with 2D gravity.
3. **Celestial Orbit**: Multi-tier orbital mechanics obeying Kepler's laws, drifting stardust particles, and a radiant pulsating celestial core.
4. **Breathing Lotus**: Sacred geometric petals unfolding and contracting to a meditative 4-4-6-2 pranayama breathing rhythm.
5. **Zen Hourglass**: Flowing ballistic stream of glowing sand grains with bounce scatter and dynamic accumulation mound physics.

### 🔔 Mindful Audio & Spoken Guidance
- **Tibetan Singing Bowl**: Real-time synthesized harmonic bell sound (432 Hz fundamental with overtone shimmer and warm exponential decay).
- **Zen Chimes**: Soft wooden bell notification on milestones and countdowns.
- **Voice Guidance**: Clear, soothing voice announcements powered by native platform Text-to-Speech (`android.speech.tts.TextToSpeech` on Android, `AVSpeechSynthesizer` on iOS).
- **Settings Choice**: Toggle between *Voice & Singing Bowl*, *Voice Only*, *Tibetan Bowl Only*, *Zen Chime*, or *Silent (Visual Only)*.

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
- **Bundled Mindful Presets**:
  - *Morning Awakening* (15 min)
  - *Deep Focus Sprint* (25 min)
  - *Mindful Tea & Breath* (8 min)
  - *Evening Wind-Down* (20 min)

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
│       └── kotlin/com/sereneflow/timer/MainActivity.kt
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

### iOS
1. Build the shared framework:
   ```bash
   ./gradlew :composeApp:compileKotlinIosSimulatorArm64
   ```
2. Build with Xcode or command line:
   ```bash
   xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination "generic/platform=iOS Simulator" build CODE_SIGNING_ALLOWED=NO
   ```
3. Or open `iosApp/iosApp.xcodeproj` in Xcode and hit **Run** on any iPhone simulator!

### Running Unit Tests
```bash
./gradlew :composeApp:testAndroidHostTest
```
