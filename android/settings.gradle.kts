pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GymRat"

// :core mirrors ios/Packages/GymRatKit: API client + models, pure Kotlin/JVM (no Android),
// so it builds and tests anywhere. :app mirrors the iOS app target: Compose UI + wiring.
include(":core")
include(":app")
