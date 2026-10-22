# App Store submission kit

Everything App Store Connect asks for, ready to paste. One folder per listing language; the app is
English-only, so there is just `en-US/` (English (U.S.)), with `name.txt`, `subtitle.txt`,
`promotional_text.txt`, `keywords.txt`, `description.txt` and these screenshot sets, in App Store
Connect's media sections of the same name:

| Folder | Size | App Store Connect display |
|---|---|---|
| `screenshots/` | 1320×2868 | iPhone with Dynamic Island (large display), 6.9" |
| `duo-inner-screenshots/` | 2007×2853 | iPhone Duo, inner display |
| `duo-outer-screenshots/` | 1398×2034 | iPhone Duo, outer display |
| `ipad-screenshots/` | 2064×2752 | iPad 13" |

iPhone Duo screenshots put an "optimized for iPhone Duo" badge on the product page, and from
April 2027 they are required for apps built with the iOS 27.1 SDK. The App Store Connect API has
no documented display type for them yet, so they are uploaded by hand.

Retake them after UI changes: `scripts/ios_screenshots.sh <raw-dir>` captures every line of
`scripts/demo/shots.txt` with the demo routines in `scripts/demo/routines.json`, then
`scripts/frame_screenshots.py <raw-dir> store-listing/app-store/en-US/<folder>` adds the captions
(in that script) and the device frame. Pick the simulator with `DEVICE_NAME`:

- `screenshots/`: the default, iPhone 17 Pro Max.
- `duo-inner-screenshots/`: `DEVICE_NAME="iPhone Duo"`, unfolded and upright. Xcode 27 has no
  Simulator.app: set the pose in Device Hub (Xcode → Open Developer Tool), as simctl can't.
- `duo-outer-screenshots/`: `DEVICE_NAME="iPhone Duo" SCREEN=1`, folded.
- `ipad-screenshots/`: `DEVICE_NAME="iPad Pro 13-inch (M5)"`.

`en-US/` also has the two images for the version page's "Header and Search Results" section, which
the App Store shows on iOS 27 and iPadOS 27 and later. They are optional; without them, search
results show the screenshots. `scripts/store_art.py --ios <iphone-raw-dir>` makes them (the
headline lives in that script), keeping key artwork inside the safe areas of Apple's templates.

| File | Size | Placement |
|---|---|---|
| `header.png` | 3840×1646 (21:9) | Product page header: three timers, no text |
| `search-result.png` | 3840×2560 (3:2) | Search results: a headline and the lotus timer |

## 0. Filling it in through the API

`scripts/appstore_listing.py --version X.Y.Z --review-contact-from com.foodbest.ios` (after
`source ~/.config/sereneflow/store.env`) sets everything below that has an API: name, subtitle,
privacy policy, categories, content rights, age rating, availability and price, the version's
texts and copyright, the App Review contact (copied from BestBefore's) and notes, and the iPhone
and iPad screenshots. `scripts/testflight.py appstore` then attaches the build and submits.

Only these are done by hand in App Store Connect, as they have no API:
- **App Privacy** → Get Started → "No, we do not collect data from this app" → Publish.
- **App Information** → the regulated medical device declaration: **No**.
- The iPhone Duo screenshot sets and the Header and Search Results images (drag in from `en-US/`).

## 1. One-time setup (done)

- App record "SereneFlow" (id 6825780663), bundle IDs `com.sereneflow.timer` and
  `com.sereneflow.timer.widget` (Live Activity), team `S8Q8KHX6G5`.
- API key `26GZL2YVV9`, role Admin (automatic cloud signing needs Admin). The key, its issuer ID
  and the Android upload key are in `~/.config/sereneflow/` (`source store.env`) and in the
  repository's Actions secrets.
- TestFlight internal group "Internal" with access to all builds; the account holder is a tester.
- The primary language is English (U.S.); the record was created with English (U.K.), switched
  through the API before the first submission.

## 2. Uploading a build

From this Mac:

```sh
source ~/.config/sereneflow/store.env && scripts/ios_release.sh
```

From CI, on every push to a `release/v*` branch (see "Releasing" in the top-level README.md); the
`ASC_*` secrets and `IOS_PUBLISH=true` are set.

## 3. App information

| Field | Value |
|---|---|
| Name | `SereneFlow: Routine Timer` |
| Privacy Policy URL | https://daniil-pastuhov.github.io/sereneflow-privacy/ |
| Support URL | https://daniil-pastuhov.github.io/sereneflow-privacy/support.html |
| Marketing URL | leave empty |
| Category | Primary: **Health & Fitness**. Secondary: **Productivity** |
| Copyright | `2026 Daniil Pastuhov` |
| Content rights | Does not contain, show or access third-party content |
| Price | Free. Availability: all countries |
| Export compliance | Already answered in Info.plist (`ITSAppUsesNonExemptEncryption = NO`) |

## 4. Age rating

Answer **None / No** to every question. The result is **4+**.

## 5. App Privacy ("nutrition label")

- **Data collection: "No, we do not collect data from this app."** SereneFlow has no internet
  access, no account, analytics, ads or crash reporting; routines stay on the device.
- **Tracking: No.**
- `iosApp/iosApp/PrivacyInfo.xcprivacy` says the same, and declares the required-reason APIs the
  Kotlin/Native runtime links (file timestamps, system boot time, disk space).

## 6. App Review information

- Sign-in required: **No**.
- Contact: your name, phone and email (not shown publicly).
- Notes (paste as is):

```
SereneFlow has no account or login, and no network access; everything stays on the device.

To try it: tap "Begin Routine" on "Morning Awakening". Each step counts down with an animated
visual timer, and a voice announces the steps (turn the volume up). Use "New Routine" to build one,
and the gear button for themes, default sounds and reminders.

Background audio: with "Follow routine with screen off" on (Settings, or the prompt after saving a
routine), the app keeps its audio session active while a routine runs, so spoken step cues and
chimes play on time with the screen locked. A Live Activity shows the current step and time left.
The audio session is released when the routine is paused or ends.
```

## 7. Version page

- Version: 1.0.0 (`scripts/testflight.py appstore` renames the prepared "1.0" version).
- What's New (first version): `First release.` (`release-notes/v1.0.0/en-US.txt`).
- Screenshots: drag the sets from section 1 into their displays.
