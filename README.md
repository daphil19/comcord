# Comcord

Comcord is a Kotlin Multiplatform Matrix client built around a mostly shared Compose Multiplatform shell and `trixnity-messenger` as the Matrix backbone.

## Product direction

Comcord is being expanded from a login-and-room-directory skeleton into a usable Matrix client that preserves the current platform reach:

- Android
- iOS
- Desktop (JVM)
- Web (JS/IR)
- Wasm remains experimental, not the primary web target

The implementation strategy is to keep feature state, presentation models, and most UI in `commonMain`, while reserving platform source sets for lifecycle, notifications, storage details, deep links, and media/device integrations.

## Milestones

### Phase 0: Skeleton baseline

- Shared homeserver discovery and password login
- Initial sync and room-directory projection
- Matrix spaces surfaced as high-level grouping

### Phase 1: First usable release

- Shared application shell with responsive three-pane layout
- Room and DM list keyed by stable room IDs
- Timeline loading and message send flow
- Unread state, reactions, logout, and multi-account-ready session state
- Local verification against the included Synapse stack

### Phase 2: Feature depth

- Settings and account/device management
- Media workflows
- Encryption verification UX
- Richer Matrix features such as room creation, joins by alias, and moderation tools

### Later phases

- Threads, pinned content, advanced notification tuning, widgets, and other higher-complexity Matrix features
- VoIP/calling as a separate subsystem with heavier platform-native work

## Implementation boundaries

Comcord targets behavior comparable to established Matrix clients while keeping its implementation, UI structure, and assets owned by this project.

- Allowed inputs: Matrix spec documentation, MSCs, public SDK/API docs, and self-authored notes about observable behavior
- Disallowed inputs: copying source code, assets, exact component trees, or proprietary iconography from upstream clients
- Cinny is a UX reference for information density and shell hierarchy
- Element is a product reference for feature breadth and Matrix compliance

See the project docs for the durable implementation rules and feature matrix.

## Project structure

- `composeApp/`
  - `src/commonMain/`: shared shell, feature stores, design system, and Matrix integration boundary
  - `src/androidMain/`, `src/iosMain/`, `src/jvmMain/`, `src/webMain/`: platform lifecycle and capability adapters
- `docs/`: implementation constraints, architecture decisions, feature planning, and UX targets
- `docker/matrix-mock/`: local Synapse environment used for repeatable verification
- `iosApp/`: iOS wrapper for the shared Compose app

## Verification posture

- JS/IR is the supported web target today
- The default homeserver remains `https://matrix.org`, but the field is user-editable
- Quality gates are still being reintroduced gradually while the monolith is split into testable modules
- Shared verification should cover login, sync, spaces, DMs, timeline send flows, and compact/wide shell behavior before Phase 1 is considered complete

## Local mocked Matrix server

For stable local development against a known Matrix homeserver, this repo includes a dockerized Synapse setup backed by SQLite.

- Stack location: `docker/matrix-mock/`
- Client homeserver URL: `http://localhost:8008`
- Seeded users:
  - `alice` / `alicepass` (admin)
  - `bob` / `bobpass`
  - `carol` / `carolpass`
  - `dave` / `davepass`
- Matrix IDs:
  - `@alice:localhost`
  - `@bob:localhost`
  - `@carol:localhost`
  - `@dave:localhost`

Start it:

- `docker compose -f docker/matrix-mock/docker-compose.yml up -d`

Stop it:

- `docker compose -f docker/matrix-mock/docker-compose.yml down`

Re-run user seeding:

- `docker compose -f docker/matrix-mock/docker-compose.yml run --rm seed-users`

Reset to a clean state:

- `rm -rf docker/matrix-mock/data`

## Useful commands

- `./gradlew :composeApp:allTests`
- `./gradlew :composeApp:assembleDebug`
- `./gradlew :composeApp:jvmJar`
- `./gradlew :composeApp:compileKotlinJs`
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
