#!/bin/zsh
# Takes the raw App Store screenshots: builds the debug app, loads the demo routines from
# scripts/demo/routines.json and captures every shot in scripts/demo/shots.txt on a simulator.
#
#   scripts/ios_screenshots.sh [output-dir]      # default: build/ios-screenshots
#   DEVICE_NAME="iPad Pro 13-inch (M5)" scripts/ios_screenshots.sh build/raw-ipad
#   DEVICE_NAME="iPhone Duo" SCREEN=1 scripts/ios_screenshots.sh build/raw-duo-outer   # folded
#
# The default iPhone 17 Pro Max captures 1320x2868, the 6.9" size App Store Connect requires.
# Screens are opened through the debug-only -demoScreen / -demoRoutine / -demoElapsed launch
# arguments (see DemoLaunch.kt), so release builds are unaffected; the routines and the theme are
# written straight into the app's Application Support folder before each launch.
set -euo pipefail

ROOT=${0:A:h:h}
OUT=${1:-$ROOT/build/ios-screenshots}
DEVICE_NAME=${DEVICE_NAME:-"iPhone 17 Pro Max"}
# Which of the simulator's displays to capture. iPhone Duo has two: the default is whichever is
# on, so unfold it in Device Hub for the inner display; folded, its outer display is SCREEN=1.
SCREEN=${SCREEN:-}
BUNDLE_ID=com.sereneflow.timer
DERIVED=$ROOT/build/ios-screenshots-derived-data

# SIM_UDID picks one simulator when several share a name (e.g. a folded and an unfolded Duo).
UDID=${SIM_UDID:-}
[[ -n $UDID ]] || UDID=$(xcrun simctl list devices available -j | python3 -c '
import json, sys
name = sys.argv[1]
# The newest iOS runtime that has the device, e.g. ...SimRuntime.iOS-27-1.
runtimes = json.load(sys.stdin)["devices"]
version = lambda runtime: [int(n) for n in runtime.rsplit("iOS-", 1)[1].split("-")]
for runtime in sorted((r for r in runtimes if "iOS-" in r), key=version, reverse=True):
    for d in runtimes[runtime]:
        if d["name"] == name:
            print(d["udid"]); sys.exit()
sys.exit("no available simulator named " + name)' "$DEVICE_NAME")

xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b >/dev/null
xcrun simctl ui "$UDID" appearance light
xcrun simctl status_bar "$UDID" override --time 9:41 --dataNetwork wifi --wifiMode active \
  --wifiBars 3 --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100

xcodebuild -quiet -project "$ROOT/iosApp/iosApp.xcodeproj" -scheme iosApp \
  -configuration Debug -destination "id=$UDID" -derivedDataPath "$DERIVED" build
xcrun simctl install "$UDID" "$DERIVED/Build/Products/Debug-iphonesimulator/SereneFlow.app"
SUPPORT="$(xcrun simctl get_app_container "$UDID" "$BUNDLE_ID" data)/Library/Application Support"
mkdir -p "$SUPPORT" "$OUT"
rm -f "$OUT"/*.png(N)

# The simulator's speech service loads its voice on first use, which can hold up the app's main
# thread for many seconds; one throwaway launch of a voice-guided routine gets that out of the way.
cp "$ROOT/scripts/demo/routines.json" "$SUPPORT/routines.json"
xcrun simctl launch "$UDID" "$BUNDLE_ID" -demoScreen timer -demoRoutine morning_awakening >/dev/null
sleep 20

N=1
grep -v '^#' "$ROOT/scripts/demo/shots.txt" | while read -r NAME VIEW ROUTINE ELAPSED THEME; do
  [[ -z $NAME ]] && continue
  xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
  cp "$ROOT/scripts/demo/routines.json" "$SUPPORT/routines.json"
  # backgroundModeAsked: no prompt about following the routine with the screen off.
  print -r -- "{\"themeMode\":\"$THEME\",\"backgroundModeAsked\":true}" > "$SUPPORT/preferences.json"
  xcrun simctl launch "$UDID" "$BUNDLE_ID" \
    -demoScreen "$VIEW" -demoRoutine "$ROUTINE" -demoElapsed "$ELAPSED" >/dev/null
  # Long enough for the screen transition, the first spoken cue and the particles to fill the scene.
  if [[ $VIEW == timer ]]; then sleep 10; else sleep 6; fi
  xcrun simctl io "$UDID" screenshot ${SCREEN:+--display="$SCREEN"} "$OUT/$N-$NAME.png" >/dev/null 2>&1
  echo "$N-$NAME"
  N=$((N + 1))
done

xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl status_bar "$UDID" clear
echo "Saved to $OUT"
