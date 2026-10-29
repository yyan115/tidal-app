# TIDAL sign-in

1. Choose **Connect TIDAL**, then **Sign in in browser**.
2. Sign in on TIDAL's website. A blank or error page can appear after authorization.
3. Share that final page from your browser and choose **Complete TIDAL sign-in**. If your browser cannot share the address, copy it and use **Paste final sign-in link** in Undertow.

The final address starts with `https://tidal.com/android/login/auth`. Treat it as a temporary credential: share it only with Undertow and keep it out of issue reports.

The browser flow uses PKCE. Undertow checks the callback address and a random state value, and exchanges the one-time code with the verifier saved on your device. Pending sign-ins expire after 15 minutes. Passwords are entered in your browser; Undertow has no password field.

This extra share step is required because the unofficial client identity's registered callback belongs to TIDAL. TIDAL rejected a dedicated Undertow callback during testing. Undertow does not claim ownership of TIDAL's website links.

Device-code sign-in is also available, including authorization from another device. In our live test, that client received full-length AAC even when requesting lossless. Browser sign-in received clear FLAC streams at 16-bit/44.1 kHz and 24-bit/192 kHz. The player shows the delivered format; quality settings are requests, not guarantees.

Both methods use unofficial client identities and can be affected by service changes. Browser login follows the [native OAuth guidance](https://www.rfc-editor.org/rfc/rfc8252.html). The protocol was checked against [tidalapi](https://github.com/EbbLabs/python-tidal).
