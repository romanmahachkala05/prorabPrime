plugins {
    id("prorab.kmp.android.library")
    id("prorab.kmp.compose")
    id("prorab.koin")
}

kotlin {
    android {
        namespace = "ru.prorabprime.shared"
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: :app starts Koin with the module list, whose elements come from :core:data.
            api(project(":core:data"))
            implementation(project(":core:ui"))
            implementation(project(":core:designsystem"))
            implementation(project(":feature:objects"))
            implementation(project(":feature:settings"))
        }
    }
}
