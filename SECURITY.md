# Security

Report account, token, or file-access vulnerabilities privately through [GitHub security advisories](https://github.com/yyan115/tidal-app/security/advisories/new). Do not put credentials or working audio URLs in public issues.

Include the affected version, reproduction steps, and the impact. Use a test account and a minimal example where possible.

Account tokens and OAuth client overrides are encrypted using Android Keystore. The release app does not expose an endpoint for importing account tokens. Signing keys and account credentials are not part of this repository or its test fixtures.
