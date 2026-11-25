#!/usr/bin/env bash
# Runs the device tests of the app in several runs, and each run starts the app process again.
# The tests share one app process. On the small emulator of the CI the app has 16 MB of heap, and what the tests leave behind adds up until a
# test runs out of memory. A run for each shard of the tests starts with an empty process.
# Usage: scripts/run_device_tests.sh API [SHARDS], two shards when the number is left out
#
# The checks of a pull request give each Android version a third of the rows of the screen matrix, and the three versions together run all of them.
# A nightly run sets FULL_MATRIX=1, and then every Android version runs every row, the tour of the screens and the crawler.
# SCREEN, such as 1080x2400@420, sets the size and the density of the display before the tests start.
set -u
api="$1"; shards="${2:-2}"
extra=()
stride=3
case "$api" in
  36) extra+=(-Pandroid.testInstrumentationRunnerArguments.screenTour=true); offset=2 ;;
  33) extra+=(-Pandroid.testInstrumentationRunnerArguments.crawl=true); offset=1 ;;
  *) offset=0 ;;
esac
if [ "${FULL_MATRIX:-}" = "1" ]; then
  extra=(-Pandroid.testInstrumentationRunnerArguments.screenTour=true -Pandroid.testInstrumentationRunnerArguments.crawl=true)
  stride=1; offset=0
fi
if [ -n "${SCREEN:-}" ]; then
  adb shell wm size "${SCREEN%@*}"
  adb shell wm density "${SCREEN#*@}"
fi

# Maven Central refuses a download now and then, with the status 429, when many runs of the CI ask at the same time. Gradle shows a refused plugin
# download as a plugin that was not found, without the status. The tests have not started when that happens, so Gradle is started again, up to four
# times, with a wait that grows. Any other failure, such as a failed test, ends the attempt.
run_gradle() {
  local attempt code log
  log="$(mktemp)"
  for attempt in 1 2 3 4; do
    ./gradlew "$@" 2>&1 | tee "$log"
    code=${PIPESTATUS[0]}
    if [ "$code" -eq 0 ]; then rm -f "$log"; return 0; fi
    if [ "$attempt" -eq 4 ] || ! grep -qE "status code 429|was not found in any of the following sources" "$log"; then rm -f "$log"; return "$code"; fi
    echo "A download was refused (attempt $attempt of 4). Waiting before the next attempt."
    sleep $(( ${RETRY_SECONDS:-60} * attempt ))
  done
}

status=0
for ((shard = 0; shard < shards; shard++)); do
  run_gradle --no-daemon :app:connectedDebugAndroidTest "${extra[@]}" \
    -Pandroid.testInstrumentationRunnerArguments.matrixStride="$stride" \
    -Pandroid.testInstrumentationRunnerArguments.matrixOffset="$offset" \
    -Pandroid.testInstrumentationRunnerArguments.numShards="$shards" \
    -Pandroid.testInstrumentationRunnerArguments.shardIndex="$shard" || status=1
  # The next run replaces the reports, so each run keeps its own.
  mkdir -p "shard-results/$shard"
  cp -r app/build/reports/androidTests app/build/outputs/androidTest-results "shard-results/$shard/" 2>/dev/null || true
done
exit "$status"
