plugins {
    id("prorab.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`: every DTO is `@Serializable`, so consumers compile against the annotations.
            api(libs.kotlinx.serialization.core)
        }

        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
