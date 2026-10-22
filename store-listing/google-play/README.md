# Google Play submission kit

Everything Play Console asks for, ready to paste. The app is English-only, so the listing is
`en-US/` alone:

| File | Play Console field | Limit |
|---|---|---|
| `en-US/title.txt` | App name | 30 |
| `en-US/short-description.txt` | Short description | 80 |
| `en-US/full-description.txt` | Full description (`<b>` is allowed) | 4000 |
| `icon-512.png` | App icon, 512×512 (made by `tools/generate_icons.swift`) | |
| `en-US/feature-graphic.png` | Feature graphic, 1024×500 (`scripts/store_art.py --android`) | |
| `en-US/phone-screenshots/` | Phone screenshots, 1080×1920 | 2–8 |
| `en-US/tablet-screenshots/` | 10-inch tablet screenshots, 2560×1440 | up to 8 |

Retake the screenshots after UI changes, with a phone emulator running:

```sh
scripts/android_screenshots.sh build/raw-android
scripts/frame_screenshots.py build/raw-android store-listing/google-play/en-US/phone-screenshots --size 1080x1920

# Tablet: the same emulator at a 10" tablet's size.
adb shell wm size 2560x1600 && adb shell wm density 320
scripts/android_screenshots.sh build/raw-android-tablet
adb shell wm size reset && adb shell wm density reset
scripts/frame_screenshots.py build/raw-android-tablet store-listing/google-play/en-US/tablet-screenshots --size 2560x1440
```

## 1. One-time setup

- App `com.sereneflow.timer` exists in Play Console; the service account
  `bestbeforepublishing@bestbefore-510415.iam.gserviceaccount.com` can open edits on it. Check under
  Users and permissions that it may **release to testing tracks** for SereneFlow.
- **App signing:** Play App Signing holds the app signing key. Uploads are signed with the upload
  key in `~/.config/sereneflow/upload-keystore.jks` (CI: the `ANDROID_*` secrets). Back it up.
- **First release:** Play only accepts releases with status `draft` until the app's first review,
  so the repository variable `PLAY_RELEASE_STATUS` is `draft`. CI uploads the first bundle as a
  draft on the internal track; roll it out in Play Console, then delete the variable (or set it to
  `completed`) so later builds reach testers by themselves.

`scripts/play_listing.py --contact-from com.foodtracker.android` publishes the texts, icon,
feature graphic, screenshots and contact details below (the email is copied from BestBefore's
Play details); add `--validate-only` for a dry run.

## 2. Store settings

| Field | Value |
|---|---|
| App category | **Health & Fitness** |
| Tags | Meditation, Productivity, Timer |
| Email | your contact email (required by Play, shown publicly) |
| Website | https://daniil-pastuhov.github.io/sereneflow-privacy/support.html |
| Privacy policy | https://daniil-pastuhov.github.io/sereneflow-privacy/ |

## 3. App content

- **Data safety:** "Does your app collect or share any of the required user data types?" **No.**
  The app has no INTERNET permission; routines and settings stay in the app's files.
  Encryption in transit: not applicable. Deletion: uninstalling deletes all data.
- **Ads:** No ads.
- **App access:** All functionality is available without special access.
- **Content rating (IARC):** category "Reference, News, or Educational" or "All Other App Types";
  answer No to every question → Everyone / PEGI 3.
- **Target audience:** 18 and over (or 13+); not designed for children.
- **Government apps, financial features, health:** No. (It is not a medical app.)
- **Foreground service declaration** (Policy → App content → Foreground service permissions):
  - Permission: `FOREGROUND_SERVICE_SPECIAL_USE`.
  - Description: *SereneFlow runs a routine the user starts: a sequence of timed steps with spoken
    step announcements and chimes. When the user turns on "Follow routine with screen off", a
    foreground service keeps the timer and its audio cues running with the screen off and shows
    the current step and time left in an ongoing notification with Pause, Skip and End. It stops
    when the routine ends or the user ends it.*
  - User impact if deferred or interrupted: *Step announcements and chimes would play late or not
    at all while the screen is off, so the user would lose track of their routine.*
  - Video: record the emulator (`adb shell screenrecord /sdcard/fgs.mp4`) while starting a routine
    with the setting on, locking the screen and showing the notification; upload it unlisted.
- **Notifications:** the app asks for `POST_NOTIFICATIONS` only after the user opts in to following
  the routine with the screen off.

## 4. Release

`scripts/release` starts a release; CI uploads it to the internal track with the notes in
`release-notes/vX.Y.Z/en-US.txt` and promotes it to closed testing when the release PR is merged
(see "Releasing" in the top-level README.md). Production rollout is done in Play Console. A new
personal developer account must run a closed test with at least 12 testers for 14 days before it
can apply for production access.
