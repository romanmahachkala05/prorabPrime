plugins {
    id("prorab.kmp.android.library")
    id("prorab.kmp.compose")
    id("prorab.koin")
}

kotlin {
    android {
        namespace = "ru.prorabprime.core.ui"
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: AppError and the models appear in this module's signatures.
            api(project(":core:domain"))
            // `api`: every screen's ViewModel extends ViewModel and uses launchCatching on it.
            api(libs.androidx.lifecycle.viewmodel)
            // `api`: UiText.Resource holds its format arguments as an ImmutableList.
            api(libs.kotlinx.collections.immutable)
        }

        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
