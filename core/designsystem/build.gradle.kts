plugins {
    id("prorab.kmp.android.library")
    id("prorab.kmp.compose")
}

kotlin {
    android {
        namespace = "ru.prorabprime.core.designsystem"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            // `api`: components take UiText and DialogModel.
            api(project(":core:ui"))
            // `api`: screens use the same icons as the components.
            api(libs.compose.mp.material.icons.core)

            // No network fetcher here: :app registers one over the app's HttpClient (ADR-0003).
            implementation(project.dependencies.platform(libs.coil.bom))
            implementation(libs.coil.compose)
        }

        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
        }
    }
}
