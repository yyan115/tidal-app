# Usage guide

## Signing in

1. Choose **Connect TIDAL**, then **Sign in to TIDAL**.
2. Sign in on TIDAL's page in your browser.
3. If your browser asks to open OpenTune, allow it. OpenTune finishes signing in automatically.

If the official TIDAL app is also installed, Android may ask which app should open the sign-in link. Choose OpenTune.

OpenTune receives a one-time authorization code and checks the state value before it exchanges the code with TIDAL. The exchange includes a PKCE verifier that OpenTune generates on your device for each sign-in. A pending sign-in expires after 15 minutes. Browser authentication follows [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252.html).

## Finding music

**Listen** shows your TIDAL mixes, radio stations and new releases below your recent plays. Pull down to refresh.

**Search** opens on genres, moods and decades. Typing shows a top result and tabs for each kind of result. Recent searches stay on the device and can be cleared.

Artist, album and mix pages list the music with the biography, review and credits that TIDAL provides. Use **Follow** on an artist page to add the artist to **Library**, **Artists**. The track menu has **Start track radio** and **Credits**.

## Lyrics

Choose **Lyrics** in the player. Lines with timing are highlighted as they are sung. Drag to read ahead, and the view follows the song again after a few seconds. Tap a line to jump to it. Tracks without timing show the plain text, and some tracks have no lyrics. Lyrics are provided by Musixmatch through TIDAL and stay on the device after the first load.

## Playback options

The timer button in the player sets a **sleep timer** that pauses playback after 15 to 90 minutes or at the end of the track.

Under **Settings**:

- **Volume normalization** evens out the level between tracks using the ReplayGain values from TIDAL. Quiet tracks are not amplified. It is off by default so that tracks play unchanged.
- **Equalizer** adjusts the bands of Android's built-in equalizer and offers its presets. The equalizer changes the audio, so leave it off for unaltered playback.
- **Audio output** shows where the music plays and the sample rates that the output accepts.
- **Bit-perfect USB output** asks Android 14 or later to send the audio to a USB DAC without mixing. It is experimental and has not been tested with a DAC yet.

## Cars, Bluetooth and the widget

Cars and Bluetooth devices can browse **Recently played**, **Saved tracks**, **Albums**, **Playlists** and **Downloads**, and start a track from there. Lists are served from the device first, so browsing works without a connection, but streaming a track needs one unless it is downloaded. Android Auto needs its developer setting for apps from unknown sources, as for any app that is not installed from Google Play. A voice request such as "play Nujabes" searches your library and TIDAL.

Add the **OpenTune** widget from your launcher's widget list. It shows the current track with previous, play or pause, and next buttons. A tap on the cover or the title opens the app.

## Offline listening

Turn on **Keep saved tracks offline** under **Settings**, **Privacy and data** to download your saved TIDAL tracks in the background, 50 at a time. Downloads follow the Wi-Fi setting and pause when free space is low.

## Statistics, scrobbling and backup

**Listening statistics** counts a listen after 30 seconds, or after half of the track when that comes first. The numbers stay on the device and **Clear listening history** removes them.

**Scrobbling** is off by default. Add a [ListenBrainz](https://listenbrainz.org) user token, or the address of your own ListenBrainz-compatible server, and OpenTune sends the artist, title and album of each finished track. Listens that cannot be sent wait on the device until the server answers. The token is stored encrypted.

**Back up data** writes a file with your settings, device playlists and listening history. **Restore data** merges such a file into the current install. TIDAL data stays in your account. The file holds no tokens, downloads or imported audio files.

## Audio quality

Choose the quality for Wi-Fi and mobile data under **Settings**.

| Setting | Delivered audio |
| --- | --- |
| Max | The best lossless source available, up to 24-bit/192 kHz. |
| Lossless | CD-quality FLAC. |
| High | AAC 320 kbps. |
| Low | AAC 96 kbps. |

Max and Lossless accept FLAC or ALAC and reject lossy responses. A FLAC stream carried in MP4 or DASH is still lossless. Tap the format line in the player to see the codec, bit depth and sample rate of the source. These values describe the file, not the output of your audio device.

New installs default to lossless on both networks.

## Downloads

Choose **Download** in a track's menu or in the player. Finished downloads appear in **Downloads** and play without a connection or an account. Downloads wait for Wi-Fi by default.

Downloads are stored in OpenTune's private storage. Android removes them when you uninstall the app or clear its storage. Use export to keep a copy.

## Export

Choose **Export audio file** in the menu of a downloaded or local track, then pick a destination with Android's file picker. Lossless downloads become tagged `.flac` files without re-encoding. Local files are copied unchanged.

## Local files

Add audio files with the plus button on **Listen**, or with **Import** under **Library**, **On device**. OpenTune plays the files from where they are and does not copy them. Android decides which formats play. FLAC, MP3 and AAC work on supported devices.

## Offline mode

Turn on **Offline mode** in **Settings** to play only audio stored on the device. OpenTune sends no requests to TIDAL in this mode.
