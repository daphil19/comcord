# Comcord

Comcord is an early-stage Matrix client built with Kotlin Multiplatform and Compose Multiplatform.

## Project vision

Build a single Matrix client codebase that ships to:

- Android
- iOS
- Desktop (JVM)
- Web (JS/IR)

The goal is to keep as much business logic and UI shared as practical while still allowing platform-specific integrations where needed.

## Current aims

The current phase is focused on a functional login/sync/rooms vertical slice:

- A shared Matrix backbone based on `trixnity-messenger`
- Basic auth flow (homeserver + username/password)
- Initial sync and room directory loading
- Guild-level grouping based on Matrix spaces (`m.space`) with an always-present `All Rooms` pseudo-guild
- Timeline rendering deferred until after room-directory flow is stable

## Project structure

- `composeApp/`
    - `src/commonMain/`: shared UI, app flow, and cross-platform logic
    - `src/androidMain/`, `src/iosMain/`, `src/desktopMain/`, `src/jsMain/`: platform-specific integrations
- `iosApp/`: iOS app entry point/wrapper for running the shared Compose application on Apple platforms

## Build notes

- Web target currently uses JS/IR (not Wasm)
- Homeserver can be customized at login, with `https://matrix.org` prefilled as the default
- During skeleton development, code quality gates (lint/ktlint/detekt) are intentionally disabled and will be re-enabled later

## Local mocked Matrix server

For stable local development against a known Matrix homeserver, this repo includes a dockerized Synapse setup backed by SQLite.

- Stack location: `docker/matrix-mock/`
- Client homeserver URL: `http://localhost:8008`
- Seeded users (username/password):
    - `alice` / `alicepass` (admin)
    - `bob` / `bobpass`
    - `carol` / `carolpass`
    - `dave` / `davepass`
- Matrix IDs: `@alice:localhost`, `@bob:localhost`, `@carol:localhost`, `@dave:localhost`

Start it:

- `docker compose -f docker/matrix-mock/docker-compose.yml up -d`

Stop it:

- `docker compose -f docker/matrix-mock/docker-compose.yml down`

Re-run user seeding against an existing server:

- `docker compose -f docker/matrix-mock/docker-compose.yml run --rm seed-users`

Reset to a clean state (removes DB + media + users):

- `rm -rf docker/matrix-mock/data`

## Useful commands

- Build Android debug app: `./gradlew :composeApp:assembleDebug`
- Build Desktop jar: `./gradlew :composeApp:desktopJar`
- Compile Web target: `./gradlew :composeApp:compileKotlinJs`
- Build iOS simulator debug framework: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
- Run tests: `./gradlew :composeApp:allTests`
