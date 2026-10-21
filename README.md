# Comcord

Comcord is an early-stage Matrix client built with Kotlin Multiplatform and Compose Multiplatform.

## Project vision

Build a single Matrix client codebase that ships to:

- Android
- iOS
- Desktop (JVM)
- Web (JS/IR and Wasm)

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
    - `src/androidMain/`, `src/iosMain/`, `src/jvmMain/`, `src/webMain/`: platform-specific integrations
- `androidApp/`: Android application entry point, manifest, and launcher resources (AGP built-in Kotlin)
- `iosApp/`: iOS app entry point/wrapper for running the shared Compose application on Apple platforms

## Build notes

- Web targets include JS/IR and Wasm; both share `webMain`.
- Use JDK 17, Android SDK 37, and Xcode for the iOS targets.
- AGP 9.3.1 uses the supported Android KMP library plugin in `composeApp` and built-in Kotlin in `androidApp`; no legacy DSL opt-outs. Newer AGP releases are held back to Kotlin 2.4.21's documented compatibility ceiling.
- Gradle 9.7.0 is Kotlin 2.4.21's documented compatibility ceiling; Gradle 9.8.x is held back.
- Ktor 3.5.2 matches Trixnity Messenger 4.10.3; coroutines 1.11.0 and Decompose 3.5.0 match its published dependencies.
- Kotlin wrappers stay on Trixnity's 2026.6.1 baseline: newer wrappers compile but fail Wasm login with an IndexedDB `createIndex` linkage error.
- Compose Material 3 remains on 1.9.0, the latest stable artifact in its own release line.
- Java stays on 17; Android minSdk 28 and targetSdk 36 retain the current device support and runtime behavior. Only compileSdk advances to 37 for dependency compatibility.
- Compatibility bounds: [Kotlin toolchain matrix](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html). Dependency baselines: [Trixnity Messenger 4.10.3 metadata](https://gitlab.com/api/v4/projects/47538655/packages/maven/de/connect2x/trixnity/messenger/trixnity-messenger/4.10.3/trixnity-messenger-4.10.3.module).
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

- Build Android debug app: `./gradlew :androidApp:assembleDebug`
- Build Desktop jar: `./gradlew :composeApp:jvmJar`
- Run Desktop app: `./gradlew :composeApp:run`
- Compile Web (JS): `./gradlew :composeApp:compileKotlinJs`
- Compile Web (Wasm): `./gradlew :composeApp:compileKotlinWasmJs`
- Run Web (JS): `./gradlew :composeApp:jsBrowserDevelopmentRun`
- Run Web (Wasm): `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
- Refresh web locks after dependency changes: `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`
- Build iOS simulator debug framework: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
- Run tests: `./gradlew :composeApp:allTests`

## Local login and sync check

Start the local homeserver, then launch the desktop app or one web target. Set
the homeserver to `http://localhost:8008`, continue with password login, and use
`alice` / `alicepass`. Confirm the app leaves the login screen and shows a running
sync state. Browser builds require a secure context (`localhost` works) for
cryptography and storage.

The old `:composeApp:assembleDebug` command forwards to `:androidApp:assembleDebug`.
Release APKs and bundles are now under `androidApp/build/outputs/`.
