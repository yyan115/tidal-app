# Releasing

Use JDK 21 and the Android SDK versions in the README. Update `VERSION`, the release notes, and the verification record. A version has three numeric components; the Android version code is derived from them.

Run the core tests, both lint variants, and the device suite before tagging. Inspect the running app and install a signed, optimized release build. Device CI covers API 26, 33, and 36 with Google Play Services disabled. Account tests remain an explicit local check and never run in public CI.

## Signing

The release workflow expects two repository Actions secrets:

- `RELEASE_KEYSTORE_BASE64`: base64-encoded PKCS12 keystore.
- `RELEASE_KEYSTORE_PASSWORD`: its password. The key alias is `undertow`.

Keep the keystore and its password outside the repository and retain an encrypted backup. Updates must use the same signing key. The current signing certificate's SHA-256 is:

```text
75ef4ecb7ef9d49acc629e4fca8f187fa485b8c04dc7797e22b80762991230c2
```

For a local signed build, set `UNDERTOW_KEYSTORE`, `UNDERTOW_STORE_PASSWORD`, `UNDERTOW_KEY_PASSWORD`, and optionally `UNDERTOW_KEY_ALIAS` in a private environment, then run `./gradlew :app:assembleRelease`. Do not put password values into shell history or Gradle properties committed to Git.

## Publishing

Push the reviewed commit to `main`. After its checks pass, tag that commit `v` followed by `VERSION` and push the tag. The release workflow repeats the checks, verifies the version and main-branch ancestry, signs the APK, verifies its signature, and publishes a prerelease with `SHA256SUMS`.

Download the published APK, verify its checksum and signing certificate, and install that exact artifact. Keep release notes specific to the shipped behavior and record any remaining device limitations.
