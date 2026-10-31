# Contributing

For a bug report, include the app version, Android version, device, and steps to reproduce it. Describe the expected result and what happened. Screenshots help with layout problems. Remove account details, access tokens, and signed audio URLs from logs.

Keep changes focused. For behavior changes, add a test at the level that exercises the failure. For interface changes, check the running screen at a small phone size and with larger text. Include a screenshot when it helps explain the change.

Before opening a pull request:

```sh
./gradlew :core:test :app:lintDebug :app:assembleDebug
```

Run the device tests for changes to playback, persistence, permissions, or navigation. Explain the change and its validation in the pull request. Documentation should say what the app does, how to use it, and what remains limited.

Live TIDAL checks are opt-in and do not run in CI. Install the debug app and instrumentation APK, sign in normally in **OpenTune Debug**, then run:

```sh
adb shell am instrument -w -r -e liveAccount true \
  -e class io.github.yyan115.undertow.LiveServiceTest \
  io.github.yyan115.undertow.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The checks download a track, change and restore a saved-track entry, and create and remove a temporary playlist. Add `-e networkToggle true` only on a dedicated test device: that check disables and re-enables Wi-Fi and mobile data. Do not put account credentials or tokens in source files or CI secrets.

This project uses Kotlin's standard style and the Gradle wrapper. Contributions are licensed under GPL-3.0-or-later.
