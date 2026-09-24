pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
plugins {
    // Auto-provisions a JDK 17 toolchain for compilation/tests so the build does
    // not depend on which JDK happens to run the Gradle daemon.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ProrabPrime"
include(":app")
include(":shared")
include(":server")
include(":api-contract")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:designsystem")
include(":core:testing")
include(":feature:objects")
include(":feature:settings")
