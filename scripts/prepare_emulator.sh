#!/usr/bin/env bash
# Prepares the emulator of the CI for the device tests.
#
# The emulator of Android 10 sets the memory setting of its image after the system has started, and the system reads it only when it starts.
# An app then gets 16 MB, and the system process gets 16 MB too, where a phone gives 192 MB or more. The emulators of Android 13 and 16 set the
# limit for apps themselves. When the limit is missing, the system is stopped and started again, which makes it read the setting.
# MemoryLimitTest fails when an app still has less than 192 MB.
set -u

wait_seconds="${WAIT_SECONDS:-2}"
attempts="${WAIT_ATTEMPTS:-90}"

limit="$(adb shell getprop dalvik.vm.heapgrowthlimit | tr -d '\r')"
if [ -n "$limit" ]; then
  echo "Apps may use $limit on this emulator. The system is not restarted."
  exit 0
fi

echo "This emulator sets no memory limit for apps. Restarting the system so that it reads the memory setting of the emulator."
adb root
adb wait-for-device
adb shell setprop sys.boot_completed 0
adb shell stop
adb shell start

started=false
for ((attempt = 1; attempt <= attempts; attempt++)); do
  if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && adb shell pm path android > /dev/null 2>&1; then
    started=true
    break
  fi
  sleep "$wait_seconds"
done
if [ "$started" != true ]; then
  echo "The system did not start again after $attempts attempts."
  exit 1
fi

# The settings are kept in the settings database, and are set again so that the tests never depend on that.
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$setting" 0
done
echo "The system has started again."
