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
            implementation(project(":core:ui"))
        }
    }
}
