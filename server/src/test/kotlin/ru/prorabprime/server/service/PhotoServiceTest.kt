package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.sql.SQLException
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PhotoLimits
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeImageProcessor
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FakeReceiptReader
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.ReceiptData

class PhotoServiceTest {

    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val storage = FakeFileStorage()
    private val images = FakeImageProcessor()
    private val receipts = FakeReceiptReader()
    private val clock = FixedClock()
    private var nextId = 0
    private val service = PhotoService(
        objects,
        photos,
        storage,
        images,
        receipts,
        ImmediateTransactor,
        clock,
        newId = { UUID.fromString("00000000-0000-0000-0000-%012d".format(++nextId)) },
    )

    private val objectId = UUID.randomUUID()
    private val bytes = byteArrayOf(9, 9, 9)

    init {
        objects.records[objectId] = ObjectRecord(
            id = objectId,
            ownerId = TEST_OWNER,
            fields = ObjectFields(null, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
            coverPhotoId = null,
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
    }

    private fun Result<*>.serviceError() = (exceptionOrNull() as? ServiceException)?.error

    private suspend fun upload(minutesLater: Int = 0): UUID {
        clock.now = FIXED_NOW + minutesLater.minutes
        return service.upload(TEST_OWNER, objectId, bytes).getOrThrow().id
    }

    private fun cover() = objects.records.getValue(objectId).coverPhotoId

    // --- upload ---

    @Test
    fun `an upload stores the original, the thumbnail and the row`() = runTest {
        val id = upload()

        assertThat(storage.namesOf(objectId)).containsExactly("$id.jpg", "${id}_thumb.jpg")
        assertThat(storage.files["$objectId/$id.jpg"]).isEqualTo(bytes)
        val photo = photos.records.getValue(id)
        assertThat(photo.width to photo.height).isEqualTo(4000 to 3000)
        assertThat(photo.sizeBytes).isEqualTo(3L)
    }

    @Test
    fun `the first photo becomes the cover and later ones do not`() = runTest {
        val first = upload()
        upload()

        assertThat(cover()).isEqualTo(first)
    }

    @Test
    fun `photos go to the end of the carousel`() = runTest {
        upload()
        upload()
        val third = upload()

        assertThat(photos.records.getValue(third).sortOrder).isEqualTo(3)
    }

    @Test
    fun `an upload counts as a change to the object`() = runTest {
        upload(minutesLater = 5)

        assertThat(objects.records.getValue(objectId).updatedAt).isEqualTo(FIXED_NOW + 5.minutes)
    }

    @Test
    fun `an upload to an unknown object is not found and writes nothing`() = runTest {
        val result = service.upload(TEST_OWNER, UUID.randomUUID(), bytes)

        assertThat(result.serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(storage.files).isEmpty()
    }

    @Test
    fun `an oversized upload is refused before anything is written`() = runTest {
        val result = service.upload(TEST_OWNER, objectId, ByteArray(PhotoLimits.MAX_UPLOAD_BYTES.toInt() + 1))

        assertThat(result.serviceError()).isInstanceOf(ServiceError.TooLarge::class.java)
        assertThat(storage.files).isEmpty()
    }

    @Test
    fun `an unreadable image is refused before anything is written`() = runTest {
        images.reject = true

        assertThat(
            service.upload(TEST_OWNER, objectId, bytes).serviceError(),
        ).isInstanceOf(ServiceError.UnsupportedMedia::class.java)
        assertThat(storage.files).isEmpty()
        assertThat(photos.records).isEmpty()
    }

    @Test
    fun `a failed database write removes the files it left behind`() = runTest {
        photos.insertFailure = SQLException("connection lost")

        val failure = runCatching { service.upload(TEST_OWNER, objectId, bytes) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SQLException::class.java)

        assertThat(storage.files).isEmpty()
        assertThat(cover()).isNull()
    }

    @Test
    fun `a failed thumbnail write removes the original and adds no row`() = runTest {
        storage.failWritesEndingWith = "_thumb.jpg"

        val failure = runCatching { service.upload(TEST_OWNER, objectId, bytes) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IOException::class.java)

        assertThat(storage.files).isEmpty()
        assertThat(photos.records).isEmpty()
    }

    // --- delete ---

    @Test
    fun `deleting puts the photo in the trash and keeps its files`() = runTest {
        upload()
        val second = upload()

        assertThat(service.delete(TEST_OWNER, second).isSuccess).isTrue()

        assertThat(photos.trashedAt).containsKey(second)
        assertThat(photos.listByObject(objectId).map { it.id }).doesNotContain(second)
        assertThat(storage.namesOf(objectId)).containsAtLeast("$second.jpg", "${second}_thumb.jpg")
    }

    @Test
    fun `deleting the cover makes the newest remaining photo the cover`() = runTest {
        val first = upload(minutesLater = 0)
        val newest = upload(minutesLater = 20)
        upload(minutesLater = 10)

        service.delete(TEST_OWNER, first).getOrThrow()

        assertThat(cover()).isEqualTo(newest)
    }

    @Test
    fun `deleting the last photo leaves no cover`() = runTest {
        val only = upload()

        service.delete(TEST_OWNER, only).getOrThrow()

        assertThat(cover()).isNull()
    }

    @Test
    fun `deleting another photo keeps the cover`() = runTest {
        val first = upload()
        val second = upload()

        service.delete(TEST_OWNER, second).getOrThrow()

        assertThat(cover()).isEqualTo(first)
    }

    @Test
    fun `deleting a photo already in the trash is not found`() = runTest {
        val id = upload()
        service.delete(TEST_OWNER, id)

        assertThat(service.delete(TEST_OWNER, id).serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `deleting an unknown photo is not found`() = runTest {
        assertThat(
            service.delete(TEST_OWNER, UUID.randomUUID()).serviceError(),
        ).isInstanceOf(ServiceError.NotFound::class.java)
    }

    // --- receipt ---

    @Test
    fun `a receipt keeps what its code said, and a photo is not even read`() = runTest {
        receipts.result = ReceiptData(79_000, "2026-10-01T15:26", "t=20261001T1526&s=790.00")

        val receipt = service.upload(TEST_OWNER, objectId, bytes, AttachmentKindDto.RECEIPT).getOrThrow()
        val photo = service.upload(TEST_OWNER, objectId, bytes, AttachmentKindDto.PHOTO).getOrThrow()

        assertThat(photos.records.getValue(receipt.id).receipt?.amountKopecks).isEqualTo(79_000)
        assertThat(photos.records.getValue(photo.id).receipt).isNull()
        assertThat(receipts.reads).isEqualTo(1)
    }

    @Test
    fun `a receipt whose code cannot be read is stored all the same`() = runTest {
        receipts.result = null

        val receipt = service.upload(TEST_OWNER, objectId, bytes, AttachmentKindDto.RECEIPT).getOrThrow()

        assertThat(photos.records.getValue(receipt.id).receipt).isNull()
    }

    // --- note ---

    @Test
    fun `a note sent with the upload is kept, trimmed, and a blank one is no note`() = runTest {
        val noted = service.upload(TEST_OWNER, objectId, bytes, note = "  Трещина над окном ").getOrThrow()
        val blank = service.upload(TEST_OWNER, objectId, bytes, note = "   ").getOrThrow()

        assertThat(photos.records.getValue(noted.id).note).isEqualTo("Трещина над окном")
        assertThat(photos.records.getValue(blank.id).note).isNull()
    }

    @Test
    fun `a note that is too long is refused and nothing is stored`() = runTest {
        val result = service.upload(TEST_OWNER, objectId, bytes, note = "а".repeat(PhotoLimits.NOTE + 1))

        assertThat(result.serviceError()).isInstanceOf(ServiceError.Validation::class.java)
        assertThat(photos.records).isEmpty()
        assertThat(storage.files).isEmpty()
    }

    @Test
    fun `a note is set and cleared later, and either moves the object's update time`() = runTest {
        val id = upload()
        clock.now = FIXED_NOW + 5.minutes

        service.setNote(TEST_OWNER, id, "Договорились на пятницу").getOrThrow()
        assertThat(photos.records.getValue(id).note).isEqualTo("Договорились на пятницу")
        assertThat(objects.records.getValue(objectId).updatedAt).isEqualTo(FIXED_NOW + 5.minutes)

        service.setNote(TEST_OWNER, id, "").getOrThrow()
        assertThat(photos.records.getValue(id).note).isNull()
    }

    @Test
    fun `a note for an unknown photo is not found, and a long one is refused`() = runTest {
        assertThat(service.setNote(TEST_OWNER, UUID.randomUUID(), "x").serviceError())
            .isInstanceOf(ServiceError.NotFound::class.java)
        val id = upload()
        assertThat(service.setNote(TEST_OWNER, id, "а".repeat(PhotoLimits.NOTE + 1)).serviceError())
            .isInstanceOf(ServiceError.Validation::class.java)
    }

    // --- rotate ---

    @Test
    fun `rotating swaps the files for ones named after the rotation and removes the old`() = runTest {
        val id = upload()
        val rotation = UUID.randomUUID()

        val photo = service.rotate(TEST_OWNER, id, 1, rotation).getOrThrow()

        assertThat(photo.fileName).isEqualTo("$rotation.jpg")
        assertThat(photos.records.getValue(id).thumbFileName).isEqualTo("${rotation}_thumb.jpg")
        assertThat(storage.namesOf(objectId)).containsExactly("$rotation.jpg", "${rotation}_thumb.jpg")
        assertThat(images.turned).containsExactly(1)
        assertThat(storage.files["$objectId/$rotation.jpg"]).isEqualTo(bytes + FakeImageProcessor.ROTATED_MARK)
    }

    @Test
    fun `the same rotation sent again turns nothing`() = runTest {
        val id = upload()
        val rotation = UUID.randomUUID()
        service.rotate(TEST_OWNER, id, 1, rotation).getOrThrow()

        assertThat(service.rotate(TEST_OWNER, id, 1, rotation).isSuccess).isTrue()

        assertThat(images.turned).containsExactly(1)
    }

    @Test
    fun `a failed write while rotating leaves the old files and the old row`() = runTest {
        val id = upload()
        storage.failWritesEndingWith = "_thumb.jpg"

        runCatching { service.rotate(TEST_OWNER, id, 1, UUID.randomUUID()) }

        assertThat(photos.records.getValue(id).fileName).isEqualTo("$id.jpg")
        assertThat(storage.namesOf(objectId)).containsExactly("$id.jpg", "${id}_thumb.jpg")
    }

    @Test
    fun `rotating by no turns, or by four, is refused`() = runTest {
        val id = upload()

        assertThat(service.rotate(TEST_OWNER, id, 0, UUID.randomUUID()).serviceError())
            .isInstanceOf(ServiceError.Validation::class.java)
        assertThat(service.rotate(TEST_OWNER, id, 4, UUID.randomUUID()).serviceError())
            .isInstanceOf(ServiceError.Validation::class.java)
    }

    @Test
    fun `rotating an unknown photo is not found`() = runTest {
        assertThat(service.rotate(TEST_OWNER, UUID.randomUUID(), 1, UUID.randomUUID()).serviceError())
            .isInstanceOf(ServiceError.NotFound::class.java)
    }

    // --- cover ---

    @Test
    fun `any photo of the object can be made the cover`() = runTest {
        upload()
        val second = upload()

        assertThat(service.setCover(TEST_OWNER, objectId, second).isSuccess).isTrue()
        assertThat(cover()).isEqualTo(second)
    }

    @Test
    fun `a photo of another object cannot be the cover`() = runTest {
        val otherObject = UUID.randomUUID()
        objects.records[otherObject] = objects.records.getValue(objectId).copy(id = otherObject)
        val foreign = service.upload(TEST_OWNER, otherObject, bytes).getOrThrow().id

        assertThat(
            service.setCover(TEST_OWNER, objectId, foreign).serviceError(),
        ).isInstanceOf(ServiceError.Validation::class.java)
    }

    @Test
    fun `a cover for an unknown object is not found`() = runTest {
        assertThat(service.setCover(TEST_OWNER, UUID.randomUUID(), UUID.randomUUID()).serviceError())
            .isInstanceOf(ServiceError.NotFound::class.java)
    }

    // --- receipts ---

    @Test
    fun `a receipt is stored as a receipt and never becomes the cover`() = runTest {
        val receipt = service.upload(TEST_OWNER, objectId, bytes, AttachmentKindDto.RECEIPT).getOrThrow()

        assertThat(receipt.kind).isEqualTo(AttachmentKindDto.RECEIPT)
        assertThat(cover()).isNull()

        val photo = upload()
        assertThat(cover()).isEqualTo(photo)
    }

    @Test
    fun `a receipt cannot be made the cover`() = runTest {
        val receipt = service.upload(TEST_OWNER, objectId, bytes, AttachmentKindDto.RECEIPT).getOrThrow()

        val result = service.setCover(TEST_OWNER, objectId, receipt.id)

        assertThat(result.serviceError()).isInstanceOf(ServiceError.Validation::class.java)
        assertThat(cover()).isNull()
    }

    @Test
    fun `deleting the cover skips receipts when choosing the next one`() = runTest {
        val cover = upload()
        service.upload(TEST_OWNER, objectId, bytes, AttachmentKindDto.RECEIPT).getOrThrow()
        val other = upload(minutesLater = 1)

        service.delete(TEST_OWNER, cover).getOrThrow()

        assertThat(cover()).isEqualTo(other)
    }

    @Test
    fun `a photo uploaded under the client's id is stored once however often it is sent`() = runTest {
        val id = UUID.randomUUID()

        val first = service.upload(TEST_OWNER, objectId, bytes, clientId = id).getOrThrow()
        val again = service.upload(TEST_OWNER, objectId, bytes, clientId = id).getOrThrow()

        assertThat(first.id).isEqualTo(id)
        assertThat(again.id).isEqualTo(id)
        assertThat(photos.listByObject(objectId)).hasSize(1)
    }
}
