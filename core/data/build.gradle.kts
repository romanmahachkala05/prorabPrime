plugins {
    id("prorab.kmp.android.library")
    id("prorab.koin")
}

kotlin {
    android {
        namespace = "ru.prorabprime.core.data"
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: the ports and use cases this module implements and provides are its surface.
            api(project(":core:domain"))
            implementation(project(":api-contract"))

            // `api`: :app builds Coil's loader over the HttpClient this module provides (ADR-0003).
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }

        // OkHttp and DataStore exist only on the two JVM-based targets; wasmJs gets its own in stage 3.
        named("jvmAndAndroidMain").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.datastore.preferences.core)
        }

        // The browser: fetch for HTTP; settings, images and the like are the browser's own APIs.
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        // The reminders: alarm clock, notifications and a place to remember them across a reboot.
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }

        jvmTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
