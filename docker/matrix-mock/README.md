# Matrix mock server

This directory provides a stable local Matrix homeserver for Comcord development.

## What it runs

- Synapse (`elementhq/synapse:v1.116.0`)
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

## Reset state

```bash
docker compose -f docker/matrix-mock/docker-compose.yml down
rm -rf docker/matrix-mock/data
```
