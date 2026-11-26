#!/bin/bash
# Takes new fixtures from the real TIDAL service, for the tests of the core module and for the fake TIDAL of the tests of the app.
# Needs a device or emulator with the debug app signed in (see LiveContractTest), and the apks built:
#   ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
# Run it from the root of the repository. It writes core/src/test/resources/fixtures/live/*.json with the text made up (scripts/sanitize_fixture.py).
set -euo pipefail

package=io.github.yyan115.opentune.debug
raw=$(mktemp -d)
trap 'rm -rf "$raw"' EXIT

adb install -r -g app/build/outputs/apk/debug/app-debug.apk > /dev/null
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk > /dev/null
adb shell am instrument -w -e liveAccount true -e captureFixtures true -e class io.github.yyan115.opentune.LiveContractTest#theAnswersOfTidalHaveTheShapeOfTheFixtures \
  "$package.test/androidx.test.runner.AndroidJUnitRunner" | tail -3 || true
adb pull "/sdcard/Android/data/$package/files/capture/." "$raw" > /dev/null

for name in artist album home search favorites; do
  python3 scripts/sanitize_fixture.py "$raw/$name.json" "core/src/test/resources/fixtures/live/$name.json" --items 2
done
echo "The fixtures are new. Run the tests of the core module and of the app, and read the diff."
