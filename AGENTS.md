# Comcord agent guide

Comcord is a Kotlin Multiplatform / Compose Multiplatform Matrix client. Keep
shared code in `composeApp/src/commonMain`; platform entry points live in
`iosMain`, `jvmMain`, and `webMain` (JS and Wasm). The Android app
entry point, manifest, and launcher resources live in `androidApp/src/main`.

## Toolchain

Use Java 17, Android SDK 37, and Xcode for iOS. Versions live in
`gradle/libs.versions.toml`; keep the Ktor catalog in `settings.gradle.kts` aligned.
AGP 9 uses the Android KMP library plugin in `composeApp` and built-in Kotlin in
`androidApp`. Do not add AGP legacy Kotlin or DSL opt-outs.

## Verification

Before choosing checks, updating dependencies/tooling, or verifying desktop/web
UI and Messenger workflows, read the [testing runbook](docs/agents/desktop-testing.md).
It owns the verification decision table, commands, and user-handoff procedure.
Use the smallest scope that covers the change and the user's current request.

When diagnosing a failure, distinguish evidence from the failing environment,
local reproductions, and remaining hypotheses before adding a permanent
workaround. Report a fix as verified only for the environments actually checked.

## Scope and style

When reviewing changes to build tasks or default verification commands, read
[the build review policy](docs/agents/build-review.md).

Work on one GitHub ticket at a time; read it with `gh issue view`. Keep dependency
and tooling work separate from features. Use Kotlin with four-space indentation,
trailing commas, descriptive names, and packages under `dev.phillipslabs.comcord`.
Do not copy source, assets, or exact visual designs from other chat clients.

For multi-ticket requests, bring each completed ticket through relevant checks
and review into a reviewable commit or PR update as it finishes. Publish within
the user's authorization. Before starting another ticket, assess the remaining
time and active work; when time is tight, prioritize finishing and delivering
active tickets. Keep unfinished work separate and record its branch and blocker
at handoff. Apply this delivery cadence when using `implement-spec` as well.

`:composeApp:assembleDebug` remains a forwarding task for compatibility. Android
release tasks and APK/AAB outputs belong to `androidApp`.

## Matrix workflows and account state

Comcord is powered by [Trixnity Messenger](https://gitlab.com/connect2x/trixnity-messenger/trixnity-messenger/).
For questions about Messenger setup, APIs, or workflows, read its
[upstream README](https://gitlab.com/connect2x/trixnity-messenger/trixnity-messenger/#readme)
and follow the relevant documentation links. Check examples against the Messenger
version declared in `gradle/libs.versions.toml`.

For timeline, composer, authentication, verification, recovery, or account
lifecycle work, read [ADR 0005](docs/adr/0005-messenger-first-matrix-workflows.md).
It supersedes ADR 0002: prefer Messenger workflows and allow feature composables
to consume their view models directly. Preserve Comcord's visible vocabulary,
community projections, navigation, and explicit product requirements. Use
Messenger behavior where Comcord has no explicit requirement.

Broader VM adoption requires the ADR's timeline integration gate. The current
code still uses Comcord-owned stores and source interfaces; the ADR records the
intended direction, not a completed migration. Account-level state owns selection
and retained navigation; room-scoped Messenger VMs should own live timeline
behavior. Bind one selection authority and explicit lifecycle; keep drafts and
navigation independently of live timeline collectors.

Keep common tests with fakes for Comcord-owned policy and projections. Verify
Messenger workflow wiring and lifecycle through integration scenarios. Existing
store tests use `FakeMatrixSource`. Put JVM adapter integration tests in
`*SmokeTest` classes; `matrixSmokeTest` runs these while `jvmTest` excludes them.
Smoke tests own their homeserver and client storage and work without the
development Docker Compose stack.
