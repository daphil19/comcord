import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.gradle.versions)
}

// checks to see if a release is "stable," meaning we don't have to worry about rc versions polluting a dependencyUpdates check
// this can be found in the versions plugin readme (https://github.com/ben-manes/gradle-versions-plugin)
fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.named("dependencyUpdates", DependencyUpdatesTask::class.java).configure {
    // disallow release candidates as upgradable versions from stable versions
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }

    // don't look at gradle rc versions either
    gradleReleaseChannel = "current"
}

val assembleDev = tasks.register("assembleDev") {
    group = "build"
    description = "Compiles desktop, Android debug, JS, Wasm, and the iOS simulator debug framework."
    dependsOn(
        ":composeApp:jvmJar",
        ":androidApp:assembleDebug",
        ":composeApp:compileKotlinJs",
        ":composeApp:compileKotlinWasmJs",
        ":composeApp:linkDebugFrameworkIosSimulatorArm64",
    )
}

tasks.register("buildDev") {
    group = "build"
    description = "Assembles development artifacts and runs both modules' checks and tests."
    dependsOn(assembleDev, ":composeApp:check", ":androidApp:check")
}
