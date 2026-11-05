# Desktop and Messenger workflow verification

Use this runbook for desktop/web UI checks or new Messenger workflow smoke tests.
The [agent guide](../../AGENTS.md) defines required final verification; Gradle task
definitions are authoritative for their options.

## Start with the smallest automated check

Run `./gradlew verifyJvmDev` for static checks and JVM/Android host tests before
expensive platform linking. It reuses the modules' existing checks, including
JVM/Android typed Detekt. Shared/build changes still finish with `buildDev` as
required by the agent guide.

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
