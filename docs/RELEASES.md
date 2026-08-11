# Release and migration policy

## Version identity

`gradle.properties` is the source of truth for `homeStashTvVersionName` and
`homeStashTvVersionCode`.

- The version name follows semantic versioning, including an optional suffix
  such as `0.1.0-rc.1`.
- A release tag is the letter `v` plus that exact version name.
- The positive integer version code must increase for every APK that is
  expected to update an installed production build.
- A release workflow rejects a tag that differs from the committed version.

The debug package is `com.yahigod.homestashtv.debug` and appends `-dev` to the
version name. The production package is `com.yahigod.homestashtv`.

## Signing boundary

Development and production builds use separate signing configurations and
separate environment variables. Production signing material is owner-held,
never committed, and supplied to the tagged-release workflow through these
GitHub secrets:

- `HOME_STASH_TV_RELEASE_KEYSTORE_B64`
- `HOME_STASH_TV_RELEASE_KEYSTORE_PASSWORD`
- `HOME_STASH_TV_RELEASE_KEY_ALIAS`
- `HOME_STASH_TV_RELEASE_KEY_PASSWORD`

The workflow fails before building if any input is absent. It verifies the
finished APK with Android `apksigner`, publishes the certificate report, and
publishes an APK SHA-256 checksum. The production keystore needs at least two
offline backups. Losing it prevents normal updates to installed production
copies.

## Release sequence

1. Update the committed version name and monotonically increasing version code
   in a reviewed pull request.
2. Pass the normal Android workflow from the exact pull-request head and again
   on `main`.
3. Create the matching annotated tag from the accepted `main` commit.
4. Let the Release workflow build and publish the signed APK. A version with a
   suffix such as `-rc.1` is published as a prerelease.
5. Compare the downloaded APK with `SHA256SUMS` and verify its signer before
   installation.
6. Record clean-install or upgrade evidence on the release checkpoint.

Tags must point to a commit contained in `main`. A tag never bypasses tests,
lint, signing validation, signature verification, or checksum generation.

## Data migration and upgrades

Production-to-production upgrades use Android's normal in-place update. They
preserve app-private profile metadata, encrypted Stash credentials, pairing,
the command ledger, and paused recovery state when all of these remain true:

- the application ID is unchanged;
- the APK uses the same production signing identity; and
- the new version code is greater than the installed version code.

Stored schemas and Android Keystore aliases carry explicit version suffixes.
Future incompatible changes must add a tested forward migration. A released
schema or key alias must not be repurposed silently.

The existing development build is intentionally not an upgrade predecessor of
the production app: it has a different application ID and signer. Secrets are
not exported automatically between them. For the first production install,
install the release alongside the debug app, recreate profiles, and pair the
production receiver independently. Remove the debug app only after the
production Send-to-TV flow passes.

The CP7 upgrade criterion is therefore tested with a production-signed release
candidate followed by a higher-version production-signed build. That test must
show that valid profiles and pairing remain usable without re-entry.

## Rollback

Keep the previous published APK and its checksum, but prefer a corrected
higher-version release over Android version downgrades. Uninstalling the app
deletes app-private profiles, credentials, pairing, and recovery state. Treat
uninstall/reinstall as a recovery operation that requires profile setup and
pairing again, not as a transparent rollback.
