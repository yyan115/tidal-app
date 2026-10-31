# Changelog

All notable changes to OpenTune are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-10-31

First stable release. OpenTune now has its own application ID, `io.github.yyan115.opentune`. It installs next to the earlier prerelease builds and does not update them. Downloads and the TIDAL session from those builds do not carry over, so export any downloads you want to keep before you uninstall.

### Changed

- Sign in through the browser only. The device-code option, the pasted-link option and the client override setting were removed.
- OpenTune no longer appears in the Android share sheet.
- Releases are built, signed and published by a GitHub Actions workflow with a build provenance attestation. They use a new signing key, listed in the README.

## [0.2.0] - 2026-10-31

Published under the application ID `io.github.yyan115.undertow`.

### Added

- Gallery design across the whole app, with serif headings, custom icons and a floating player.
- Seven color themes, each with light, dark and system appearance.
- Source details in the player showing codec, bit depth and sample rate.

### Changed

- Renamed the app to OpenTune.

## [0.1.1] - 2026-10-30

### Added

- Browser sign-in that returns to the app automatically.

### Changed

- Lossless quality settings reject lossy responses.
- New installs default to lossless quality on Wi-Fi and mobile data.

## [0.1.0] - 2026-10-30

### Added

- TIDAL search, collection browsing and full-track lossless playback.
- Downloads that play without an account.
- Export of downloads as tagged FLAC files without re-encoding.
- Local audio import, device and TIDAL playlists, queue editing, shuffle and repeat.
- Background playback with notification, lock screen and Bluetooth controls.
