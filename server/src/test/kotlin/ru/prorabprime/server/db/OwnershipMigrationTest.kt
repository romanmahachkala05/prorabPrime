package ru.prorabprime.server.db

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * What a server that was running before accounts has in its database must come out of the migration as the
 * first account's, with nothing lost (ADR-0021).
 */
class OwnershipMigrationTest {

    private lateinit var dataSource: HikariDataSource

    @Before
    fun setUp() {
        TestPostgres.assumeAvailable()
        dataSource = createDataSource(TestPostgres.config())
        // A database as it was before accounts: everything dropped, then only the migrations up to V12.
        sql("DROP SCHEMA public CASCADE")
        sql("CREATE SCHEMA public")
        flyway("12").migrate()
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun flyway(target: String) = Flyway.configure().dataSource(dataSource).target(target).load()

    private fun sql(statement: String) = dataSource.connection.use { it.createStatement().execute(statement) }

    private fun scalar(query: String): Any? = dataSource.connection.use { connection ->
        connection.createStatement().executeQuery(query).use { if (it.next()) it.getObject(1) else null }
    }

    @Test
    fun `existing objects and tasks go to the first account, and none is lost`() {
        val objectIds = List(3) { UUID.randomUUID() }
        objectIds.forEach {
            sql("INSERT INTO objects (id, address, created_at, updated_at) VALUES ('$it', 'Тверская, 5', now(), now())")
        }
        sql("UPDATE objects SET deleted_at = now() WHERE id = '${objectIds.first()}'")
        repeat(2) {
            sql(
                "INSERT INTO tasks (id, title, day, created_at) " +
                    "VALUES ('${UUID.randomUUID()}', 'x', '2026-09-25', now())",
            )
        }

        flyway("latest").migrate()

        val first = scalar("SELECT id FROM users ORDER BY created_at, id LIMIT 1")
        assertThat(scalar("SELECT name FROM users WHERE id = '$first'")).isEqualTo("owner")
        assertThat(scalar("SELECT count(*) FROM objects WHERE owner_id = '$first'")).isEqualTo(3L)
        assertThat(scalar("SELECT count(*) FROM tasks WHERE owner_id = '$first'")).isEqualTo(2L)
        assertThat(scalar("SELECT count(*) FROM objects WHERE owner_id IS NULL")).isEqualTo(0L)
    }

    @Test
    fun `a server with no data migrates too, and objects and tasks must have an owner afterwards`() {
        flyway("latest").migrate()

        val failure = runCatching {
            sql(
                "INSERT INTO objects (id, address, created_at, updated_at) " +
                    "VALUES ('${UUID.randomUUID()}', 'x', now(), now())",
            )
        }.exceptionOrNull()
        assertThat(failure).isNotNull()
    }
}
