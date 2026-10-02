package ru.prorabprime.server.db

import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.junit.Assume.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.prorabprime.server.OTHER_OWNER
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.config.DatabaseConfig

/**
 * One real PostgreSQL for the whole test run, started on first use: a Testcontainers container
 * when Docker is available, otherwise embedded PostgreSQL binaries (ADR-0007). Only when neither
 * starts are the tests skipped — and never on CI, where that means a broken runner (ADR-0006).
 */
object TestPostgres {

    private const val IMAGE = "postgres:17-alpine"

    private val database: DatabaseConfig? by lazy { fromDocker() ?: fromEmbedded() }

    private fun fromDocker(): DatabaseConfig? {
        val available = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
        if (!available) return null
        val container = PostgreSQLContainer(IMAGE).apply { start() }
        return DatabaseConfig(container.jdbcUrl, container.username, container.password)
    }

    private fun fromEmbedded(): DatabaseConfig? = runCatching {
        val postgres = EmbeddedPostgres.builder().start()
        Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })
        DatabaseConfig(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "")
    }.getOrNull()

    /** Where the test database is, for a test that starts something that connects to it by itself. */
    fun config(): DatabaseConfig = checkNotNull(database)

    /** Call first in every test that needs the database. */
    fun assumeAvailable() {
        if (System.getenv("CI") != null) {
            checkNotNull(database) { "PostgreSQL is required on CI for the integration tests" }
        }
        assumeTrue("Neither Docker nor embedded PostgreSQL is available: skipping", database != null)
    }

    /** A migrated database with every table emptied: those of objects by cascade, the rest by name. */
    fun freshDataSource(): HikariDataSource {
        val dataSource = createDataSource(checkNotNull(database))
        migrate(dataSource)
        dataSource.connection.use {
            it.createStatement().execute("TRUNCATE objects, photos, tasks, api_tokens CASCADE")
        }
        // The migration makes the first account; any other was made by a test. The two accounts the tests
        // use (TEST_OWNER, OTHER_OWNER) are made again, as objects and tasks must belong to somebody.
        dataSource.connection.use {
            it.createStatement().execute("DELETE FROM users WHERE name <> 'owner'")
            it.createStatement().execute(
                "INSERT INTO users (id, name, created_at) VALUES " +
                    "('${TEST_OWNER.value}', 'test-owner', now()), ('${OTHER_OWNER.value}', 'other-owner', now())",
            )
        }
        return dataSource
    }
}
