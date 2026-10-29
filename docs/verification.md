# Verification

This file records actual checks rather than a list of intended features. Last checked on 2026-10-30.

## Checked locally

- Debug app and instrumentation APK compile with JDK 21 and Android SDK 36.
- Thirty JVM tests pass, covering token renewal and rotation, error redaction, catalogue parsing, metadata persistence, stream formats, preview rejection, and XML handling.
- Ten device tests pass on an Android 13 x86-64 emulator with Google Play Services and Play Store disabled.
- Device tests verify real 24-bit/96 kHz FLAC playback in normal and offline modes, position updates, pause controls, duplicate queue entries, queue editing, local playlist persistence, navigation, local search, and theme switching.
- Device tests also cover database migration, account-scoped collection caches, persistent pending favorites, adding a track when creating a playlist, and safe removal of imported tracks.
- Screenshots are captured from the running app and retrieved by Gradle for visual review. Portrait, landscape, light/dark themes, and 200% text size were inspected.
- The optimized release APK passes lint and signature verification, installs and updates, imports audio through Android’s file picker, plays FLAC, and displays its bundled license texts. Browser PKCE sign-in, live catalogue search, full-length FLAC streaming, and one-tap download also succeeded in that release build.

The playback tests use an original 30-second sine-tone FLAC stored only in the instrumentation APK. They do not substitute for testing a live TIDAL session.

The GitHub check workflow also passed the device suite on Android 8, 13, and 16.

## Live service checks

Browser PKCE sign-in completed through Fennec’s share menu and Undertow’s Android share target. Catalogue search and full-track 16-bit/44.1 kHz FLAC DASH playback succeeded in the app. The player showed the full 2:55 duration and advancing playback position. Device-code login returned full-length AAC for the same track; the app does not label that result as lossless.

A full clear DASH download completed, with roughly 17 MB in persistent offline storage. Playback from that download succeeded after signing out, enabling offline mode, and disabling both Wi-Fi and mobile data on the emulator.

Three optional live-account tests also passed: an offline saved-track change synced when reconnected and was restored afterward; a temporary playlist was created, renamed, edited, and deleted; a full 24-bit/192 kHz FLAC stream was played, downloaded, and replayed after sign-out and streaming-cache removal. The downloaded manifest’s codec, bit depth, and sample rate were checked on the device.

A native FLAC export from the hi-res download passed playback and seek tests with no account or network. FFprobe identified 24-bit/192 kHz stereo FLAC, the full 186.673792-second duration, and title/artist/album tags. Decoding the entire file produced 215,048,208 PCM bytes; their MD5 matched the original STREAMINFO checksum. A separate local-file export preserved its SHA-256 exactly.

Export through Android’s document picker, re-import, background playback, Android media-session pause, and queue/position restoration after a full process restart also succeeded with Wi-Fi and mobile data disabled.

## Limits

Physical GrapheneOS devices, USB DACs, Bluetooth devices, and long-running battery behavior have not been checked. An emulator passing does not establish hardware-specific audio behavior. Interrupted downloads clean up incomplete data and can be retried; every network/provider failure mode has not been exercised.

The service integration uses unofficial client identities. Full account access and returned formats depend on TIDAL. A subscription alone does not establish that a particular client will receive every quality tier.
