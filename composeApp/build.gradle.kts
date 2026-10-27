import dev.detekt.gradle.Detekt
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude("**/build/**", "**/generated/**")
    }
}

dependencies {
    ktlintRuleset(libs.ktlint.compose)
}

detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    // The default Detekt source roots omit KMP, including iOS and web sources.
    source.setFrom(provider { kotlin.sourceSets.map { it.kotlin.sourceDirectories } })
}

tasks.withType<Detekt>().configureEach {
    exclude("**/build/**", "**/generated/**")
}

tasks.named("check") {
    // KMP type-resolution tasks are available for JVM/Android, but aren't wired to check.
    dependsOn(tasks.withType<Detekt>())
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    // toolchains are better than setting source/target compatibilities
    jvm()
    jvmToolchain(
        libs.versions.jdk
            .get()
            .toInt(),
    )

    android {
        namespace = "dev.phillipslabs.comcord.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        androidResources.enable = true
        withHostTestBuilder {}
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        // we aren't using an intel mac, so no need for the iosx64 target for now
//        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        // TODO build types?
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    js {
        browser()
        binaries.executable()
        useEsModules()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.trixnity.messenger)
            implementation(libs.decompose.extensions)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.uiTestJUnit4)
            implementation(libs.kotlin.test.junit)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.swing)
        }
        webMain.dependencies {
            // webpack.config.d/trixnity.js explicitly imports this custom production minifier.
            // Keep it declared directly; remove it with that configuration if default
            // minification is verified to produce working bundles in acceptable build time.
            implementation(devNpm("terser-webpack-plugin", libs.versions.terserWebpackPlugin.get()))
//            implementation(npm("copy-webpack-plugin", libs.versions.copyWebpackPlugin.get()))
            implementation(project.dependencies.platform(libs.kotlin.wrappers.bom))
            implementation(libs.kotlin.browser)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.phillipslabs.comcord.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Comcord"
            packageVersion = "1.0.0"
        }
    }
}

// Keep the ticket's existing debug-build command working after the AGP 9 split.
tasks.register("assembleDebug") {
    group = "build"
    dependsOn(":androidApp:assembleDebug")
}

// Container smoke tests are explicit so fast JVM/common tests work without Docker.
val jvmTest =
    tasks.named<Test>("jvmTest") {
        exclude("**/*SmokeTest.class")
    }
tasks.register<Test>("matrixSmokeTest") {
    group = "verification"
    description = "Runs the Trixnity adapter against a test-managed Synapse container (requires Docker)."
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    include("**/*SmokeTest.class")
    outputs.upToDateWhen { false }
}

// Kotlin 2.4.21's incremental Wasm test linker emits invalid GC types in the
// Trixnity/test dependency graph, preventing Karma from discovering any tests.
tasks.named<KotlinJsIrLink>("compileTestDevelopmentExecutableKotlinWasmJs") {
    incrementalWasm = false
}
