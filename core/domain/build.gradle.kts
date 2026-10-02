plugins {
    id("prorab.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`: Flow and ImmutableList/Map appear in the ports' and models' signatures.
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.collections.immutable)
        }

        jvmTest.dependencies {
            // Shared fakes; a test-to-main dependency in the other direction, not a cycle.
            implementation(project(":core:testing"))
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
