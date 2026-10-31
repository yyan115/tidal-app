# Security policy

## Supported versions

Only the latest release receives security fixes.

## Reporting a vulnerability

Report vulnerabilities privately through [GitHub security advisories](https://github.com/yyan115/tidal-app/security/advisories/new). Do not put credentials or working audio URLs in public issues.

Include the affected version, the steps to reproduce and the impact. Use a test account and a minimal example where possible.

## Scope

Reports about account tokens, file access, sign-in handling and the signing of releases are in scope. Signing keys and account credentials are not part of this repository or its test fixtures. Account tokens are encrypted with a key held in the Android Keystore, and release builds do not provide a way to import tokens.
