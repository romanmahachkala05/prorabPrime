plugins {
    id("prorab.ktor.server")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":api-contract"))

    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.call.logging)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    implementation(libs.metadata.extractor)
    implementation(libs.imageio.webp)

    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers.postgresql)
    // Real PostgreSQL binaries for machines without Docker (ADR-0007).
    testImplementation(libs.embedded.postgres)
    testImplementation(platform(libs.embedded.postgres.binaries.bom))
}

/**
 * Both ways of running the server locally: from the repository root, so `./data/...` is the
 * directory docker-compose and the README talk about, and with `.env` loaded so the local
 * secrets never have to be exported by hand. Real environment variables still win.
 */
fun JavaExec.runsLocally() {
    workingDir = rootProject.projectDir
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            .filter { (key, _) -> System.getenv(key) == null }
            .forEach { (key, value) -> environment(key, value) }
    }
}

tasks.named<JavaExec>("run") { runsLocally() }

// The server over an embedded PostgreSQL, for machines without Docker (see DevServer.kt).
tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Runs the server with an embedded PostgreSQL in data/dev-postgres. Needs API_TOKEN (.env)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ru.prorabprime.server.dev.DevServerKt")
    runsLocally()
}

tasks.withType<Test>().configureEach {
    // A skipped integration test says why (no Docker) instead of vanishing from the output.
    testLogging {
        events("skipped", "failed")
        showExceptions = true
    }
}
