plugins {
    id("prorab.kmp.android.library")
    id("prorab.kmp.compose")
    id("prorab.koin")
    // FinanceNavKey is @Serializable, for Navigation 3's saved state.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "ru.prorabprime.feature.finance"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:ui"))
            implementation(project(":core:designsystem"))

            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.kotlinx.collections.immutable)
        }

        jvmTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
