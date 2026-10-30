import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
    filter {
        exclude("**/build/**", "**/generated/**")
    }
}

detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    // Include build-type/flavor and test sources in the syntax-only pass too.
    source.setFrom(layout.projectDirectory.dir("src"))
}

tasks.withType<Detekt>().configureEach {
    exclude("**/build/**", "**/generated/**")
}

tasks.named("check") {
    dependsOn(tasks.named("detektMain"), tasks.named("detektTest"))
}

android {
    namespace = "dev.phillipslabs.comcord"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "dev.phillipslabs.comcord"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(
        libs.versions.jdk
            .get()
            .toInt(),
    )
}

dependencies {
    ktlintRuleset(libs.ktlint.compose)
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.trixnity.messenger)
    debugImplementation(libs.compose.ui.tooling)
}

if (providers.gradleProperty("messengerGate").isPresent) {
    android {
        defaultConfig.applicationId = "dev.phillipslabs.comcord.gate"
        sourceSets.getByName("main") {
            java.srcDir("src/messengerGate/kotlin")
            manifest.srcFile("src/messengerGate/AndroidManifest.xml")
        }
    }
}
