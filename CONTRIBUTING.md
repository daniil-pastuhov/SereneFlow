# Contributing to SereneFlow

Thanks for your interest! Bug reports, fixes and translations are welcome.

## Before you start

- **Bugs:** open an issue with the platform (Android or iOS), OS version, device and steps to
  reproduce. A screen recording helps with animation or layout problems.
- **Features:** open an issue to discuss the idea before writing code. SereneFlow is meant to stay
  small and calm, so some good ideas won't fit, and it is better to find that out before you spend
  time on a pull request.
- **Translations:** every user-visible string is a resource, so adding a language means adding
  files (see "Strings" in [CLAUDE.md](CLAUDE.md)). Open an issue first so work isn't duplicated.

## Building and testing

You need JDK 17 or 21 in `JAVA_HOME`, the Android SDK, and Xcode for iOS. See
[README.md](README.md#-building--running) for details.

```bash
./gradlew :composeApp:testAndroidHostTest             # unit tests (run these before every PR)
./gradlew :androidApp:assembleDebug                   # Android build
./gradlew :composeApp:compileKotlinIosSimulatorArm64  # shared code compiles for iOS
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination "generic/platform=iOS Simulator" build
```

Simulator builds need no Apple Developer account. If you can only test one platform, say which
one in the pull request.

## Conventions

[CLAUDE.md](CLAUDE.md) describes the architecture and the project's rules. The ones most often
missed:

- **Strings** go in resources (`composeResources/values/strings.xml` for shared code), never
  hard-coded. `StringResourcesTest` fails on unused or missing ones.
- **Dialogs and menus** in shared screens use `ConfirmDialog` and `ActionMenu` from
  `ui/platform/PlatformUi.kt`, not Material's, so iOS gets native alerts.
- **Layout** comes from the space a screen has (`BoxWithConstraints`), not from the device type or
  orientation. Check a narrow phone, a large phone and a wide window.
- **Insets:** the app is edge-to-edge, so screens apply `WindowInsets.safeDrawing` themselves.
- **Animations** are a pure function of frame time and size from their own canvas.
- **Xcode project:** edit `iosApp/project.yml` and run `xcodegen`; don't edit the pbxproj by hand.
- **App icons:** edit `tools/generate_icons.swift`, not the generated images.
- Add or update tests in `composeApp/src/commonTest` for engine, model and repository changes.

## Pull requests

- Keep each pull request to one change, with a description of what it does and why. Include
  screenshots for UI changes, in light and dark themes.
- Never commit signing keys, team IDs, API keys or anything from `~/.config/sereneflow/`. See
  `.gitignore` and `Secrets.xcconfig.example`.
- CI runs the tests and builds the platforms your pull request touches. Publishing runs only on
  release branches, never for pull requests.

## License

By contributing you agree that your contribution is licensed under the
[Apache License 2.0](LICENSE), as section 5 of that license describes. The SereneFlow name and
icons are not part of that license (see [NOTICE](NOTICE)).
