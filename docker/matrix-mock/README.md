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

## Verification expansion

The current mock is enough for login and basic sync checks. As the Phase 1 shell lands, extend this environment to cover:

- spaces and room hierarchy fixtures
- DM scenarios
- invite acceptance
- message send and retry
- reactions and unread transitions

## Reset state

```bash
docker compose -f docker/matrix-mock/docker-compose.yml down
rm -rf docker/matrix-mock/data
```
