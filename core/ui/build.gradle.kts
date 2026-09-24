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
            implementation(project(":core:domain"))
        }
    }
}
