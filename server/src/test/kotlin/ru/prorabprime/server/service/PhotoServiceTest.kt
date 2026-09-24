package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.sql.SQLException
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PhotoLimits
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeImageProcessor
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord

class PhotoServiceTest {

    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val storage = FakeFileStorage()
    private val images = FakeImageProcessor()
    private val clock = FixedClock()
    private var nextId = 0
    private val service = PhotoService(
        objects,
        photos,
        storage,
        images,
        ImmediateTransactor,
        clock,
        newId = { UUID.fromString("00000000-0000-0000-0000-%012d".format(++nextId)) },
    )

    private val objectId = UUID.randomUUID()
    private val bytes = byteArrayOf(9, 9, 9)

    init {
        objects.records[objectId] = ObjectRecord(
            id = objectId,
            fields = ObjectFields(null, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
            coverPhotoId = null,
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
    }

    private fun Result<*>.serviceError() = (exceptionOrNull() as? ServiceException)?.error

    private suspend fun upload(minutesLater: Int = 0): UUID {
        clock.now = FIXED_NOW + minutesLater.minutes
        return service.upload(objectId, bytes).getOrThrow().id
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
        val result = service.upload(UUID.randomUUID(), bytes)

        assertThat(result.serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(storage.files).isEmpty()
    }

    @Test
    fun `an oversized upload is refused before anything is written`() = runTest {
        val result = service.upload(objectId, ByteArray(PhotoLimits.MAX_UPLOAD_BYTES.toInt() + 1))

        assertThat(result.serviceError()).isInstanceOf(ServiceError.TooLarge::class.java)
        assertThat(storage.files).isEmpty()
    }

    @Test
    fun `an unreadable image is refused before anything is written`() = runTest {
        images.reject = true

        assertThat(
            service.upload(objectId, bytes).serviceError(),
        ).isInstanceOf(ServiceError.UnsupportedMedia::class.java)
        assertThat(storage.files).isEmpty()
        assertThat(photos.records).isEmpty()
    }

    @Test
    fun `a failed database write removes the files it left behind`() = runTest {
        photos.insertFailure = SQLException("connection lost")

        val failure = runCatching { service.upload(objectId, bytes) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SQLException::class.java)

        assertThat(storage.files).isEmpty()
        assertThat(cover()).isNull()
    }

    @Test
    fun `a failed thumbnail write removes the original and adds no row`() = runTest {
        storage.failWritesEndingWith = "_thumb.jpg"

        val failure = runCatching { service.upload(objectId, bytes) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IOException::class.java)

        assertThat(storage.files).isEmpty()
        assertThat(photos.records).isEmpty()
    }

    // --- delete ---

    @Test
    fun `deleting removes the row and the files`() = runTest {
        upload()
        val second = upload()

        assertThat(service.delete(second).isSuccess).isTrue()

        assertThat(photos.records.keys).doesNotContain(second)
        assertThat(storage.namesOf(objectId)).containsNoneOf("$second.jpg", "${second}_thumb.jpg")
    }

    @Test
    fun `deleting the cover makes the newest remaining photo the cover`() = runTest {
        val first = upload(minutesLater = 0)
        val newest = upload(minutesLater = 20)
        upload(minutesLater = 10)

        service.delete(first).getOrThrow()

        assertThat(cover()).isEqualTo(newest)
    }

    @Test
    fun `deleting the last photo leaves no cover`() = runTest {
        val only = upload()

        service.delete(only).getOrThrow()

        assertThat(cover()).isNull()
    }

    @Test
    fun `deleting another photo keeps the cover`() = runTest {
        val first = upload()
        val second = upload()

        service.delete(second).getOrThrow()

        assertThat(cover()).isEqualTo(first)
    }

    @Test
    fun `a file that cannot be deleted does not fail the request`() = runTest {
        val id = upload()
        storage.failDeletes = true

        assertThat(service.delete(id).isSuccess).isTrue()
        assertThat(photos.records).isEmpty()
    }

    @Test
    fun `deleting an unknown photo is not found`() = runTest {
        assertThat(service.delete(UUID.randomUUID()).serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
    }

    // --- cover ---

    @Test
    fun `any photo of the object can be made the cover`() = runTest {
        upload()
        val second = upload()

        assertThat(service.setCover(objectId, second).isSuccess).isTrue()
        assertThat(cover()).isEqualTo(second)
    }

    @Test
    fun `a photo of another object cannot be the cover`() = runTest {
        val otherObject = UUID.randomUUID()
        objects.records[otherObject] = objects.records.getValue(objectId).copy(id = otherObject)
        val foreign = service.upload(otherObject, bytes).getOrThrow().id

        assertThat(service.setCover(objectId, foreign).serviceError()).isInstanceOf(ServiceError.Validation::class.java)
    }

    @Test
    fun `a cover for an unknown object is not found`() = runTest {
        assertThat(service.setCover(UUID.randomUUID(), UUID.randomUUID()).serviceError())
            .isInstanceOf(ServiceError.NotFound::class.java)
    }
}
