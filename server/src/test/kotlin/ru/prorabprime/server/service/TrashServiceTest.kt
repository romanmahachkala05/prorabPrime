package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.PhotoRecord

class TrashServiceTest {

    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val storage = FakeFileStorage()
    private val clock = FixedClock()
    private val service = TrashService(objects, photos, storage, ImmediateTransactor, clock)

    private fun anObject(title: String): UUID {
        val id = UUID.randomUUID()
        objects.records[id] = ObjectRecord(
            id = id,
            fields = ObjectFields(title, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
            coverPhotoId = null,
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
        return id
    }

    private suspend fun aPhoto(objectId: UUID): UUID {
        val id = UUID.randomUUID()
        photos.insert(
            PhotoRecord(
                id = id,
                objectId = objectId,
                fileName = "$id.jpg",
                thumbFileName = "${id}_thumb.jpg",
                contentType = "image/jpeg",
                sizeBytes = 3,
                width = 4,
                height = 3,
                sortOrder = 1,
                createdAt = FIXED_NOW,
            ),
        )
        storage.write(objectId, "$id.jpg", byteArrayOf(1))
        storage.write(objectId, "${id}_thumb.jpg", byteArrayOf(2))
        return id
    }

    private fun Result<*>.serviceError() = (exceptionOrNull() as? ServiceException)?.error

    @Test
    fun `the trash lists deleted objects and the deleted photos of objects that are not deleted`() = runTest {
        val gone = anObject("Снесённый")
        val kept = anObject("Кухня")
        val photoOfGone = aPhoto(gone)
        val photoOfKept = aPhoto(kept)
        aPhoto(kept)
        photos.trash(photoOfGone, FIXED_NOW)
        photos.trash(photoOfKept, FIXED_NOW)
        objects.trash(gone, FIXED_NOW)

        val trash = service.list()

        assertThat(trash.objects.map { it.record.id }).containsExactly(gone)
        assertThat(trash.photos.map { it.photo.id }).containsExactly(photoOfKept)
        assertThat(trash.photos.single().objectTitle).isEqualTo("Кухня")
    }

    @Test
    fun `restoring an object brings it back and moves its update time`() = runTest {
        val id = anObject("Кухня")
        objects.trash(id, FIXED_NOW)
        clock.now = FIXED_NOW + 3.days

        assertThat(service.restoreObject(id).isSuccess).isTrue()

        assertThat(objects.find(id)).isNotNull()
        assertThat(objects.records.getValue(id).updatedAt).isEqualTo(clock.now)
    }

    @Test
    fun `restoring what is not in the trash is not found`() = runTest {
        val id = anObject("Кухня")

        assertThat(service.restoreObject(id).serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(service.restoreObject(UUID.randomUUID()).serviceError())
            .isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(service.restorePhoto(UUID.randomUUID()).serviceError())
            .isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `restoring a photo puts it back in its object, but not while the object is in the trash`() = runTest {
        val id = anObject("Кухня")
        val photo = aPhoto(id)
        photos.trash(photo, FIXED_NOW)
        objects.trash(id, FIXED_NOW)

        assertThat(service.restorePhoto(photo).serviceError()).isInstanceOf(ServiceError.NotFound::class.java)

        objects.restore(id)
        assertThat(service.restorePhoto(photo).isSuccess).isTrue()
        assertThat(photos.listByObject(id).map { it.id }).containsExactly(photo)
    }

    @Test
    fun `purging an object removes it and all its files for good`() = runTest {
        val id = anObject("Кухня")
        aPhoto(id)
        objects.trash(id, FIXED_NOW)

        assertThat(service.purgeObject(id).isSuccess).isTrue()

        assertThat(objects.records).doesNotContainKey(id)
        assertThat(photos.records).isEmpty()
        assertThat(storage.files).isEmpty()
    }

    @Test
    fun `a live object cannot be purged`() = runTest {
        val id = anObject("Кухня")

        assertThat(service.purgeObject(id).serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(objects.records).containsKey(id)
    }

    @Test
    fun `purging a photo removes its row and both files`() = runTest {
        val id = anObject("Кухня")
        val photo = aPhoto(id)
        val other = aPhoto(id)
        photos.trash(photo, FIXED_NOW)

        assertThat(service.purgePhoto(photo).isSuccess).isTrue()

        assertThat(photos.records.keys).containsExactly(other)
        assertThat(storage.namesOf(id)).containsExactly("$other.jpg", "${other}_thumb.jpg")
    }

    @Test
    fun `files that cannot be deleted do not fail a purge`() = runTest {
        val id = anObject("Кухня")
        objects.trash(id, FIXED_NOW)
        storage.failDeletes = true

        assertThat(service.purgeObject(id).isSuccess).isTrue()
        assertThat(objects.records).doesNotContainKey(id)
    }

    @Test
    fun `emptying removes everything in the trash and leaves the rest`() = runTest {
        val gone = anObject("Снесённый")
        val kept = anObject("Кухня")
        aPhoto(gone)
        val trashedPhoto = aPhoto(kept)
        val keptPhoto = aPhoto(kept)
        objects.trash(gone, FIXED_NOW)
        photos.trash(trashedPhoto, FIXED_NOW)

        assertThat(service.empty()).isEqualTo(2)

        assertThat(objects.records.keys).containsExactly(kept)
        assertThat(photos.records.keys).containsExactly(keptPhoto)
    }

    @Test
    fun `only what waited for thirty days is purged by the schedule`() = runTest {
        val old = anObject("Старый")
        val recent = anObject("Недавний")
        val oldPhoto = aPhoto(recent)
        objects.trash(old, FIXED_NOW - 31.days)
        objects.trash(anObject("Свежий"), FIXED_NOW - 2.days)
        photos.trash(oldPhoto, FIXED_NOW - 40.days)
        photos.trash(aPhoto(recent), FIXED_NOW - 29.days)

        assertThat(service.purgeExpired()).isEqualTo(2)

        assertThat(objects.records).doesNotContainKey(old)
        assertThat(photos.records).doesNotContainKey(oldPhoto)
        assertThat(service.list().objects).hasSize(1)
        assertThat(service.list().photos).hasSize(1)
    }
}
