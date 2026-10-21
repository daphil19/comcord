rootProject.name = "Comcord"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // trixnity-messenger prod
        maven("https://gitlab.com/api/v4/projects/47538655/packages/maven")
        // trixnity-messenger dev builds
        maven("https://gitlab.com/api/v4/projects/26519650/packages/maven")
    }
    versionCatalogs {
        create("ktor") {
            from("io.ktor:ktor-version-catalog:3.5.2")
        }
    }
}

include(":composeApp", ":androidApp")
