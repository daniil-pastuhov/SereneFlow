#!/bin/zsh
# Records an App Store app preview on a simulator: one clip per scene below, opened through the
# debug-only -demoScreen launch arguments with the demo routines (as scripts/ios_screenshots.sh
# does), joined with crossfades and encoded to App Store Connect's preview format.
#
#   scripts/ios_previews.sh store-listing/app-store/en-US/app-previews/1.mp4
#   DEVICE_NAME="iPad Pro 13-inch (M5)" scripts/ios_previews.sh \
#     store-listing/app-store/en-US/ipad-app-previews/1.mp4
#
# The simulator's orientation is kept: turn it first for a landscape preview. The size follows the
# device and orientation: 886x1920 / 1920x886 for an iPhone, 1200x1600 / 1600x1200 for an iPad.
# Simulator recordings have no sound, so the preview gets a silent stereo track (App Store Connect
# wants one). Needs ffmpeg.
set -euo pipefail

ROOT=${0:A:h:h}
OUT=${1:?usage: $0 <out.mp4>}
DEVICE_NAME=${DEVICE_NAME:-"iPhone 17 Pro Max"}
BUNDLE_ID=com.sereneflow.timer
DERIVED=$ROOT/build/ios-screenshots-derived-data
WORK=$ROOT/build/ios-preview-clips
FADE=0.5

# Scenes in order: view, routine id (- for none), seconds into the first step, theme, and how many
# seconds of it the preview shows.
SCENES=(
  "list      -                  0   ZEN_SAGE            3.5"
  "timer     morning_awakening  50  ZEN_SAGE            5"
  "timer     gentle_stretch     34  SUNSET_CLAY         5"
  "timer     deep_focus         38  MIDNIGHT_STARLIGHT  5"
  "timer     evening_wind_down  70  OCEAN_MIST          5"
  "timer     tea_break          70  SUNSET_CLAY         4.5"
)
# Seconds between launching the app and the scene being settled (splash, screen transition).
SETTLE=4

UDID=${SIM_UDID:-}
[[ -n $UDID ]] || UDID=$(xcrun simctl list devices available -j | python3 -c '
import json, sys
name = sys.argv[1]
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
mkdir -p "$SUPPORT" "$WORK" "${OUT:h}"
rm -f "$WORK"/*.mov(N)

# The speech service loads its voice on first use, which can stall the app; get it out of the way.
cp "$ROOT/scripts/demo/routines.json" "$SUPPORT/routines.json"
xcrun simctl launch "$UDID" "$BUNDLE_ID" -demoScreen timer -demoRoutine morning_awakening >/dev/null
sleep 20

INPUTS=()
FILTERS=()
N=0
for SCENE in "${SCENES[@]}"; do
  read -r VIEW ROUTINE ELAPSED THEME LENGTH <<< "$SCENE"
  xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
  cp "$ROOT/scripts/demo/routines.json" "$SUPPORT/routines.json"
  print -r -- "{\"themeMode\":\"$THEME\",\"backgroundModeAsked\":true}" > "$SUPPORT/preferences.json"
  CLIP=$WORK/$N.mov
  xcrun simctl io "$UDID" recordVideo --codec=h264 --force "$CLIP" >/dev/null 2>&1 &
  RECORDER=$!
  sleep 1
  xcrun simctl launch "$UDID" "$BUNDLE_ID" \
    -demoScreen "$VIEW" -demoRoutine "$ROUTINE" -demoElapsed "$ELAPSED" >/dev/null
  sleep $((SETTLE + LENGTH + 1))
  kill -INT $RECORDER
  wait $RECORDER || true
  echo "clip $N: $VIEW $ROUTINE"

  # Each clip starts once the scene has settled; consecutive clips overlap by FADE. The recorder
  # only writes a frame when the screen changes, so a still scene (the list) ends early: its last
  # frame is held for as long as needed.
  INPUTS+=(-i "$CLIP")
  FILTERS+=("[$N:v]fps=30,tpad=stop_mode=clone:stop_duration=$((SETTLE + LENGTH + 2)),trim=start=$((SETTLE + 1)):duration=$LENGTH,setpts=PTS-STARTPTS,settb=AVTB,format=yuv420p[v$N]")
  N=$((N + 1))
done
xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl status_bar "$UDID" clear

# The output size from the clips' orientation and the device family. A turned simulator records
# its portrait framebuffer with a rotation tag, which ffmpeg applies when decoding.
read -r WIDTH HEIGHT <<< "$(ffprobe -v error -select_streams v:0 -show_entries stream=width,height \
  -of default=nw=1:nk=1 "$WORK/0.mov" | tr '\n' ' ')"
ROTATION=$(ffprobe -v error -select_streams v:0 -show_entries stream_side_data=rotation \
  -of csv=p=0 "$WORK/0.mov" | tr -dc '0-9-')
(( ${ROTATION:-0} % 180 != 0 )) && read -r WIDTH HEIGHT <<< "$HEIGHT $WIDTH"
if [[ $DEVICE_NAME == iPad* ]]; then SIZE=(1200 1600); else SIZE=(886 1920); fi
(( WIDTH > HEIGHT )) && SIZE=(${SIZE[2]} ${SIZE[1]})

# Chain the crossfades: each one starts FADE before the end of everything joined so far.
LAST=v0
OFFSET=0
for ((i = 1; i < N; i++)); do
  # SCENES is 1-based, so SCENES[i] is clip i-1, the last one joined so far.
  read -r _ _ _ _ L <<< "${SCENES[$i]}"
  OFFSET=$(printf %.2f $((OFFSET + L - FADE)))
  FILTERS+=("[$LAST][v$i]xfade=transition=fade:duration=$FADE:offset=${OFFSET}[x$i]")
  LAST=x$i
done
FILTERS+=("[$LAST]scale=${SIZE[1]}:${SIZE[2]}:force_original_aspect_ratio=increase,crop=${SIZE[1]}:${SIZE[2]},setsar=1[out]")

ffmpeg -loglevel error -y "${INPUTS[@]}" -f lavfi -i anullsrc=channel_layout=stereo:sample_rate=48000 \
  -filter_complex "${(j:;:)FILTERS}" -map "[out]" -map ${N}:a -shortest \
  -c:v libx264 -profile:v high -level 4.0 -preset slow -crf 18 -pix_fmt yuv420p -r 30 \
  -c:a aac -b:a 256k -movflags +faststart "$OUT"
echo "Saved $OUT ($(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT")s, ${SIZE[1]}x${SIZE[2]})"
