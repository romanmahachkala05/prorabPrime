package ru.prorabprime.server.repository

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.OTHER_OWNER
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.PhotoRecord
import ru.prorabprime.server.model.ReceiptData

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
                ownerId = TEST_OWNER,
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
    fun `what a receipt's code said is stored and read back`() = runTest {
        val receipt = ReceiptData(79_000, "2026-10-01T15:26", "t=20261001T1526&s=790.00")
        val photo = photo(sortOrder = 1).copy(kind = AttachmentKindDto.RECEIPT, receipt = receipt)

        photos.insert(photo)

        assertThat(photos.find(TEST_OWNER, photo.id)?.receipt).isEqualTo(receipt)
        assertThat(photos.find(TEST_OWNER, photo(sortOrder = 2).also { photos.insert(it) }.id)?.receipt).isNull()
    }

    @Test
    fun `a receipt's sum is set later and cleared`() = runTest {
        val photo = photo(sortOrder = 1).copy(kind = AttachmentKindDto.RECEIPT)
        photos.insert(photo)

        assertThat(photos.setReceipt(TEST_OWNER, photo.id, ReceiptData(5_000, "2026-10-01", ""))).isTrue()
        assertThat(photos.find(TEST_OWNER, photo.id)?.receipt?.amountKopecks).isEqualTo(5_000L)
        assertThat(photos.setReceipt(TEST_OWNER, photo.id, null)).isTrue()
        assertThat(photos.find(TEST_OWNER, photo.id)?.receipt).isNull()
        assertThat(photos.setReceipt(TEST_OWNER, UUID.randomUUID(), null)).isFalse()
    }

    @Test
    fun `a note is stored, read back, and cleared`() = runTest {
        val photo = photo(sortOrder = 1).copy(note = "Розетка слева")
        photos.insert(photo)
        assertThat(photos.find(TEST_OWNER, photo.id)?.note).isEqualTo("Розетка слева")

        assertThat(photos.setNote(TEST_OWNER, photo.id, null)).isTrue()
        assertThat(photos.find(TEST_OWNER, photo.id)?.note).isNull()
        assertThat(photos.setNote(TEST_OWNER, UUID.randomUUID(), "x")).isFalse()
    }

    @Test
    fun `an inserted photo reads back unchanged`() = runTest {
        val photo = photo(sortOrder = 1)

        photos.insert(photo)

        assertThat(photos.find(TEST_OWNER, photo.id)).isEqualTo(photo)
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

        assertThat(photos.delete(TEST_OWNER, photo.id)).isTrue()
        assertThat(photos.delete(TEST_OWNER, photo.id)).isFalse()
    }

    @Test
    fun `the cover can be set, cleared, and touches nothing else`() = runTest {
        val photo = photo(sortOrder = 1)
        photos.insert(photo)

        objects.setCover(TEST_OWNER, objectId, photo.id)
        assertThat(objects.find(TEST_OWNER, objectId)?.coverPhotoId).isEqualTo(photo.id)

        objects.setCover(TEST_OWNER, objectId, null)
        assertThat(objects.find(TEST_OWNER, objectId)?.coverPhotoId).isNull()
        assertThat(objects.find(TEST_OWNER, objectId)?.updatedAt).isEqualTo(base)
    }

    @Test
    fun `touching moves only updatedAt`() = runTest {
        objects.touch(TEST_OWNER, objectId, base + 7.minutes)

        val record = objects.find(TEST_OWNER, objectId)
        assertThat(record?.updatedAt).isEqualTo(base + 7.minutes)
        assertThat(record?.createdAt).isEqualTo(base)
    }

    @Test
    fun `work inside a failed transaction is rolled back`() = runTest {
        val photo = photo(sortOrder = 1)

        val failure = runCatching {
            db.inTransaction {
                photos.insert(photo)
                objects.setCover(TEST_OWNER, objectId, photo.id)
                error("fail after both writes")
            }
        }.exceptionOrNull()

        assertThat(failure).hasMessageThat().isEqualTo("fail after both writes")
        assertThat(photos.find(TEST_OWNER, photo.id)).isNull()
        assertThat(objects.find(TEST_OWNER, objectId)?.coverPhotoId).isNull()
    }

    @Test
    fun `a trashed photo is out of the object, found in the trash, and restored`() = runTest {
        val keep = photo(sortOrder = 1)
        val drop = photo(sortOrder = 2)
        photos.insert(keep)
        photos.insert(drop)

        assertThat(photos.trash(TEST_OWNER, drop.id, base + 3.minutes)).isTrue()
        assertThat(photos.trash(TEST_OWNER, drop.id, base)).isFalse()

        assertThat(photos.listByObject(objectId).map { it.id }).containsExactly(keep.id)
        assertThat(photos.find(TEST_OWNER, drop.id)).isNull()
        assertThat(photos.findAny(TEST_OWNER, drop.id)).isNotNull()
        assertThat(photos.findTrashed(TEST_OWNER, drop.id)).isNotNull()
        assertThat(photos.findTrashed(TEST_OWNER, keep.id)).isNull()
        val trashed = photos.listTrashed(TEST_OWNER).single()
        assertThat(trashed.photo.id).isEqualTo(drop.id)
        assertThat(trashed.deletedAt).isEqualTo(base + 3.minutes)
        assertThat(trashed.objectAddress).isEqualTo("Тверская, 5")

        assertThat(photos.restore(TEST_OWNER, drop.id)).isTrue()
        assertThat(photos.restore(TEST_OWNER, drop.id)).isFalse()
        assertThat(photos.listByObject(objectId).map { it.id }).containsExactly(keep.id, drop.id)
    }

    @Test
    fun `the photos of a trashed object are not listed in the trash on their own`() = runTest {
        val drop = photo(sortOrder = 1)
        photos.insert(drop)
        photos.trash(TEST_OWNER, drop.id, base)
        objects.trash(TEST_OWNER, objectId, base)

        assertThat(photos.listTrashed(TEST_OWNER)).isEmpty()
    }

    @Test
    fun `a photo is its object's owner's, and another account finds and changes nothing of it`() = runTest {
        val shot = photo(sortOrder = 1)
        photos.insert(shot)

        assertThat(photos.find(OTHER_OWNER, shot.id)).isNull()
        assertThat(photos.findAny(OTHER_OWNER, shot.id)).isNull()
        assertThat(photos.setNote(OTHER_OWNER, shot.id, "чужая")).isFalse()
        assertThat(photos.setReceipt(OTHER_OWNER, shot.id, ReceiptData(1, null, "q"))).isFalse()
        assertThat(photos.trash(OTHER_OWNER, shot.id, base)).isFalse()
        assertThat(photos.delete(OTHER_OWNER, shot.id)).isFalse()
        photos.replaceFiles(OTHER_OWNER, shot.copy(fileName = "evil.jpg"))

        assertThat(photos.find(TEST_OWNER, shot.id)).isEqualTo(shot)
    }

    @Test
    fun `the trash of photos is per account`() = runTest {
        val shot = photo(sortOrder = 1)
        photos.insert(shot)
        photos.trash(TEST_OWNER, shot.id, base)

        assertThat(photos.listTrashed(TEST_OWNER).map { it.photo.id }).containsExactly(shot.id)
        assertThat(photos.listTrashed(OTHER_OWNER)).isEmpty()
        assertThat(photos.findTrashed(OTHER_OWNER, shot.id)).isNull()
        assertThat(photos.restore(OTHER_OWNER, shot.id)).isFalse()
    }

    @Test
    fun `the expiry removes old trashed photos of live objects, and leaves those of a trashed object to it`() =
        runTest {
            val oldPhoto = photo(sortOrder = 1)
            val recent = photo(sortOrder = 2)
            photos.insert(oldPhoto)
            photos.insert(recent)
            photos.trash(TEST_OWNER, oldPhoto.id, base)
            photos.trash(TEST_OWNER, recent.id, base + 10.days)

            assertThat(photos.deleteTrashedBefore(base + 5.days).map { it.id }).containsExactly(oldPhoto.id)
            assertThat(photos.findAny(TEST_OWNER, oldPhoto.id)).isNull()
            assertThat(photos.findTrashed(TEST_OWNER, recent.id)).isNotNull()

            objects.trash(TEST_OWNER, objectId, base)
            assertThat(photos.deleteTrashedBefore(base + 20.days)).isEmpty()
        }
}
