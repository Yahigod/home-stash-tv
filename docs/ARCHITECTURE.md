# Architecture

## Design goal

Home Stash TV is a native playback endpoint, not a second Stash server. Stash remains the source of truth for libraries and metadata, while the TV app owns playback and TV-native interaction.

## High-level flow

1. Home Stash creates an ordered queue of scene IDs.
2. The Home Stash integration sends a versioned command to the home-server bridge.
3. If necessary, the bridge wakes the display and launches Home Stash TV.
4. The TV app establishes an outbound receiver connection to the bridge.
5. The bridge delivers the pending command.
6. The app uses its matching local Stash server profile to resolve metadata and media.
7. Media3 plays the queue and the app reports state to the bridge.

An outbound connection from the app avoids exposing a listener on the television and lets commands wait safely while the TV is asleep.

## Component ownership

### Home Stash TV

- Store named Stash server profiles.
- Protect credentials with Android Keystore-backed storage.
- Pair with a bridge and maintain the receiver connection.
- Validate and acknowledge versioned commands.
- Resolve scene metadata and playable sources from Stash.
- Own the Media3 player, queue, audio selection, and D-pad controls.
- Report coarse playback state and actionable errors.

### Home-server bridge

- Maintain the local device registry and pairing state.
- Accept commands from trusted Home Stash instances.
- Queue commands while a receiver is offline.
- Deduplicate commands and expire stale commands.
- Wake the display and launch the app using host-specific automation.
- Relay state without receiving or proxying Stash credentials.

### Home Stash fork

- Expose paired TV devices in settings.
- Add a Send to TV action for one scene or the current selected/filtered queue.
- Preserve Home Stash queue order and policy.
- Show delivery acknowledgement and useful failure messages.
- Never embed television or LAN-specific automation in core queue code.

### Stash server

- Authenticate API requests.
- Return scene metadata and stream/subtitle information.
- Serve media directly to the TV app.

## Receiver protocol

The wire format will be versioned before implementation. The initial command envelope should contain:

- protocol version;
- unique command ID;
- target device ID;
- source server profile ID;
- ordered scene IDs;
- starting scene and optional position;
- playback policy: continue, loop, and reshuffle;
- creation and expiry timestamps.

Credentials and raw API keys must never appear in receiver commands.

The receiver must acknowledge at least these states:

- accepted;
- rejected with reason;
- resolving;
- playing;
- completed;
- failed with an actionable error code.

The bridge transport is expected to use a persistent WebSocket for receiver delivery plus a small HTTP API for pairing and sender commands. This remains a design hypothesis until the protocol spike proves reconnection and offline delivery on the actual television.

## Security boundaries

- Local-network-only operation for the MVP.
- Pairing requires a short-lived, one-time code shown on the TV.
- Long-lived secrets are random, revocable, and stored outside source control.
- Stash credentials stay on the TV and are encrypted at rest.
- Logs redact credentials, authorization headers, media URLs containing tokens, and pairing secrets.
- The bridge does not proxy media.
- Release signing material is never committed.

## Failure behaviour

The receiver must fail visibly and recoverably when:

- the bridge is unreachable;
- the selected Stash profile is missing;
- authentication is rejected;
- a scene or media source no longer exists;
- a codec is unsupported;
- the app is killed or the TV sleeps mid-queue;
- the same command is delivered more than once.

The last accepted queue and playback position may be persisted locally, but automatic resume must not unexpectedly start playback after a normal manual exit.

## Repository boundaries

This repository contains the Android application, protocol specification, tests, CI, and generic setup documentation. Private host automation and real device configuration belong in the private home-server repository. Home Stash web-interface changes belong in the Stash fork.

## Open decisions for the first spikes

- Compose for TV compatibility on the device
- Supported video/audio matrix
- Exact Stash stream-resolution API calls
- WebSocket library and reconnection policy
- Whether bridge discovery uses manual address entry, mDNS, or both

## Resolved foundation decisions

- `minSdk 26`; the first Tesla target is Android 14 / API 34.
- The application is TV-only and requires Leanback and television device
  features while explicitly not requiring a touchscreen.
- The first app process contains an empty Media3 session service, but playback
  starts only after a later checkpoint supplies media.
- Cleartext transport remains enabled because the receiver MVP must support
  user-configured HTTP Stash servers on the local network. Credentials and
  server addresses are never compiled into the APK.
