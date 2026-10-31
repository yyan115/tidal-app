#!/usr/bin/env bash
# Captures the README and store screenshots from the debug preview activity on a running emulator or device.
# Needs adb and ImageMagick.
# The preview activity shows fictional tracks and generated artwork, so the images contain no third-party content.
# Artwork comes from scripts/assets.
#
# Usage: scripts/capture-screenshots.sh
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
package=io.github.yyan115.opentune.debug
activity="$package/io.github.yyan115.opentune.preview.ThemePreviewActivity"
output="$root/fastlane/metadata/android/en-US/images/phoneScreenshots"
mkdir -p "$output"

(cd "$root" && ./gradlew :app:assembleDebug)
adb install -r "$root/app/build/outputs/apk/debug/app-debug.apk"

adb push "$root/scripts/assets/late-hours.jpg" /data/local/tmp/late-hours.jpg
adb push "$root/scripts/assets/salt-and-glass.jpg" /data/local/tmp/salt-and-glass.jpg
adb shell run-as "$package" mkdir -p files/theme-preview
adb shell run-as "$package" cp /data/local/tmp/late-hours.jpg files/theme-preview/late-hours.jpg
adb shell run-as "$package" cp /data/local/tmp/salt-and-glass.jpg files/theme-preview/salt-and-glass.jpg

capture() {
  adb shell am force-stop "$package"
  adb shell am start -n "$activity" --es page "$1" --es direction gallery --es mode dark
  sleep 5
  adb exec-out screencap -p > "$output/$2.png"
  # Replace the system status bar with the page background so the images show only the app.
  local background
  background=$(magick "$output/$2.png" -format '%[pixel:p{540,2}]' info:)
  magick "$output/$2.png" -fill "$background" -draw 'rectangle 0,0 1079,95' "$output/$2.png"
}
capture home 1
capture player 2

mkdir -p "$root/docs/screenshots"
cp "$output/1.png" "$root/docs/screenshots/home.png"
cp "$output/2.png" "$root/docs/screenshots/player.png"
echo "Screenshots written to $output and docs/screenshots"
