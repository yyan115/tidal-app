# TIDAL sign-in

1. Choose **Connect TIDAL**, then **Sign in to TIDAL**.
2. Sign in on TIDAL's page in the browser panel.
3. If your browser asks to open Undertow, allow it. Undertow completes sign-in automatically.

Version 0.1.1 uses the registered native callback, `tidal://login/auth`. You do not need to share or paste a page address for new sign-ins. If the official TIDAL app or another Undertow build is also installed, choose the Undertow instance where you started signing in. Debug builds are labelled **Undertow Debug**.

Passwords stay in your browser. Undertow receives a one-time authorization code and verifies its state before exchanging it with the PKCE verifier stored on your device. Pending sign-ins expire after 15 minutes. Account tokens are encrypted using Android Keystore.

The browser panel is a Custom Tab supplied by your browser, rather than an embedded page that Undertow can inspect. A compatible browser such as Vanadium is needed. Browser settings can affect whether it asks for confirmation before returning to the app.

## Older sign-ins and device codes

Version 0.1.0 used an HTTPS callback that could display a 404 page, followed by a manual share step. Upgrade and start a new sign-in to use the automatic return. Previously pending HTTPS links can still be shared to Undertow or pasted through **Other sign-in options** until they expire.

Device-code login is also available under **Other sign-in options**. TIDAL may reject that client or limit it to AAC. Use the main sign-in button for lossless access.

## Audio quality

Lossless settings accept FLAC or ALAC and reject a lossy response from TIDAL. High and Low explicitly allow AAC. A FLAC stream carried in MP4/DASH is still lossless; the player reports the audio codec and source resolution.

New installs default to lossless on Wi-Fi and mobile data. Updates preserve stored preferences, so check **Mobile audio quality** if you previously used High. Max is a request for the best available lossless source; tracks available only at CD quality still play as FLAC.

The integration uses unofficial client identities and can be affected by service changes. Browser authentication follows the [native OAuth guidance](https://www.rfc-editor.org/rfc/rfc8252.html). Protocol references include [tidalapi](https://github.com/EbbLabs/python-tidal) and [Minim](https://github.com/bbye98/minim).
