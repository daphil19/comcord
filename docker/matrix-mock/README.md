# Matrix mock server

This directory provides a stable local Matrix homeserver for Comcord development.

## What it runs

- Synapse (`ghcr.io/element-hq/synapse:v1.149.1`)
- SQLite database at `./data/homeserver.db`
- Seeded local users for username/password login
- Seeded Channels and message history for Alice

## Start

```bash
docker compose -f docker/matrix-mock/docker-compose.yml up -d
```

Homeserver URL: `http://localhost:8008`

For the ordinary Android debug app, use `http://10.0.2.2:8008` in the Homeserver
field. The emulator maps `10.0.2.2` to the development machine. Alternatively,
run `adb reverse tcp:8008 tcp:8008` and use `http://localhost:8008`.

Debug builds permit HTTP only to `10.0.2.2`, `localhost`, and `127.0.0.1` through
`androidApp/src/debug/res/xml/debug_network_security_config.xml`. Release builds
do not include this configuration. Rebuild and reinstall the debug app after
changing network security configuration; no emulator proxy setting is required.

## Seeded users

- `alice` / `alicepass` (admin)
- `bob` / `bobpass`
- `carol` / `carolpass`
- `dave` / `davepass`

Matrix IDs:

- `@alice:localhost`
- `@bob:localhost`
- `@carol:localhost`
- `@dave:localhost`

## Timeline fixtures

Sign in as `alice` and open **All Rooms** to find:

- **Timeline History**: 80 numbered messages alternating between Alice and Bob for
  scrolling, history pagination and sender presentation.
- **Timeline Switching**: 8 messages alternating between Alice and Bob for checking
  Channel switching.
- **Timeline Empty**: no messages, for checking the empty state.

Messages include Unicode, multiple lines and long text for narrow-screen wrapping.
Alice and Bob take turns with paired questions and replies.
These are unencrypted reading fixtures sent as Alice and Bob; they do not exercise
DMs, uploaded avatars, encryption or continuous live incoming messages. Bob joins
both populated Channels; only Alice joins Timeline Empty.

Startup seeds these fixtures after creating users. Rerunning the seeder reuses
the Channels and skips existing fixture messages, preserving user-added messages.
If Alice or Bob has left a fixture Channel, reseeding joins them again. Run only one
seeder at a time.

The first seed can take several minutes because it respects Synapse's message
rate limits. Later runs skip messages already present.

The interleaved fixtures use versioned aliases (`comcord-seed-history-v2` and
`comcord-seed-switching-v2`). When upgrading from the original single-sender
fixtures, Alice and Bob leave the old Channels only if every message is recognized
seed data. Old Channels containing user-added messages remain joined; no history
is deleted. Timeline Empty is reused.

## Re-seed users and timeline data

```bash
docker compose -f docker/matrix-mock/docker-compose.yml run --rm seed-users
```

## Reset state

```bash
docker compose -f docker/matrix-mock/docker-compose.yml down
rm -rf docker/matrix-mock/data
```
