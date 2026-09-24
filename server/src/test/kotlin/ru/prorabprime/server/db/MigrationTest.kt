package ru.prorabprime.server.db

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.sql.SQLException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MigrationTest {

    private lateinit var dataSource: HikariDataSource

    @Before
    fun setUp() {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun sql(statement: String) = dataSource.connection.use { it.createStatement().execute(statement) }

    private fun scalar(query: String): Any? = dataSource.connection.use { connection ->
        connection.createStatement().executeQuery(query).use { if (it.next()) it.getObject(1) else null }
    }

    private fun insertObject(id: UUID) =
        sql("INSERT INTO objects (id, address, created_at, updated_at) VALUES ('$id', 'Тверская, 5', now(), now())")

    private fun insertPhoto(id: UUID, objectId: UUID) = sql(
        "INSERT INTO photos (id, object_id, file_name, thumb_file_name, content_type, size_bytes, " +
            "width, height, sort_order, created_at) " +
            "VALUES ('$id', '$objectId', 'a.jpg', 'a_thumb.jpg', 'image/jpeg', 1, 1, 1, 1, now())",
    )

    @Test
    fun `a new object defaults to in progress with empty custom fields`() {
        val id = UUID.randomUUID()
        insertObject(id)

        assertThat(scalar("SELECT status FROM objects WHERE id = '$id'")).isEqualTo("IN_PROGRESS")
        assertThat(scalar("SELECT custom_fields::text FROM objects WHERE id = '$id'")).isEqualTo("{}")
    }

    @Test
    fun `an unknown status is rejected`() {
        assertThrows(SQLException::class.java) {
            sql(
                "INSERT INTO objects (id, address, status, created_at, updated_at) " +
                    "VALUES ('${UUID.randomUUID()}', 'x', 'LOST', now(), now())",
            )
        }
    }

    @Test
    fun `a photo of another object cannot be the cover`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val photoOfSecond = UUID.randomUUID()
        insertObject(first)
        insertObject(second)
        insertPhoto(photoOfSecond, second)

        assertThrows(SQLException::class.java) {
            sql("UPDATE objects SET cover_photo_id = '$photoOfSecond' WHERE id = '$first'")
        }
    }

    @Test
    fun `deleting the cover photo clears only the cover`() {
        val objectId = UUID.randomUUID()
        val photoId = UUID.randomUUID()
        insertObject(objectId)
        insertPhoto(photoId, objectId)
        sql("UPDATE objects SET cover_photo_id = '$photoId' WHERE id = '$objectId'")

        sql("DELETE FROM photos WHERE id = '$photoId'")

        assertThat(scalar("SELECT count(*) FROM objects WHERE id = '$objectId'")).isEqualTo(1L)
        assertThat(scalar("SELECT cover_photo_id FROM objects WHERE id = '$objectId'")).isNull()
    }

    @Test
    fun `deleting an object deletes its photos`() {
        val objectId = UUID.randomUUID()
        insertObject(objectId)
        insertPhoto(UUID.randomUUID(), objectId)
        sql("UPDATE objects SET cover_photo_id = (SELECT id FROM photos LIMIT 1) WHERE id = '$objectId'")

        sql("DELETE FROM objects WHERE id = '$objectId'")

        assertThat(scalar("SELECT count(*) FROM photos")).isEqualTo(0L)
    }

    @Test
    fun `the executor runs a query in a transaction`() = runTest {
        val executor = DbExecutor(Database.connect(dataSource), Dispatchers.IO)

        val answer = executor.query {
            exec("SELECT 42") { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

        assertThat(answer).isEqualTo(42)
    }
}
