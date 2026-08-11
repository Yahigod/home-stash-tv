# Receiver MVP Roadmap

The MVP is complete when a user can select a scene or queue in Home Stash, choose a paired Android TV, and have the native app reliably begin and continue playback without TV Bro.

Each checkpoint must leave the system testable and usable. Work does not advance past a device-dependent checkpoint until it passes on the target Tesla TV.

## 1. Android TV foundation

Deliverables:

- Kotlin Android TV application
- Compose for TV shell with D-pad focus handling
- Media3 dependency and empty playback service
- Debug and release build variants
- Unit-test, lint, and debug-APK CI
- No secrets or machine-specific configuration

Exit criteria:

- CI builds from a clean checkout.
- Debug APK installs and launches on the Tesla TV.
- The app can be fully exited with the remote.
- A placeholder screen remains legible at normal viewing distance.

## 2. Single-scene playback proof

Deliverables:

- Temporary developer configuration for one test Stash server
- Resolve one known scene through the Stash API
- Media3 playback with play/pause, seek, back, and error display
- Initial codec and audio compatibility notes

Exit criteria:

- One representative scene plays from Stash without TV Bro.
- Seeking and remote controls work.
- App background/foreground transitions do not corrupt playback.
- Unsupported media produces an actionable error instead of a crash.

This is the primary feasibility gate. Architecture may change if the Tesla exposes codec or Android compatibility constraints.

## 3. Stash server profiles

Deliverables:

- Add, edit, test, and delete named server profiles
- Support multiple Stash instances
- API-key authentication
- Keystore-backed credential storage
- Stable, non-secret profile IDs for receiver commands

Exit criteria:

- Normal Stash and JAV Stash can both be configured.
- Connection tests distinguish DNS, network, TLS, and authentication failures.
- Credentials never appear in logs, exported state, screenshots, or repository files.
- Deleting a profile revokes its local credential and does not break other profiles.

## 4. Pairing and receiver protocol

Deliverables:

- Versioned protocol specification
- One-time pairing code flow
- Outbound receiver connection
- Reconnection with bounded backoff
- Pending-command delivery, expiry, acknowledgement, and deduplication
- Generic bridge implementation in the private home-server repository

Exit criteria:

- A fresh app installation can pair without manually copying a long secret.
- A command sent while the TV is offline is delivered once after wake and launch.
- Duplicate delivery never starts the same queue twice.
- Revoking a device prevents further commands.
- Logs reveal no pairing or Stash credentials.

## 5. Native queue playback

Deliverables:

- Resolve an ordered list of scene IDs
- Media3 playlist construction
- Start-at-scene support
- Continue, loop, and reshuffle policy
- Audio selection and D-pad player UI
- Queue persistence sufficient for crash recovery
- Playback-state reporting

Exit criteria:

- Selected and filtered Home Stash queues retain their intended first-cycle order.
- Loop and reshuffle behaviour matches the tested Home Stash queue semantics.
- Missing or unplayable scenes are reported and handled predictably.
- The receiver survives network interruption and app recreation without accidental duplicate playback.
- Queue policy has deterministic automated tests.

Implementation note: crash recovery always restores paused. A normal BACK or
task exit clears the recovery record, so recovery cannot create surprise
autoplay.

## 6. Home Stash Send to TV integration

Deliverables:

- Device-management settings
- Single-scene and bulk Send to TV actions
- Device picker
- Delivery acknowledgement and useful errors
- Bridge client isolated from Stash queue logic
- Integration tests with a fake bridge

Exit criteria:

- A user can send the current scene, selected scenes, or filtered queue.
- Multiple paired devices can be distinguished.
- Offline, unpaired, expired, and protocol-incompatible states are clear.
- Existing browser playback and queue behaviour remain unchanged.
- No LAN address, device credential, or bridge secret is committed to the public Stash fork.

## 7. Release hardening

Current status: supervised playback acceptance has passed on the Tesla target
for representative 1080p and 4K media, remote controls, replacement playback,
HOME/reopen, and Android TV Ambient Mode recovery. The signed-release
foundation is tracked in issue #23; this checkpoint remains open until the
production-signed install, upgrade, recovery, and tagged-release criteria pass.

Deliverables:

- Device test matrix
- Receiver, protocol, and queue regression suite
- Crash-safe logging with redaction
- Versioning and migration policy
- Signed release APK workflow
- Installation, update, pairing, recovery, and troubleshooting documentation

Exit criteria:

- A cleanly installed release APK completes the full Send-to-TV flow.
- Upgrade from the previous test build preserves valid profiles and pairing.
- Recovery steps exist for lost pairing, changed server addresses, and revoked credentials.
- CI and all required automated tests pass.
- A tagged MVP release is published.

## Deferred: full TV client

After the receiver MVP is stable, the same app may add:

- Subtitle discovery, selection, and translated-subtitle workflows
- TV-native scene browsing
- Search and saved filters
- Performers and groups
- Continue watching and history
- Favorites
- A richer server switcher
- Recommendations and discovery

These features must build on the receiver architecture and must not delay the MVP.
