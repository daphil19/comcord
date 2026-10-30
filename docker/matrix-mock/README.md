# Matrix mock server

This directory provides a stable local Matrix homeserver for Comcord development.

## What it runs

- Synapse (`ghcr.io/element-hq/synapse:v1.149.1`)
- SQLite database at `./data/homeserver.db`
- Seeded local users for username/password login

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

## Re-seed users

```bash
docker compose -f docker/matrix-mock/docker-compose.yml run --rm seed-users
```

## Reset state

```bash
docker compose -f docker/matrix-mock/docker-compose.yml down
rm -rf docker/matrix-mock/data
```
