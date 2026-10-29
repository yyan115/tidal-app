# Undertow

Android music player for TIDAL and local audio files. Built with Kotlin, Compose, and Media3. Google Play Services are not required.

Development is in progress. Local playback, browser sign-in, and live TIDAL lossless playback have been checked on an Android emulator. Downloads and wider device compatibility are still under verification; see [verification](docs/verification.md).

## Features

- Search TIDAL, browse your collection, and play full tracks with a subscription.
- Play local FLAC, AAC, MP3, and other formats supported by Android.
- Manage a queue, save tracks, and combine local and TIDAL tracks in device playlists.
- Keep supported audio offline, with Wi-Fi-only download controls.
- Export clear lossless downloads as tagged FLAC files without re-encoding.
- Control playback from notifications, the lock screen, or a Bluetooth device.
- Show the audio format returned by the service rather than assuming the requested quality was delivered.

TIDAL integration is unofficial. It connects directly to TIDAL and requires your own account. Service changes can affect authentication and playback. Protected streams are not supported.

Use [browser sign-in](docs/sign-in.md) for lossless access. The final TIDAL page must be shared back to Undertow; device-code login can return AAC instead.

## Build

Requirements: JDK 21, Android SDK 36, and Android Build Tools 35.0.0. Set `ANDROID_HOME` or add the SDK path to an untracked `local.properties` file.

```sh
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Debug and release builds use different application IDs, so both can be installed together.

## Test

```sh
./gradlew :core:test :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The second command needs an emulator or connected Android device. Device tests play an original 24-bit/96 kHz FLAC fixture, exercise the interface, and capture screenshots. Gradle retrieves those images under `app/build/outputs/connected_android_test_additional_output/`.

See [verification](docs/verification.md) for the checks performed and their limits.

## Privacy

Account tokens are encrypted with a key held in Android Keystore. Password entry happens on TIDAL's website. The app has no advertising, analytics, crash-reporting service, or shared account backend. TIDAL still receives account and streaming requests. Local files can be played without signing in.

The file picker grants access only to the audio files you choose. Imported files stay in their original locations. Explicit downloads are stored in app data; temporary streaming cache is limited to 256 MB. Android backup is disabled for account and library data.

## Development

The `core` module contains the service client, models, and stream parsing. The `app` module contains storage, playback, downloads, and the Android interface. Tests do not need a TIDAL password. Keep real accounts, tokens, signing material, and private recordings out of the repository and issue reports.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a change. Report vulnerabilities using [SECURITY.md](SECURITY.md).

## License

[GPL-3.0-or-later](LICENSE). Dependency licences are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
