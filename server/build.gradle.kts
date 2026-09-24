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

    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers.postgresql)
    // Real PostgreSQL binaries for machines without Docker (ADR-0007).
    testImplementation(libs.embedded.postgres)
    testImplementation(platform(libs.embedded.postgres.binaries.bom))
}

tasks.named<JavaExec>("run") {
    // Run from the repository root, so `./data/uploads` is the same directory docker-compose
    // and the README talk about.
    workingDir = rootProject.projectDir
    // `.env` holds the local secrets; loading it here means they never have to be exported
    // by hand. Real environment variables still win.
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

tasks.withType<Test>().configureEach {
    // A skipped integration test says why (no Docker) instead of vanishing from the output.
    testLogging {
        events("skipped", "failed")
        showExceptions = true
    }
}
