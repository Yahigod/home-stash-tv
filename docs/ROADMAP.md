# Home Stash TV Roadmap

The receiver MVP is complete when a user can select a scene or queue in Home Stash, choose a paired Android TV, and have the native app reliably begin and continue playback without TV Bro.

Each checkpoint must leave the system testable and usable. Work does not advance past a device-dependent checkpoint until it passes on the target Tesla TV.

Stable `v0.1.0` completed the receiver MVP. Post-MVP work is tracked separately below and does not retroactively expand the completed MVP checkpoints.

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

Current status: complete. The production-signed release-candidate line passed
the supervised clean-install, in-place upgrade, profile and pairing
preservation, offline delivery, queue, remote-control, HOME/reopen, Android TV
Ambient Mode, and TV-off wake/foreground acceptance gates on the Tesla target.
The stable [v0.1.0 release](https://github.com/Yahigod/home-stash-tv/releases/tag/v0.1.0)
contains the accepted rc.4 runtime unchanged except for release metadata and
was published from reviewed main after exact-head and exact-main CI passed.

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

# Post-MVP roadmap

## 8. Complete playback experience — target v0.2.0

Tracking issue: #37.

The first post-MVP release keeps the accepted receiver architecture and Send-to-TV semantics while completing the playback experience around two deliberately separate overlays.

### Navigational UI

The navigational overlay is focusable and remote-driven. It appears on expected navigation or playback input and automatically hides after inactivity during uninterrupted playback.

Deliverables:

- visible playback timeline with current position and duration
- play/pause
- seek backward and forward
- previous and next scene
- current queue
- clear current-scene indication
- direct jump to another queue item
- predictable Back/Exit behavior

The navigational UI is for control and movement. It should remain compact enough that the video is still the primary content, and normal Send-to-TV commands must continue to start or replace playback exactly as accepted in `v0.1.0`.

### Informational UI

The informational overlay is a separate persistent scene-information mode controlled by the remote Info button.

Behavior:

- first Info press shows the overlay
- the overlay remains visible indefinitely while playback continues
- second Info press hides the overlay
- the overlay is primarily passive/read-only rather than a second navigation surface
- its background must remain transparent/translucent so the playing scene stays visible behind it; it must not become an opaque full-screen panel

Available scene metadata may include:

- scene title
- linked movie/group title, when present
- linked movie/group cover art, when available
- performer names
- performer thumbnails, when available
- performer age at filming only
- tags
- studio
- scene date

Performer age means age on the scene date, never current age. Calculate and display it only when both the performer birth date and the scene date exist. If either value is missing, omit the age rather than estimate it or substitute current age.

Presentation rules:

- movie cover art stays compact and does not dominate the video
- performer thumbnails stay compact and TV-readable
- long performer/tag sets are capped or compacted, with an indicator such as `+N more` where useful
- unavailable sections are hidden rather than represented by empty placeholders
- both playback overlays preserve scene visibility wherever practical

Exit criteria:

- both overlays are fully legible and usable at normal TV viewing distance
- the navigational overlay auto-hides and restores predictably with the Tesla remote
- queue UI shows the active scene and supports previous/next and direct queue-item selection
- Info toggles the informational overlay without stopping playback
- the informational overlay stays visible until Info is pressed again
- the informational overlay remains transparent/translucent enough that the scene is still visible
- movie/group title and cover render when available and disappear cleanly when absent
- performer thumbnails render when available
- performer ages are shown only as age at filming and omitted when required dates are unavailable
- tags, studio, and scene date render cleanly with compact overflow handling
- existing single-scene, ordered-queue, filtered-queue, loop/reshuffle, resume, replacement, bridge acknowledgement, TV-off wake, and Ambient Mode behavior do not regress
- representative 1080p and high-bitrate 4K playback pass physical Tesla TV checks

## 9. TV-native scene browsing — target v0.3.0

Tracking issue: #38.

Planned capabilities:

- Android TV scene grid
- search
- saved filters and useful filter controls
- sort controls
- richer server switching between configured Stash profiles
- direct playback from the TV
- select/build a queue from the TV
- random/shuffle entry points where they fit cleanly

Architecture intent: TV-native browsing queries the configured Stash server directly. The home-server bridge remains responsible for external Send-to-TV delivery, wake/launch orchestration, pairing, and command delivery rather than becoming the backend for the TV browsing interface.

## 10. Rich library experience — target v0.4.0

Tracking issue: #38.

Planned capabilities:

- performer browsing and performer-to-scenes views
- movie/group browsing and group-to-scenes views
- favorites
- history
- continue watching
- richer scene, performer, and movie/group detail pages

## Later candidates

After the core library experience is solid, consider:

- recommendations and discovery
- subtitle discovery, selection, and translated-subtitle workflows
- richer server administration
- additional playback and library polish
- broader platform support only if it becomes worthwhile

All post-MVP features must build on the accepted receiver architecture rather than destabilize the completed `v0.1.0` Send-to-TV path.