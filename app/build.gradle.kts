plugins {
    alias(libs.plugins.android.application)
    id("prorab.quality")
}

// versionCode is derived from the one version in gradle.properties, so the two cannot drift.
// Minor and patch are allowed 0-99 each.
val (versionMajor, versionMinor, versionPatch) = providers.gradleProperty("prorab.version").get()
    .split('.')
    .map(String::toInt)

android {
    namespace = "ru.prorabprime"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.prorabprime"
        minSdk = 26
        targetSdk = 37
        versionCode = versionMajor * 10_000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // The whole UI and the Koin module list; this module is only the Android entry point.
    implementation(project(":shared"))
}
