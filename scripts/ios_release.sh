#!/bin/zsh
# Archives the iOS app, signs it for the App Store and uploads it to App Store Connect, where it
# shows up in TestFlight a few minutes later. Used by .github/workflows/ci.yml and runnable by hand:
#
#   ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_PATH=~/.config/sereneflow/AuthKey_XXXX.p8 \
#     scripts/ios_release.sh
#
# Signing is automatic: with the App Store Connect API key, xcodebuild creates or fetches the
# distribution certificate and profiles (app and Live Activity widget) itself, so no Xcode
# account login is needed (on CI too). The key needs the Admin role: Apple only lets Admin keys use
# cloud-managed distribution certificates, and an App Manager key fails with "Cloud signing
# permission error".
#
# The signing team comes from Secrets.xcconfig at the repository root (git-ignored; copy
# Secrets.xcconfig.example). CI writes it from the DEVELOPMENT_TEAM Actions secret.
#
# BUILD_NUMBER defaults to the current UTC time (yyyymmddHHMM): App Store Connect wants every
# upload of a version to have a higher build number, and a timestamp always is, whether the
# build comes from CI or from this Mac.
set -euo pipefail

ROOT=${0:A:h:h}
: "${ASC_KEY_ID:?set ASC_KEY_ID to the App Store Connect API key ID}"
: "${ASC_ISSUER_ID:?set ASC_ISSUER_ID to the App Store Connect issuer ID}"
: "${ASC_KEY_PATH:?set ASC_KEY_PATH to the .p8 key file}"
if ! grep -Eq '^TEAM_ID *= *[A-Z0-9]{10} *$' "$ROOT/Secrets.xcconfig" 2>/dev/null; then
  echo "error: $ROOT/Secrets.xcconfig is missing or has no TEAM_ID. Copy Secrets.xcconfig.example" \
    "to Secrets.xcconfig and set TEAM_ID to your Apple Developer team ID." >&2
  exit 1
fi
BUILD_NUMBER=${BUILD_NUMBER:-$(date -u +%Y%m%d%H%M)}
OUT=$ROOT/build/ios-release
AUTH=(-allowProvisioningUpdates
      -authenticationKeyPath "$ASC_KEY_PATH"
      -authenticationKeyID "$ASC_KEY_ID"
      -authenticationKeyIssuerID "$ASC_ISSUER_ID")

rm -rf "$OUT"
echo "Archiving build $BUILD_NUMBER"
# The project's pre-build script compiles the Kotlin framework (release, iosArm64) through Gradle.
xcodebuild -quiet -project "$ROOT/iosApp/iosApp.xcodeproj" -scheme iosApp \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath "$OUT/SereneFlow.xcarchive" \
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER" "${AUTH[@]}" archive

echo "Uploading to App Store Connect"
xcodebuild -quiet -exportArchive -archivePath "$OUT/SereneFlow.xcarchive" \
  -exportOptionsPlist "$ROOT/iosApp/ExportOptions.plist" -exportPath "$OUT/export" "${AUTH[@]}"
echo "Uploaded build $BUILD_NUMBER; it appears in TestFlight once App Store Connect has processed it."
