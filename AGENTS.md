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

- Everyday checks, tests, and cross-platform compilation: `./gradlew buildDev` (macOS, Xcode, Android SDK)
- Cross-platform compilation only: `./gradlew assembleDev` (same requirements)
- Checks/tests, including compilation of their required inputs: `./gradlew check`
- Desktop compile/package: `./gradlew :composeApp:jvmJar`
- Desktop run: `./gradlew :composeApp:run`
- Android: `./gradlew :androidApp:assembleDebug`
- Web compile: `./gradlew :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs`
- Web run: `./gradlew :composeApp:jsBrowserDevelopmentRun` or `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
- Web lock refresh: `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`
- iOS: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
- Fast shared/store tests on JVM (no Docker): `./gradlew :composeApp:jvmTest`
- Matrix adapter smoke tests: `./gradlew :composeApp:matrixSmokeTest` (Docker required; starts and removes an isolated Synapse container, random port, and fresh database)
- Full platform tests: `./gradlew :composeApp:allTests` (run `:composeApp:matrixSmokeTest` separately for Matrix integration)
- Local homeserver: `docker compose -f docker/matrix-mock/docker-compose.yml up -d`

Choose the narrowest verification that covers the change: relevant tests/checks,
affected-platform compilation, or both when needed. For
documentation or comment-only changes, review the diff and run `git diff --check`;
no Gradle build is required. Broaden verification only when the change warrants it.

For shared code or toolchain changes, use `./gradlew buildDev`. It combines
`assembleDev` with both modules' `check` tasks (including configured tests).
`assembleDev` builds the desktop JAR, Android debug APK, JS and Wasm Kotlin code,
and the Apple Silicon iOS simulator debug framework. Both avoid production web
bundling and iOS release linking and require macOS/Xcode and the Android SDK.
Browser and iOS tests may also need a browser and an available iOS simulator.
These tasks do not launch the app or compile the Swift iOS wrapper. Use
`./gradlew check` for checks/tests alone.

Do not run `./gradlew build` for routine agent verification. It also performs
production web optimization and iOS release linking, which can be very expensive.
Reserve the full build for explicitly requested release validation. If changing
production bundling or packaging, run only the relevant production/package tasks.

For toolchain changes, compile every target and verify desktop and Wasm login/sync
with the local homeserver (`http://localhost:8008`, `alice` / `alicepass`). Report
checks that could not run; compilation alone does not verify login or IDE indexing.

## Scope and style

Work on one GitHub ticket at a time; read it with `gh issue view`. Keep dependency
and tooling work separate from features. Use Kotlin with four-space indentation,
trailing commas, descriptive names, and packages under `dev.phillipslabs.comcord`.
Do not copy source, assets, or exact visual designs from other chat clients.

`:composeApp:assembleDebug` remains a forwarding task for compatibility. Android
release tasks and APK/AAB outputs belong to `androidApp`.

## Shell store and Matrix source

`ShellStore` owns room state and selection for one Account. Its caller owns the
coroutine scope and cancels it when that Account shell leaves. Shell composables
collect `ShellState` and send store intents. `MatrixSource` uses Comcord models
and string IDs; keep SDK types and room observation in `TrixnityMatrixSource`
and `ComcordRoomDirectory`. Startup/account connection may still use SDK view
models (ADR 0002).

Put store behavior tests in `commonTest`, driving `FakeMatrixSource`. Put adapter
integration tests in JVM `*SmokeTest` classes; `matrixSmokeTest` runs these while
`jvmTest` excludes them. Smoke tests own their homeserver and client storage;
they must work without the development Docker Compose stack.
