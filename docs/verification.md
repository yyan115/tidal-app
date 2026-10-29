# Verification

This file records actual checks rather than a list of intended features. It is updated as testing proceeds.

## Checked locally

- Debug app and instrumentation APK compile with JDK 21 and Android SDK 36.
- Fifteen JVM tests pass, covering token renewal and rotation, error redaction, catalogue parsing, metadata persistence, stream formats, preview rejection, and XML handling.
- Four device tests pass on an Android 13 x86-64 emulator with Google Play Services and Play Store disabled.
- Device tests verify real 24-bit/96 kHz FLAC playback, position updates, pause controls, duplicate queue entries, queue editing, local playlist persistence, navigation, local search, and theme switching.
- Screenshots are captured from the running app and retrieved by Gradle for visual review.

The playback tests use an original 30-second sine-tone FLAC stored only in the instrumentation APK. They do not substitute for testing a live TIDAL session.

## Still under verification

Live TIDAL authorization and full-track lossless playback, network interruptions, download completion and recovery, release signing and installation, and physical GrapheneOS devices. An emulator passing does not establish hardware-specific audio or Bluetooth behavior.

The service integration uses an unofficial device-client identity. Full account access and returned formats depend on TIDAL. A subscription alone does not establish that a particular client will receive every quality tier.
