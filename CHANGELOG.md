# Changelog

All notable changes to OpenTune are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.4.0] - 2026-11-01

### Added

- Playlist folders: Library shows your TIDAL folders and the playlists inside them. Create, rename and delete folders, and move a playlist into a folder with its folder button. Deleting a folder moves its playlists to the top level first.
- Translation support. All text in the interface, the widget, the notifications and the error messages comes from string resources, and the system language settings list every language that has translations. [docs/translating.md](docs/translating.md) explains how to add a language. Only English ships so far.
- TIDAL pages, such as Home and Explore, now ask TIDAL for text in the language of the device.
- Checks for translation: a script rejects text that is written in code, and a device test runs the main screens in a pseudo-language to show that no text is left untranslated.

### Fixed

- Missing fields from TIDAL no longer appear as the word "null" in lists.
- The button row at the bottom of the player wraps onto a second line when the labels do not fit, instead of squeezing them. This affects small screens and longer text.

### Changed

- The ATMOS badge is gone. TIDAL sends this client the stereo mix of every track that also has an Atmos mix, so OpenTune cannot play Atmos. The limit is listed in the usage guide.

## [0.3.0] - 2026-11-01

OpenTune now has its own application ID, `io.github.yyan115.opentune`. It installs next to the earlier prerelease builds and does not update them. Downloads and the TIDAL session from those builds do not carry over, so export any downloads you want to keep before you uninstall.

### Added

- Synced lyrics in the player. The current line is highlighted and centred, and a tap on a line seeks to it. Lyrics are kept on the device after the first load.
- Personal sections on Listen from TIDAL: your mixes, radio stations, albums based on what you played, new releases and editors' playlists. Pull down to refresh.
- Explore in Search: genres, moods, decades and the Hi-Res page. Recent searches stay on the device.
- Search shows a top result and has tabs for tracks, albums, artists, playlists and videos.
- Artist pages with biography, popular tracks, albums, singles, playlists, videos and related artists. Follow artists and play artist radio.
- Album pages with TIDAL's review, quality badges and full credits. Mix pages for your daily mixes.
- Track radio and track credits in the track menu.
- Library lists followed artists and can filter and sort saved tracks and albums. Shuffle plays all filtered tracks.
- Sleep timer in the player: 15 to 90 minutes, or the end of the current track.
- Volume normalization from TIDAL's ReplayGain values, by track or by album. It is off by default.
- Equalizer with presets, using the equalizer that Android provides.
- Audio output information in Settings, showing where music plays and which sample rates the output accepts.
- Experimental bit-perfect output to a USB DAC on Android 14 and later. It is off by default and has not been tested with a DAC yet.
- Listening history on the device, with a statistics view for top tracks and artists.
- Optional scrobbling to ListenBrainz or a compatible server. It is off by default and queues listens while the server is out of reach.
- Music videos from artist pages and search, with picture-in-picture.
- Back up and restore for settings, device playlists and listening history.
- Keep saved tracks offline: downloads your saved TIDAL tracks in the background.
- Move tracks up and down in your playlists, on the device and in TIDAL.
- Mixes in Library, "Playing from" in the player and saving the queue as a playlist.
- Browsing for cars and Bluetooth devices, including Android Auto, and voice requests such as "play Nujabes".
- A home screen widget with the current track and playback buttons.

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
