#!/usr/bin/env bash
# Runs the device tests of the app in several runs, and each run starts the app process again.
# The tests share one app process. On the small emulator of the CI the app has 16 MB of heap, and what the tests leave behind adds up until a
# test runs out of memory. A run for each shard of the tests starts with an empty process.
# Usage: scripts/run_device_tests.sh API [SHARDS]
set -u
api="$1"; shards="${2:-2}"
extra=()
case "$api" in
  36) extra+=(-Pandroid.testInstrumentationRunnerArguments.screenTour=true); offset=2 ;;
  33) extra+=(-Pandroid.testInstrumentationRunnerArguments.crawl=true); offset=1 ;;
  *) offset=0 ;;
esac

# Maven Central refuses a download now and then, with the status 429, when many runs of the CI ask at the same time. The tests have not started when
# that happens, so Gradle is started again, up to four times, with a wait that grows. Any other failure, such as a failed test, ends the attempt.
run_gradle() {
  local attempt code log
  log="$(mktemp)"
  for attempt in 1 2 3 4; do
    ./gradlew "$@" 2>&1 | tee "$log"
    code=${PIPESTATUS[0]}
    if [ "$code" -eq 0 ]; then rm -f "$log"; return 0; fi
    if [ "$attempt" -eq 4 ] || ! grep -q "status code 429" "$log"; then rm -f "$log"; return "$code"; fi
    echo "Maven Central answered with 429 (attempt $attempt of 4). Waiting before the next attempt."
    sleep $(( ${RETRY_SECONDS:-60} * attempt ))
  done
}

status=0
for ((shard = 0; shard < shards; shard++)); do
  run_gradle --no-daemon :app:connectedDebugAndroidTest "${extra[@]}" \
    -Pandroid.testInstrumentationRunnerArguments.matrixStride=3 \
    -Pandroid.testInstrumentationRunnerArguments.matrixOffset="$offset" \
    -Pandroid.testInstrumentationRunnerArguments.numShards="$shards" \
    -Pandroid.testInstrumentationRunnerArguments.shardIndex="$shard" || status=1
  # The next run replaces the reports, so each run keeps its own.
  mkdir -p "shard-results/$shard"
  cp -r app/build/reports/androidTests app/build/outputs/androidTest-results "shard-results/$shard/" 2>/dev/null || true
done
exit "$status"
