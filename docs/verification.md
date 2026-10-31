# Verification

Last checked on 2026-10-31 for version 0.2.0. This records completed checks and their limits.

## Builds and device checks

- Thirty-seven JVM tests pass: token renewal, error redaction, catalogue and stream parsing, native callback validation, metadata, and rejection of lossy responses in lossless mode.
- Thirteen device tests pass on Android 16 with Google Play Services disabled. Five opt-in account/export checks are skipped in the default suite.
- Device tests play an original 24-bit/96 kHz FLAC and exercise pause, seek, offline playback, duplicate queue entries, queue editing, local search, playlists, data migration, collection caches and saved-track sync state.
- The new source panel reports FLAC, 24 bit and 96 kHz for that recording. The application-name test checks OpenTune's launcher label, application class and unchanged Android package identity. Callback routing resolves to the app and excludes unrelated TIDAL links.
- Debug and optimized release builds pass lint. The signed update installed over 0.1.1 with the same certificate; the existing download and preferences survived. That download played at 16-bit/44.1 kHz with both radios disabled.
- Screens were inspected in light/dark appearance, at 360 × 640 dp, in landscape and at 200% text size. The album grid, source sheet, custom icons and theme picker were reviewed. Primary, secondary and accent text contrast is at least 5.09:1 across all fourteen palette/appearance combinations.

GitHub device CI covers Android 8, 13 and 16. Account credentials and signing material are excluded from public test artifacts.

## Live service

The signed 0.2.0 build completed normal browser sign-in through Chromium Custom Tabs on Android 16 and returned through the native callback. A fresh catalogue search played a full 3:06 FLAC track at 24-bit/192 kHz. Playback advanced past 30 seconds. The source sheet showed the delivered format, and the player download completed.

The new hi-res download played after signing out and disabling Wi-Fi and mobile data. Seeking to 2:45 succeeded and playback continued. The README screenshots come from this signed build.

Four optional live-account tests also pass on 0.2.0: an offline saved-track change syncs after reconnecting; a temporary playlist is created, renamed, edited and deleted; a failed search retries through the interface after both radios reconnect; and hi-res audio streams, downloads and plays after sign-out and streaming-cache removal. Account changes are restored after the checks.

## Earlier compatibility checks

Version 0.1.1 also completed automatic browser sign-in in Fennec on Android 13. Full-length 16-bit/44.1 kHz FLAC playback and downloads passed. Device-code access returned AAC; lossless modes rejected it.

A hi-res FLAC export from that release passed account-free playback and seeking. FFprobe reported 24-bit/192 kHz stereo, the full 186.673792-second duration, and title/artist/album tags. Decoding the file produced 215,048,208 PCM bytes with the original STREAMINFO MD5. A local-file export preserved its SHA-256. Document-picker export/re-import, background playback, Android media controls and queue/position restoration after process restart also passed offline.

## Limits

Physical GrapheneOS devices, USB DACs, Bluetooth devices and long-running battery behavior have not been checked. Source resolution does not establish the hardware output format. Interrupted downloads clean up incomplete data and can be retried; every network/provider failure mode has not been exercised.

The service integration uses unofficial client identities. Full account access and returned formats depend on TIDAL. A subscription alone does not establish that a particular client can access every format.
