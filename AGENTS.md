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

- Checks/tests, including compilation of their required inputs: `./gradlew check`
- Desktop compile/package: `./gradlew :composeApp:jvmJar`
- Desktop run: `./gradlew :composeApp:run`
- Android: `./gradlew :androidApp:assembleDebug`
- Web compile: `./gradlew :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs`
- Web run: `./gradlew :composeApp:jsBrowserDevelopmentRun` or `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
- Web lock refresh: `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`
- iOS: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
- Fast tests: `./gradlew :composeApp:jvmTest`
- Full tests: `./gradlew :composeApp:allTests`
- Local homeserver: `docker compose -f docker/matrix-mock/docker-compose.yml up -d`

Choose the narrowest verification that covers the change: relevant tests/checks,
affected-platform compilation, or both when needed. For
documentation or comment-only changes, review the diff and run `git diff --check`;
no Gradle build is required. Broaden verification only when the change warrants it.

For shared code or toolchain changes, use this cross-platform compilation check
(requires macOS/Xcode and Android SDK):

```bash
./gradlew \
  :composeApp:jvmJar \
  :androidApp:assembleDebug \
  :composeApp:compileKotlinJs \
  :composeApp:compileKotlinWasmJs \
  :composeApp:linkDebugFrameworkIosSimulatorArm64
```

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
