plugins {
    id("prorab.kmp.android.library")
    id("prorab.kmp.compose")
    id("prorab.koin")
}

// The whole UI above the screens: navigation between them and the Koin module list.
kotlin {
    android {
        namespace = "ru.prorabprime.shared"
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: :app starts Koin with the module list and builds Coil over the HttpClient.
            api(project(":core:data"))
            implementation(project(":core:ui"))
            implementation(project(":core:designsystem"))
            implementation(project(":feature:objects"))
            implementation(project(":feature:settings"))

            implementation(libs.koin.compose)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.navigation3)
            implementation(libs.kotlinx.serialization.core)
        }

        jvmTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.ktor.client.mock)
        }
    }
}
