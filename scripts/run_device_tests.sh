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
status=0
for ((shard = 0; shard < shards; shard++)); do
  ./gradlew --no-daemon :app:connectedDebugAndroidTest "${extra[@]}" \
    -Pandroid.testInstrumentationRunnerArguments.matrixStride=3 \
    -Pandroid.testInstrumentationRunnerArguments.matrixOffset="$offset" \
    -Pandroid.testInstrumentationRunnerArguments.numShards="$shards" \
    -Pandroid.testInstrumentationRunnerArguments.shardIndex="$shard" || status=1
  # The next run replaces the reports, so each run keeps its own.
  mkdir -p "shard-results/$shard"
  cp -r app/build/reports/androidTests app/build/outputs/androidTest-results "shard-results/$shard/" 2>/dev/null || true
done
exit "$status"
