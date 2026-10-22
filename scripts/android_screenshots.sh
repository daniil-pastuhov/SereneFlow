#!/bin/zsh
# Takes the raw Google Play screenshots: builds and installs the debug app on the connected
# device or emulator, loads the demo routines from scripts/demo/routines.json and captures every
# shot in scripts/demo/shots.txt.
#
#   scripts/android_screenshots.sh [output-dir]      # default: build/android-screenshots
#   ANDROID_SERIAL=emulator-5556 scripts/android_screenshots.sh   # pick a device
#
# Start a phone emulator first (e.g. a Pixel 9 Pro image). Screens are opened through the
# debug-only demoScreen / demoRoutine / demoElapsed intent extras (see DemoLaunch.kt), so
# release builds are unaffected; the routines and the theme are written into the app's files
# directory with run-as before each launch. scripts/frame_screenshots.py then puts them on Play's
# 1080x1920 canvas.
set -euo pipefail

ROOT=${0:A:h:h}
OUT=${1:-$ROOT/build/android-screenshots}
PACKAGE=com.sereneflow.timer

adb get-state >/dev/null || { echo "No device; start an emulator or set ANDROID_SERIAL" >&2; exit 1; }
"$ROOT/gradlew" -p "$ROOT" -q :androidApp:installDebug

adb shell cmd uimode night no >/dev/null
# System UI demo mode: a fixed clock and full bars and battery.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false

mkdir -p "$OUT"
rm -f "$OUT"/*.png(N)
adb shell run-as "$PACKAGE" mkdir -p files

N=1
# The list is read on fd 3: adb shell would swallow the loop's stdin.
while read -r -u 3 NAME VIEW ROUTINE ELAPSED THEME; do
  [[ -z $NAME ]] && continue
  adb shell am force-stop "$PACKAGE"
  adb shell run-as "$PACKAGE" sh -c "'cat > files/routines.json'" < "$ROOT/scripts/demo/routines.json"
  # backgroundModeAsked: no prompt about following the routine with the screen off.
  print -r -- "{\"themeMode\":\"$THEME\",\"backgroundModeAsked\":true}" |
    adb shell run-as "$PACKAGE" sh -c "'cat > files/preferences.json'"
  adb shell am start -W -n "$PACKAGE/.MainActivity" --es demoScreen "$VIEW" \
    --es demoRoutine "$ROUTINE" --ei demoElapsed "$ELAPSED" >/dev/null
  # Long enough for the screen transition, the particles and the text-to-speech engine to start.
  if [[ $VIEW == timer ]]; then sleep 10; else sleep 5; fi
  adb exec-out screencap -p > "$OUT/$N-$NAME.png"
  echo "$N-$NAME"
  N=$((N + 1))
done 3< <(grep -v '^#' "$ROOT/scripts/demo/shots.txt")

adb shell am force-stop "$PACKAGE"
demo exit
echo "Saved to $OUT"
