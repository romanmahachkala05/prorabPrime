import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("prorab.quality")
}

// versionCode is derived from the one version in gradle.properties, so the two cannot drift.
// Minor and patch are allowed 0-99 each.
val (versionMajor, versionMinor, versionPatch) = providers.gradleProperty("prorab.version").get()
    .split('.')
    .map(String::toInt)

// The server the app talks to until the user changes it in the settings. From local.properties,
// which is gitignored: a real address and token never enter the repository.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun buildConfigString(key: String): String {
    val value = localProperties.getProperty(key).orEmpty()
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "ru.prorabprime"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.prorabprime"
        minSdk = 26
        targetSdk = 37
        versionCode = versionMajor * 10_000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        buildConfigField("String", "DEFAULT_SERVER_URL", buildConfigString("prorab.serverUrl"))
        buildConfigField("String", "DEFAULT_API_TOKEN", buildConfigString("prorab.apiToken"))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// :app configures Compose directly rather than through a convention plugin (those are for
// libraries), so the shared stability config is pointed at by hand.
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose-stability.conf"))
}

dependencies {
    // The whole UI and the Koin module list; this module is only the Android entry point.
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    // Coil's singleton loader is built here, over the app's HttpClient (ADR-0003, ADR-0008).
    implementation(platform(libs.coil.bom))
    implementation(libs.coil)
    implementation(libs.coil.network.ktor3)
}
