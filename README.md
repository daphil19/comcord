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
- Messenger-owned Channel and DM timelines with older history and incoming messages
  (see [implementation and verification](docs/implementation/6-channel-timeline.md))

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
- `terser-webpack-plugin` is an explicit development dependency because `composeApp/webpack.config.d/trixnity.js` imports it for custom production minification. The webpack upgrade stopped supplying it transitively under that name. It can be removed together with the custom minifier configuration if webpack's default minification produces working JS/Wasm bundles with acceptable build times; the current tuning disables expensive compression passes.
- Compose Material 3 remains on 1.9.0, the latest stable artifact in its own release line.
- Java stays on 17; Android minSdk 28 and targetSdk 36 retain the current device support and runtime behavior. Only compileSdk advances to 37 for dependency compatibility.
- Compatibility bounds: [Kotlin toolchain matrix](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html). Dependency baselines: [Trixnity Messenger 4.10.3 metadata](https://gitlab.com/api/v4/projects/47538655/packages/maven/de/connect2x/trixnity/messenger/trixnity-messenger/4.10.3/trixnity-messenger-4.10.3.module).
- Homeserver can be customized at login, with `https://matrix.org` prefilled as the default
- ktlint and Detekt run as part of `check` and `buildDev`; see the code quality notes below.

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

- Everyday checks, tests, and cross-platform compilation: `./gradlew buildDev` (macOS, Xcode, Android SDK)
- Cross-platform compilation only: `./gradlew assembleDev` (same requirements)
- Checks/tests (compiles required inputs): `./gradlew check`
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

For everyday checks, tests, and cross-platform compilation, use this on macOS
with Xcode and the Android SDK:

```bash
./gradlew buildDev
```

`assembleDev` builds the desktop JAR, Android debug APK, JS and Wasm Kotlin code,
and the Apple Silicon iOS simulator debug framework. `buildDev` combines
`assembleDev` with both modules' `check` tasks (including their configured tests).
Both skip production web bundling and iOS release linking. Use `./gradlew check`
to run just checks and tests with their required compilation. Browser and iOS
tests may require a browser and an available iOS simulator once test sources are
added. These tasks do not launch the app or compile the Swift iOS wrapper; use
Xcode to verify the complete iOS app.

Use targeted tests/checks and affected-platform compilation for normal development.
`./gradlew build` is optional full release validation: it also bundles production
JS/Wasm and links release iOS frameworks, and can take substantially longer. For
production bundling changes, run the affected production webpack task directly.
`buildDev` includes the account-store tests. Run `:composeApp:matrixSmokeTest`
separately for the isolated Synapse integration test (Docker required). Verify
interactive login and sync using the steps below.

## Code quality

- `./gradlew ktlintCheck detekt`: formatting, Compose rules, and syntax-based analysis across all source sets.
- `./gradlew ktlintFormat`: apply automatic formatting fixes (some Compose findings require manual changes).
- `./gradlew check`: all quality gates and configured tests, including Detekt type resolution for desktop JVM and Android.
- `./gradlew buildDev`: the same checks plus development compilation for every platform.

ktlint also checks the root and module Kotlin Gradle scripts. `.editorconfig` uses
`ktlint_official` for shared, iOS, desktop, web, and build scripts, and
`android_studio` for `androidApp/src` and Android source sets in `composeApp`.
Compose rules run on both modules, including shared Compose UI. Generated and
build output sources are excluded.

Detekt uses `config/detekt/detekt.yml` on top of its default rules, with strict
configuration validation and Compose conventions. The plugin is pinned to
`2.0.0-alpha.6` because Detekt 1.23.8 predates this project's Kotlin 2.x/AGP 9
setup; Detekt 2 supports built-in Android Kotlin without legacy opt-outs.
See the [Detekt migration notes](https://detekt.dev/changelog-2.0.0/).
Android variants use their compilation classpaths; KMP JVM/Android compilations
get type-aware analysis too. iOS, JS, and Wasm receive syntax-based analysis;
Detekt does not provide native or web type resolution through these Gradle tasks.

Reports are under each module's `build/reports/ktlint/` and
`build/reports/detekt/` (root script reports are under `build/reports/ktlint/`).
Findings fail the checks; no baseline is used. Android Lint is configured
separately in `androidApp/build.gradle.kts`.

## Local login and sync check

Start the local homeserver, then launch the desktop app or one web target. Set
the homeserver to `http://localhost:8008`, continue with password login, and use
`alice` / `alicepass`. Confirm the app leaves the login screen and shows a running
sync state. Browser builds require a secure context (`localhost` works) for
cryptography and storage.

The old `:composeApp:assembleDebug` command forwards to `:androidApp:assembleDebug`.
Release APKs and bundles are now under `androidApp/build/outputs/`.
