package ru.prorabprime.server.dev

import io.ktor.server.netty.EngineMain
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Path

/**
 * `./gradlew :server:runDev`: the real server over an embedded PostgreSQL, for machines without
 * Docker. Data survives restarts in `data/dev-postgres`. The token still comes from `API_TOKEN`
 * (`.env`), as in production; only the database is provided here.
 */
fun main() {
    val postgres = EmbeddedPostgres.builder()
        .setPort(DEV_DB_PORT)
        .setDataDirectory(Path.of("data", "dev-postgres"))
        .setCleanDataDirectory(false)
        .start()
    Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })

    EngineMain.main(
        arrayOf(
            "-P:prorab.database.url=${postgres.getJdbcUrl("postgres", "postgres")}",
            // The embedded server trusts local connections; the password is not checked.
            "-P:prorab.database.user=postgres",
            "-P:prorab.database.password=postgres",
        ),
    )
}

// Not 5432, so it never collides with the docker-compose database.
private const val DEV_DB_PORT = 5433
