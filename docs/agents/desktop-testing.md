# Development verification

Use this runbook to choose checks, verify dependency updates, and test desktop/web
UI or Messenger workflows. Gradle task definitions are authoritative for task
behavior and options.

## Choose verification scope

Choose the smallest scope that covers the changed behavior. The user's current
verification limit or decision to run checks takes precedence over these defaults.

| Change or requested coverage | Start with | Completion criterion |
| --- | --- | --- |
| Documentation or comments only | Review the diff and run `git diff --check`. | Diff and whitespace checks pass; no Gradle build needed. |
| Comcord policy or JVM/Android host behavior | `./gradlew verifyJvmDev` | Existing static checks and relevant host tests pass. |
| One platform's code or build configuration | The relevant test and compile/link task below. | The affected path passes; report platform/runtime coverage separately. |
| Shared code | `verifyJvmDev`, then compilation/linking for affected targets. | Tests cover the changed policy and affected targets compile/link. Use `buildDev` when all-target coverage is needed. |
| Dependencies or toolchain | Follow the dependency-update sequence below. | Fast checks pass before platform work; record affected targets and any runtime checks left open. |
| Cross-platform checks and tests | `./gradlew buildDev` | Development artifacts and both modules' checks pass. |
| Cross-platform artifacts without the test suite | `./gradlew assembleDev` | Development artifacts and linked Wasm validation pass. |
| Production packaging | The affected production/package task. | The changed package is verified. Reserve `./gradlew build` for explicitly requested full release validation. |

Broaden verification when a failure, a new change, or an uncovered affected path
requires it. Once the selected checks pass, stop unless required coverage remains.
Report checks that could not run rather than treating partial coverage as complete.

### Dependency-update sequence

1. Check published metadata and release requirements for the selected version.
   Distinguish dependencies Comcord uses directly from SDK internals supplied
   transitively. Record the reason for deliberate version overrides.
2. Run `./gradlew verifyJvmDev` before expensive platform linking. This already
   runs ktlint, Detekt, and JVM/Android host tests and compiles JVM test sources,
   including smoke-test sources. Fix failures here before broadening the build.
3. Compile/link the affected platforms. For Wasm, run
   `./gradlew :composeApp:validateWasmDevelopment` to check the linked application;
   `compileKotlinWasmJs` alone only checks Kotlin compilation. Refresh JS/Wasm
   Yarn locks when the web dependency graph changes, then repeat the failed check.
   For dependencies used on every target, cover every target with the platform
   tasks below or `buildDev`, subject to the user's verification limit.
4. For Messenger or web runtime dependency changes, verify startup and relevant
   login/sync behavior on the affected app targets. Use the documented local
   Homeserver (`http://localhost:8008`, `alice` / `alicepass`) for desktop/Wasm
   checks. Existing browser tests use a separate test executable; they do not
   establish that the actual application starts. Record binary validation,
   browser startup, login/sync, and IDE indexing as separate evidence.

CI already runs static checks, host tests, web bundling, browser tests, and Matrix
workflows. Use those existing checks before adding another rule or task. The
current Wasm binary validator does not run the application. An automated check
of actual application startup remains a separate CI improvement; assess its
placement and cost under [the build review policy](build-review.md).

### User handoff

When the user says they will run verification, stop verification processes started
for this task and hand off. List the exact remaining commands, completed results,
and unresolved failures. Leave the user's existing dev servers and apps running.
Resume verification when the user asks or provides a failure to investigate.

### Platform commands and prerequisites

| Purpose | Command |
| --- | --- |
| Desktop compile/package or run | `./gradlew :composeApp:jvmJar` or `./gradlew :composeApp:run` |
| Android debug APK | `./gradlew :androidApp:assembleDebug` |
| JS compilation | `./gradlew :composeApp:compileKotlinJs` |
| Wasm application link and validation | `./gradlew :composeApp:validateWasmDevelopment` |
| Web development server | `./gradlew :composeApp:jsBrowserDevelopmentRun` or `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| Web lock refresh | `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock` |
| iOS simulator framework | `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` |
| Shared/store JVM tests without Docker | `./gradlew :composeApp:jvmTest` |
| Full platform tests | `./gradlew :composeApp:allTests` (Matrix smoke tests remain separate) |
| Local Homeserver | `docker compose -f docker/matrix-mock/docker-compose.yml up -d` |

`assembleDev` builds the desktop JAR, Android debug APK, JS code, the linked and
validated Wasm development application, and the Apple Silicon iOS simulator
debug framework. `buildDev` adds both modules' `check` tasks. These require macOS,
Xcode, and the Android SDK; browser/iOS tests may also need a browser/simulator.
They avoid production web bundling and iOS release linking, do not launch the
application, and do not compile the Swift iOS wrapper. `./gradlew check` runs
checks/tests and their required compilation/linking, including Wasm validation.

## Matrix workflow checks

For real SDK workflows, run Gradle directly from the target checkout:

```sh
./gradlew :composeApp:matrixSmokeTest -PmatrixWorkflowsOnly=true
# Narrow a run while implementing one behavior:
./gradlew :composeApp:matrixSmokeTest -PmatrixWorkflowsOnly=true --tests '*MessengerAuthenticationSmokeTest'
```

Docker is required. Gradle bounds the smoke task to ten minutes; the fixture also
bounds its workflow scenario. CI has a separate job timeout and uploads Gradle's
HTML/XML results from `composeApp/build/reports/tests/matrixSmokeTest/` and
`composeApp/build/test-results/matrixSmokeTest/`.

The fixtures close their disposable clients, containers and temporary storage on
normal completion or failure. A forced process termination can bypass cleanup;
inspect and remove only resources from that test run if a local run is killed.

`-PmatrixWorkflowsOnly=true` includes production `*SmokeTest` classes as they land. Adoption
`*GateSmokeTest` experiments and the opt-in platform host are separate. To run a
particular experiment, omit that flag and select its class, for example:

```sh
./gradlew :composeApp:matrixSmokeTest --tests '*MessengerTimelineGateSmokeTest'
```

With no filters or workflow property, Gradle executes the complete Matrix smoke
task, including experimental fixtures. The [CI workflow](../../.github/workflows/ci.yml)
shows the required production selection.

## Reuse the headless workflow fixture

[MessengerWorkflowFixture](../../composeApp/src/jvmTest/kotlin/dev/phillipslabs/comcord/MessengerWorkflowFixture.kt)
owns a disposable homeserver, HTTP fault proxy, client storage and application
hosts. Use `MessengerWorkflowFixture.run { ... }`, `createHost()` and
`messengerFactory(host)` for real discovery, authentication, restoration and
lifecycle scenarios. The existing authentication smoke test is the working example.

Only Main is replaced by a serialized dispatcher; Messenger clients and workers
keep their production default dispatchers. The fixture restores Main and the
Decompose test setting even after failure. Its secret-provider override belongs
only to disposable tests. Production keeps its platform providers.

This fixture verifies workflow wiring, not native rendering. Compose UI tests
need their own UI clock and dispatcher setup; mixing a Compose rule's Main owner
with the headless fixture recreates the dispatcher stalls seen during #24.

## Choose an interactive target

### Wasm linking and incremental support

Incremental Wasm support is desirable and should be restored when it is reliable.
For now, `gradle.properties` sets `kotlin.incremental.wasm=false` for all Wasm
binaries. With Kotlin 2.4.21 and Messenger 4.11.0, incremental development linking
produced a binary rejected by both Brave and Node with `struct.set` reference-type
mismatches in Trixnity's device-key code. Non-incremental linking
produced a valid binary and restored the existing browser Account to `Synced`.
This is a compiler/linker workaround, not a requirement to keep Messenger on an
older version. The earlier test-only workaround did not protect the application.

`:composeApp:validateWasmDevelopment` links the actual development application
and compiles its Wasm with Kotlin's managed Node runtime without running the app.
It runs with `check`, `assembleDev`/`buildDev`, CI, and development-server startup.
Compilation of Kotlin to a KLIB alone does not detect invalid linked Wasm;
validation does not detect runtime ABI, storage, or login/sync problems.

To evaluate incremental support with a future stable Kotlin compiler, temporarily
override the setting with `-Pkotlin.incremental.wasm=true`. Verify a fresh app
and test link, then repeat after a source edit and a dependency update without
cleaning the linker outputs. Require application validation, Wasm browser tests,
and browser startup/login/sync to pass in both cases. Restore the default only
after those checks pass; keep binary validation enabled to catch regressions.
The override uses Kotlin's [documented Wasm incremental compilation setting](https://kotlinlang.org/docs/wasm-configuration.html#kotlin-wasm-incremental-compilation).

### Interactive verification

1. Check the Compose hot-reload MCP connection first. Confirm the connected app
   belongs to the checkout and revision being tested before using reload or
   interaction tools. If useful hot reload is disconnected, ask the user to start
   it for that checkout; the user offered to do this. Continue independent checks
   while waiting.
2. Keep the project toolchain on Java 17. The hot-reload setup inspected during
   #24 required a separate JetBrains Runtime 21. Treat a missing runtime as a
   tooling prerequisite and use an available fallback for the current check.
3. The normal desktop entrypoint is `./gradlew :composeApp:run`. If native UI
   tools cannot attach to its unbundled JVM, build `:composeApp:createDistributable`
   and attach to the resulting app bundle. On macOS the default output is
   `composeApp/build/compose/binaries/main/app/Comcord.app`; verify that it exists
   and give the UI tool its absolute path.
4. Web development is another useful interactive target; choose the JS or Wasm
   development task from the agent guide. A browser tab's apparent activation may
   not produce real visibility changes in the in-app browser.

If the Mac is locked, request an unlock once and continue headless verification.
Record native observations after access is restored.

## Observe the real UI and host events

- Use the current tool documentation. Refresh the native accessibility tree
  after route changes, window zoom or screenshots before reusing element IDs;
  native IDs can be renumbered. Confirm field selection before replacing text.
- Check discovery error/retry, bad credentials/correction, the synced shell, and
  full close/reopen restoration using the documented development Account or a
  disposable fixture. Keep existing user Accounts and storage intact.
- Exercise actual minimize/background, resume and close events when testing host
  lifecycle. Record the observed sync transition. If the tool cannot expose the
  transition, mark that observation unverified rather than inferring it from
  compilation, a rendered shell or an automated fixture.
- Record revision, target/runtime, commands, visible results and limitations.
  Keep generated screenshots under ignored `build/` directories. Separate build,
  headless-workflow, rendering and native-lifecycle evidence.
- Sign out only sessions created for the check, close test apps/dev servers, and
  remove only owned disposable resources. Leave the user's running homeserver
  and unrelated worktrees/files untouched.
