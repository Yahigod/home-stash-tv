# Installation, update, recovery, and troubleshooting

## Requirements

- Android TV 8.0 / API 26 or newer
- A Home Stash TV bridge reachable on the same trusted local network
- A Home Stash sender configured for that bridge
- At least one reachable Stash server and an API key when that server requires
  one

No private address, device identifier, or credential belongs in this
repository or in a GitHub issue.

## Verify a release

Download the APK, `SHA256SUMS`, and `signing-certificate.txt` from the same
GitHub release. From that directory, verify the file hash:

```bash
sha256sum --check SHA256SUMS
```

When Android build tools are installed, verify the APK signature and compare
the reported certificate SHA-256 digest with `signing-certificate.txt`:

```bash
apksigner verify --verbose --print-certs home-stash-tv-v0.1.0-rc.1.apk
```

Stop if either result differs. Do not substitute an APK from a workflow run,
chat attachment, or another release.

## Clean installation

Use the television's normal trusted APK installation flow, or select the exact
ADB device explicitly:

```bash
adb -s <tv-serial> install home-stash-tv-v0.1.0-rc.1.apk
```

Open **Home Stash TV** from the Android TV app launcher. A clean production
installation can coexist with the old development package while acceptance is
in progress.

### Configure a Stash profile

1. Open **Server profiles**.
2. Add a descriptive name, the complete HTTP or HTTPS Stash address, and the
   API key if required.
3. Select **Test connection**.
4. Save only after the connection test succeeds.

The API key is encrypted with Android Keystore and is not displayed again.

### Pair the receiver

1. Open **Bridge pairing**.
2. Enter the complete local bridge address and a recognizable TV name.
3. Select **Start pairing**.
4. Approve the six-digit code locally on the bridge host using that bridge's
   operator documentation. The code expires after five minutes and must not be
   copied into an issue or log.
5. Wait for **Pairing approved**, then confirm the home screen reports the
   receiver connected.
6. From Home Stash, send one representative scene and confirm playback.

## In-place update

Verify the new release first. An update must have the same production signer
and a higher version code:

```bash
adb -s <tv-serial> install -r home-stash-tv-v0.1.1.apk
```

After installation, open the app and confirm the existing profiles are still
listed, the bridge reconnects without new pairing, and a scene plays. Do not
uninstall first: uninstalling deletes the app's private data.

## Recovery

### Lost or revoked bridge pairing

Open **Bridge pairing**, choose **Forget pairing**, and complete a new pairing.
The bridge operator should revoke the obsolete receiver record. Existing
Stash profiles are independent and remain available.

### Changed Stash address

Open **Server profiles**, edit the existing profile, replace its address, test
the connection, and save. Editing preserves the stable profile ID used by
sender commands.

### Revoked or replaced Stash credential

Edit the existing profile, enter the replacement API key, test the connection,
and save. If access should be removed completely, delete the profile; deletion
also removes its locally encrypted credential.

### App will not reconnect

1. Confirm the TV and bridge are on the trusted local network.
2. Read the credential-free connection status on the app home screen.
3. If the bridge reports this receiver revoked or the app remains unpaired,
   forget the local pairing and pair again.
4. Do not post bridge addresses, receiver IDs, codes, tokens, or API keys in
   public diagnostics.

### Playback fails

- A missing profile requires selecting or recreating the matching profile.
- Authentication failures require testing and replacing the Stash credential.
- Network failures require restoring TV-to-Stash connectivity.
- Unsupported or decoding failures require a compatible media rendition.

The app and bridge report stable error categories rather than credential,
authorization-header, or media-URL contents.

## Last-resort reset

Clearing app storage or uninstalling deletes local profiles, encrypted
credentials, pairing, the command ledger, and recovery state. Record the names
of required profiles first, revoke the old receiver at the bridge, then perform
a clean installation and pair again. Never export raw credentials into a
ticket or repository as a shortcut.
