# Releasing

Releases are cut by the **Release** workflow. It needs no local build.

## One-time setup

Create a PKCS12 keystore with the key alias `opentune`. Keep the keystore and its password outside the repository, with an encrypted backup. Every update must use the same key.

Add two repository secrets under **Settings**, **Secrets and variables**, **Actions**:

- `RELEASE_KEYSTORE_BASE64`: the keystore, base64-encoded.
- `RELEASE_KEYSTORE_PASSWORD`: its password. The key password must be the same.

The SHA-256 of the signing certificate is stored in `.github/signing-certificate.sha256`. The workflow refuses to publish an APK signed with any other certificate. Update that file and the fingerprint in the README only when you rotate the key on purpose.

## Cutting a release

`main` is protected, so a release has three parts: a pull request that prepares the version, a workflow run that builds a draft, and your decision to publish it.

1. Make sure the `Unreleased` section of `CHANGELOG.md` lists every change that users can see.
2. On a branch, run `python3 scripts/release.py prepare 1.1.0`, commit the changes of `VERSION`, `CHANGELOG.md` and `fastlane`, and open a pull request titled `Release 1.1.0`. Merge it when the checks pass.
3. Open **Actions**, choose **Release**, select **Run workflow** on `main` and enter the same version. Select **dry run** to build and test without creating a release.

The workflow then:

1. Runs the full check workflow on the commit.
2. Checks that `VERSION`, the changelog entry and the F-Droid changelog match the version, and that the tag does not exist.
3. Builds the release APK, signs it and checks its certificate and signature.
4. Creates a build provenance attestation.
5. Installs the previous release on emulators with Android 8 and Android 16, starts it, installs the new APK over it and starts that. The run fails when the app stops or logs a crash.
6. Creates a **draft** release with the APK and `SHA256SUMS`. Versions below 1.0.0 are marked as prereleases.

A draft is invisible to downloaders and to the update check in the app. Nothing reaches users until you publish it.

4. Download the APK from the draft, install it over your current version on a phone, and check sign-in, playback and a download.
5. Publish the draft on GitHub, or run `gh release edit v1.1.0 --draft=false`.

## Local upgrade test

With an emulator or a device connected, `scripts/smoke_upgrade.sh NEW.apk PREVIOUS.apk` runs the same check as the workflow.

## Local signed build

Set `OPENTUNE_KEYSTORE`, `OPENTUNE_STORE_PASSWORD`, `OPENTUNE_KEY_PASSWORD` and, if the alias differs from `opentune`, `OPENTUNE_KEY_ALIAS` in a private environment, then run:

```sh
./gradlew :app:assembleRelease
```

Do not write these values to shell history or to committed Gradle properties.

## Version numbers

`VERSION` holds `MAJOR.MINOR.PATCH`. Gradle derives the Android version code as `MAJOR × 1,000,000 + MINOR × 1,000 + PATCH`, so each component must stay at or below 999.
