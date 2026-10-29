# Verification

This file records actual checks rather than a list of intended features. It is updated as testing proceeds.

## Checked locally

- Debug app and instrumentation APK compile with JDK 21 and Android SDK 36.
- Twenty-two JVM tests pass, covering token renewal and rotation, error redaction, catalogue parsing, metadata persistence, stream formats, preview rejection, and XML handling.
- Four device tests pass on an Android 13 x86-64 emulator with Google Play Services and Play Store disabled.
- Device tests verify real 24-bit/96 kHz FLAC playback, position updates, pause controls, duplicate queue entries, queue editing, local playlist persistence, navigation, local search, and theme switching.
- Screenshots are captured from the running app and retrieved by Gradle for visual review.

The playback tests use an original 30-second sine-tone FLAC stored only in the instrumentation APK. They do not substitute for testing a live TIDAL session.

## Live service checks

Browser PKCE sign-in completed through Fennec’s share menu and Undertow’s Android share target. Catalogue search and full-track 16-bit/44.1 kHz FLAC DASH playback succeeded in the app. The player showed the full 2:55 duration and advancing playback position. Device-code login returned full-length AAC for the same track; the app does not label that result as lossless.

A full clear DASH download completed, with roughly 17 MB in persistent offline storage. Playback from that download succeeded after signing out, enabling offline mode, and disabling both Wi-Fi and mobile data on the emulator.

## Still under verification

Hi-res catalogue playback, interrupted-download recovery, release signing and installation, and physical GrapheneOS devices. An emulator passing does not establish hardware-specific audio or Bluetooth behavior.

The service integration uses an unofficial device-client identity. Full account access and returned formats depend on TIDAL. A subscription alone does not establish that a particular client will receive every quality tier.
