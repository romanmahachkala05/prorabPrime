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
        }

        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
        }
    }
}
