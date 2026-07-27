# Home Stash TV receiver protocol v1

## Scope

Version 1 pairs a television with a LAN-only bridge and delivers commands
reliably after the television wakes. The bridge never receives Stash
credentials or media URLs.

All HTTP and WebSocket messages use UTF-8 JSON, set `v` to `1`, and reject
unknown protocol versions. Timestamps are Unix epoch milliseconds.

## Pairing

1. The app sends `POST /api/v1/pairings` with:

   ```json
   {"v":1,"device_name":"Living Room TV"}
   ```

2. The bridge returns `201` with an opaque `pairing_id`, a six-digit `code`,
   and `expires_at_ms`. The code is shown on the TV and is never logged.
3. The owner approves the displayed code locally on the bridge host.
4. The app polls `GET /api/v1/pairings/{pairing_id}`.
5. While waiting, the bridge returns `200` with `status: "pending"`. Once
   approved it returns the `receiver_id`, a random `receiver_token`, and
   `status: "approved"` exactly once.
6. The app encrypts the token with Android Keystore. Expired, consumed, or
   unknown pairing requests return a non-secret error.

Pairing codes expire after five minutes, are single-use, and are stored only
as keyed hashes by the bridge. Receiver tokens are random 256-bit values and
are stored only as hashes by the bridge.

## Receiver connection

The app opens:

```text
GET /api/v1/receivers/connect
Upgrade: websocket
Authorization: Bearer <receiver_token>
```

After the WebSocket opens, the app sends:

```json
{
  "v": 1,
  "type": "hello",
  "receiver_id": "uuid",
  "app_version": "0.1.0-dev",
  "profiles": [
    {"id": "stable-profile-uuid", "name": "Normal Stash"}
  ]
}
```

Authentication failure, receiver revocation, or a mismatched `receiver_id`
closes the connection. Reconnect delay starts at one second, doubles to a
maximum of one minute, and resets after a successful connection. Only one
reconnect attempt may be scheduled at a time.

The profile manifest contains only stable IDs and display names. Server
addresses and Stash credentials are never sent to the bridge.

## Command

```json
{
  "v": 1,
  "type": "command",
  "id": "uuid",
  "receiver_id": "uuid",
  "created_at_ms": 1785146400000,
  "expires_at_ms": 1785147000000,
  "command": {
    "type": "play_queue",
    "profile_id": "stable-profile-uuid",
    "scene_ids": ["42", "43"],
    "start_index": 0,
    "start_position_ms": 0,
    "policy": {
      "continue": true,
      "loop": false,
      "reshuffle": false
    }
  }
}
```

`scene_ids` contains 1–500 positive decimal IDs. `start_index` must address an
item in the list. Version 1 supports only `play_queue`.

## Acknowledgement and exactly-once start

The receiver responds:

```json
{
  "v": 1,
  "type": "ack",
  "command_id": "uuid",
  "status": "accepted",
  "at_ms": 1785146401000
}
```

Statuses are `accepted`, `duplicate`, `expired`, or `rejected`. A rejected
acknowledgement includes one stable `error_code`, such as
`profile_missing`, `invalid_command`, or `receiver_mismatch`.

Before starting playback, the app atomically records the command ID in its
bounded persistent ledger. A repeated ID is acknowledged as `duplicate` and
is not executed again. The bridge retains pending commands until a terminal
acknowledgement, expires them before delivery, and may redeliver an unacked
command after reconnection. This is at-least-once delivery with exactly-once
command start at the receiver.

## Playback state

After accepting a command, the receiver sends credential-free state updates:

```json
{
  "v": 1,
  "type": "playback_state",
  "command_id": "uuid",
  "state": "playing",
  "at_ms": 1785146405000,
  "scene_id": "43",
  "queue_index": 1,
  "position_ms": 2500,
  "skipped_scene_ids": ["42"]
}
```

States are `resolving`, `playing`, `paused`, `stopped`, `completed`, and
`failed`. `error_code` is optional and contains only a stable non-secret code.
The report never contains a server address, media URL, API key, pairing token,
or receiver token. The bridge accepts state only for a command belonging to
the authenticated receiver and exposes the latest report to authenticated
senders.

## Queue policy and recovery

The first playback cycle retains the sender's scene order and starts at
`start_index` and `start_position_ms`. When `continue` is false, only the
starting scene is played. `loop` starts another cycle after the final playable
scene. `reshuffle` changes each later cycle while avoiding both an immediate
boundary repeat and, for queues of three or more scenes, an identical cycle.
Two-scene queues retain their order because reversing them would repeat the
previous final scene.

Missing scenes and sources are skipped in sender order and reported. Runtime
format failures skip only the failed item; network interruption retries the
same item without advancing the queue. The current queue and position are
stored locally without credentials. Process recovery reconstructs the queue
paused, while BACK/normal exit clears recovery state so playback cannot start
unexpectedly later.

## Revocation and logging

Revocation marks the receiver disabled, closes its active socket, and rejects
future authentication. Pairing codes, receiver tokens, authorization headers,
Stash credentials, and media URLs must never be logged.
