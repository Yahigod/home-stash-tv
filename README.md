# Home Stash TV

Native Android TV receiver for [Stash](https://github.com/stashapp/stash), designed to grow into a full TV client.

> [!IMPORTANT]
> The receiver MVP has passed supervised playback acceptance on the first Tesla
> target, but the first production-signed release has not been published yet.
> Library browsing is intentionally deferred.

## Receiver MVP

The first usable version will:

- run as a native Android TV application;
- receive a scene or ordered queue from the Home Stash integration;
- resolve authenticated media from one or more configured Stash servers;
- play through Android Media3/ExoPlayer;
- support D-pad playback controls, queue continuation, looping, and reshuffling;
- report connection, playback, and failure state to the bridge;
- allow the existing bridge to handle Wake-on-LAN and application launching.

## System boundaries

| Component | Responsibility |
| --- | --- |
| Home Stash TV | Native playback, TV controls, local server profiles, receiver state |
| Home server bridge | Pairing broker, command delivery, Wake-on-LAN, ADB launch |
| Home Stash fork | Send-to-TV action, queue creation, paired-device selection |
| Stash servers | Library, metadata, authentication, and media delivery |

The public app repository must never contain private addresses, device identifiers, Stash credentials, or API keys. Machine-specific deployment remains in the private home-server repository.

## Not in the receiver MVP

- Full library browsing
- Performers, groups, search, or filters
- Recommendations and discovery
- Samsung Tizen or LG webOS clients
- Cloud relay or access outside the local network
- Advanced multi-TV administration

These are future-client concerns and must not block the receiver.

## Planned stack

- Kotlin
- Android TV
- Jetpack Compose for TV
- AndroidX Media3 / ExoPlayer
- Gradle version catalog
- GitHub Actions for build and tests

The receiver uses `minSdk 26`. The first target Tesla TV was verified as
Android 14 / API 34 with an ARMv7 ABI and the standard Android TV feature
flags. See [the compatibility notes](docs/DEVICE_COMPATIBILITY.md).

## Delivery checkpoints

1. Project foundation and CI
2. Single-scene Media3 playback proof
3. Multiple Stash server profiles and secure authentication
4. Pairing and receiver protocol
5. Native queue playback and TV controls
6. Home Stash Send-to-TV integration
7. Resilience, signed APK, and installation documentation

See [the roadmap](docs/ROADMAP.md) and [architecture notes](docs/ARCHITECTURE.md) for scope and acceptance criteria.

## Development signing

CI debug APKs use a persistent development-only signing identity supplied
through repository secrets. This keeps in-place TV upgrades compatible between
checkpoints. The development signer must never be reused for a production
release.

Debug builds also include a one-shot migration entry point for a development
signing reset. It reads only a fixed file in the app's private directory,
deletes that input after one attempt, and never accepts secrets through intent
extras or logs. Release builds do not contain this component.

## Releases

Production releases use a separate owner-held signing identity and are
published only from a version-matching Git tag after tests, lint, signature
verification, and checksum generation pass. See the
[release policy](docs/RELEASES.md) and
[installation and recovery guide](docs/INSTALLATION.md).

## License

No license has been selected yet. Until one is added, normal copyright restrictions apply.
