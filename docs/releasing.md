# Releasing

Releases are cut by the **Release** workflow. It needs no local build.

## One-time setup

Create a PKCS12 keystore with the key alias `opentune`. Keep the keystore and its password outside the repository, with an encrypted backup. Every update must use the same key.

Add two repository secrets under **Settings**, **Secrets and variables**, **Actions**:

- `RELEASE_KEYSTORE_BASE64`: the keystore, base64-encoded.
- `RELEASE_KEYSTORE_PASSWORD`: its password. The key password must be the same.

The SHA-256 of the signing certificate is stored in `.github/signing-certificate.sha256`. The workflow refuses to publish an APK signed with any other certificate. Update that file and the fingerprint in the README only when you rotate the key on purpose.

The workflow pushes the version commit and tag to `main`. If you protect `main`, allow GitHub Actions to bypass the pull request requirement.

## Cutting a release

1. Make sure the `Unreleased` section of `CHANGELOG.md` lists every change that users can see.
2. Open **Actions**, choose **Release**, select **Run workflow** on `main` and enter the version, for example `1.1.0`.

The workflow then:

1. Runs the full check workflow on the commit.
2. Rejects a version that is not newer than `VERSION` or that already has a tag.
3. Moves the `Unreleased` entries to the new version in `CHANGELOG.md`, updates `VERSION` and writes the F-Droid changelog.
4. Builds the release APK, signs it and checks its certificate and signature.
5. Creates a build provenance attestation.
6. Commits the version change, tags it `v<version>` and pushes both to `main`.
7. Publishes the GitHub release with the APK and `SHA256SUMS`. Versions below 1.0.0 are marked as prereleases.

Nothing is pushed or published if an earlier step fails.

After the release is published, install the downloaded APK on a device and check sign-in and playback.

## Local signed build

Set `OPENTUNE_KEYSTORE`, `OPENTUNE_STORE_PASSWORD`, `OPENTUNE_KEY_PASSWORD` and, if the alias differs from `opentune`, `OPENTUNE_KEY_ALIAS` in a private environment, then run:

```sh
./gradlew :app:assembleRelease
```

Do not write these values to shell history or to committed Gradle properties.

## Version numbers

`VERSION` holds `MAJOR.MINOR.PATCH`. Gradle derives the Android version code as `MAJOR × 1,000,000 + MINOR × 1,000 + PATCH`, so each component must stay at or below 999.
