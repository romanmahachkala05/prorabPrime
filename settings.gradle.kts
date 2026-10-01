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
    // The Kotlin/Wasm toolchain adds its own repositories to every project that targets the browser,
    // which FAIL_ON_PROJECT_REPOS refuses; PREFER_SETTINGS ignores them, so the two it needs (the
    // Node.js, Binaryen and Yarn distributions) are declared here instead.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    name = "Node.js distributions"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("org.nodejs") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/WebAssembly/binaryen/releases/download") {
                    name = "Binaryen distributions"
                    patternLayout { artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("com.github.webassembly") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    name = "Yarn distributions"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("com.yarnpkg") }
        }
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
include(":feature:finance")
include(":feature:materials")
include(":feature:map")
include(":feature:tasks")
include(":web")
