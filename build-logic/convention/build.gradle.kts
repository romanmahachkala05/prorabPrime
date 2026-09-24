plugins {
    `kotlin-dsl`
}

group = "ru.prorabprime.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // Applied programmatically by the convention plugins below, so they need the plugin
    // artifacts themselves on the classpath, not just an `id(...)`/`alias(...)` reference.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "prorab.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpAndroidLibrary") {
            id = "prorab.kmp.android.library"
            implementationClass = "KmpAndroidLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "prorab.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("koin") {
            id = "prorab.koin"
            implementationClass = "KoinConventionPlugin"
        }
        register("quality") {
            id = "prorab.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}
