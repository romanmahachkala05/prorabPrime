plugins {
    id("prorab.kmp.android.library")
    id("prorab.kmp.compose")
    id("prorab.koin")
    // The NavKeys are @Serializable, for Navigation 3's saved state.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "ru.prorabprime.feature.objects"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:ui"))
            implementation(project(":core:designsystem"))

            implementation(libs.androidx.lifecycle.runtime.compose)
            // SavedStateHandle: the edit form's draft survives process death.
            implementation(libs.androidx.lifecycle.viewmodel.savedstate)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.kotlinx.collections.immutable)
            // Upload previews show the picture from the device before it reaches the server.
            implementation(project.dependencies.platform(libs.coil.bom))
            implementation(libs.coil.compose)
        }

        // The camera and the gallery: activity-result launchers and a FileProvider for captures.
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }

        jvmTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
