package ru.prorabprime.server.db

import com.zaxxer.hikari.HikariDataSource
import org.junit.Assume.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.prorabprime.server.config.DatabaseConfig

/**
 * One PostgreSQL container for the whole test run, started on first use. Without Docker the
 * tests that need it are skipped with a message — except on CI, where Docker is always present
 * and a missing one is a broken runner, not a reason to skip (ADR-0006).
 */
object TestPostgres {

    private const val IMAGE = "postgres:17-alpine"

    private val dockerAvailable: Boolean by lazy {
        runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
    }

    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer(IMAGE).apply { start() }
    }

    /** Call first in every test that needs the database. */
    fun assumeAvailable() {
        if (System.getenv("CI") != null) {
            check(dockerAvailable) { "Docker is required on CI for the PostgreSQL integration tests" }
        }
        assumeTrue("Docker is not available: skipping the PostgreSQL integration tests", dockerAvailable)
    }

    /** A migrated database with every table emptied. */
    fun freshDataSource(): HikariDataSource {
        val dataSource = createDataSource(DatabaseConfig(container.jdbcUrl, container.username, container.password))
        migrate(dataSource)
        dataSource.connection.use { it.createStatement().execute("TRUNCATE objects, photos CASCADE") }
        return dataSource
    }
}
