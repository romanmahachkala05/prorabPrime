import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    id("prorab.quality")
}

// The browser entry point only, as `:app` is the Android one: the UI is `:shared`'s.
// `./gradlew :web:wasmJsBrowserDistribution` builds the site into build/dist/wasmJs/productionExecutable.
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "prorab-web"
        browser {
            testTask { enabled = false }
            commonWebpackConfig {
                outputFileName = "prorab-web.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:data"))

            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(project.dependencies.platform(libs.coil.bom))
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
    }
}
