plugins {
    id("prorab.kmp.android.library")
}

kotlin {
    android {
        namespace = "ru.prorabprime.core.testing"
    }

    sourceSets {
        commonMain.dependencies {
            // Fakes implement :core:domain's ports and :core:ui's collaborators, so consumers
            // need those types too.
            api(project(":core:domain"))
            api(project(":core:ui"))
        }
    }
}
