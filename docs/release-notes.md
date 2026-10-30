Fix TIDAL sign-in and lossless defaults.

- Sign in through a secure browser panel and return to Undertow automatically. New sign-ins no longer require sharing a 404 page or pasting a callback address.
- Keep device-code and legacy sign-in options out of the main flow.
- Default new installs to lossless on Wi-Fi and mobile data. Existing preferences are preserved.
- Reject AAC when lossless is requested instead of silently accepting a lower-quality response.
- Validate native and legacy callback origins, state, and duplicate parameters. Keep older pending sign-ins usable until they expire.

Install this APK over version 0.1.0 to keep your library and downloads. Android 8 or later is required. Google Play Services are not required. See the README and sign-in guide for usage and verification details.

TIDAL integration is unofficial. Protected streams are unsupported. Physical GrapheneOS devices, USB DACs, Bluetooth devices, and long-running battery behavior have not been checked.
