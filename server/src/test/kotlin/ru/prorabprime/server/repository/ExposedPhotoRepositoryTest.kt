package ru.prorabprime.server.repository

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.PhotoRecord

class ExposedPhotoRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var db: DbExecutor
    private lateinit var objects: ExposedObjectRepository
    private lateinit var photos: ExposedPhotoRepository

    private val base = Instant.parse("2026-09-25T10:00:00Z")
    private val objectId = UUID.randomUUID()

    @Before
    fun setUp() = runTest {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
        db = DbExecutor(Database.connect(dataSource), Dispatchers.IO)
        objects = ExposedObjectRepository(db)
        photos = ExposedPhotoRepository(db)
        objects.insert(
            ObjectRecord(
                id = objectId,
                fields = ObjectFields(null, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
                coverPhotoId = null,
                createdAt = base,
                updatedAt = base,
            ),
        )
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun photo(sortOrder: Int, id: UUID = UUID.randomUUID()) = PhotoRecord(
        id = id,
        objectId = objectId,
        fileName = "$id.jpg",
        thumbFileName = "${id}_thumb.jpg",
        contentType = "image/jpeg",
        sizeBytes = 1234,
        width = 2048,
        height = 1536,
        sortOrder = sortOrder,
        createdAt = base,
    )

    @Test
    fun `an inserted photo reads back unchanged`() = runTest {
        val photo = photo(sortOrder = 1)

        photos.insert(photo)

        assertThat(photos.find(photo.id)).isEqualTo(photo)
    }

    @Test
    fun `the next sort order follows the highest one`() = runTest {
        assertThat(photos.nextSortOrder(objectId)).isEqualTo(1)
        photos.insert(photo(sortOrder = 1))
        photos.insert(photo(sortOrder = 5))

        assertThat(photos.nextSortOrder(objectId)).isEqualTo(6)
    }

    @Test
    fun `deleting reports whether there was a photo`() = runTest {
        val photo = photo(sortOrder = 1)
        photos.insert(photo)

        assertThat(photos.delete(photo.id)).isTrue()
        assertThat(photos.delete(photo.id)).isFalse()
    }

    @Test
    fun `the cover can be set, cleared, and touches nothing else`() = runTest {
        val photo = photo(sortOrder = 1)
        photos.insert(photo)

        objects.setCover(objectId, photo.id)
        assertThat(objects.find(objectId)?.coverPhotoId).isEqualTo(photo.id)

        objects.setCover(objectId, null)
        assertThat(objects.find(objectId)?.coverPhotoId).isNull()
        assertThat(objects.find(objectId)?.updatedAt).isEqualTo(base)
    }

    @Test
    fun `touching moves only updatedAt`() = runTest {
        objects.touch(objectId, base + 7.minutes)

        val record = objects.find(objectId)
        assertThat(record?.updatedAt).isEqualTo(base + 7.minutes)
        assertThat(record?.createdAt).isEqualTo(base)
    }

    @Test
    fun `work inside a failed transaction is rolled back`() = runTest {
        val photo = photo(sortOrder = 1)

        val failure = runCatching {
            db.inTransaction {
                photos.insert(photo)
                objects.setCover(objectId, photo.id)
                error("fail after both writes")
            }
        }.exceptionOrNull()

        assertThat(failure).hasMessageThat().isEqualTo("fail after both writes")
        assertThat(photos.find(photo.id)).isNull()
        assertThat(objects.find(objectId)?.coverPhotoId).isNull()
    }
}
