package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.aPhotoRecord
import ru.prorabprime.server.model.ObjectListQuery

class ObjectServiceTest {

    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val clock = FixedClock()
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val storage = FakeFileStorage()
    private val service = ObjectService(objects, photos, FakeContactRepository(), clock, newId = { id })

    private val request = ObjectRequestDto(address = "Тверская, 5", status = ObjectStatusDto.IN_PROGRESS)

    private fun Result<*>.serviceError() = (exceptionOrNull() as? ServiceException)?.error

    @Test
    fun `creating stores the object with a new id and the current time`() = runTest {
        val created = service.create(TEST_OWNER, request).getOrThrow()

        assertThat(created.id).isEqualTo(id)
        assertThat(created.createdAt).isEqualTo(FIXED_NOW)
        assertThat(created.updatedAt).isEqualTo(FIXED_NOW)
        assertThat(created.coverPhotoId).isNull()
        assertThat(objects.records[id]).isEqualTo(created)
    }

    @Test
    fun `an invalid request stores nothing`() = runTest {
        val result = service.create(TEST_OWNER, request.copy(address = " "))

        assertThat(result.serviceError()).isInstanceOf(ServiceError.Validation::class.java)
        assertThat(objects.records).isEmpty()
    }

    @Test
    fun `getting an object includes its photos in order`() = runTest {
        service.create(TEST_OWNER, request)
        val second = aPhotoRecord(id, sortOrder = 2)
        val first = aPhotoRecord(id, sortOrder = 1)
        photos.records[second.id] = second
        photos.records[first.id] = first

        val details = service.get(TEST_OWNER, id).getOrThrow()

        assertThat(details.photos).containsExactly(first, second).inOrder()
    }

    @Test
    fun `getting an unknown object is not found`() = runTest {
        assertThat(
            service.get(TEST_OWNER, UUID.randomUUID()).serviceError(),
        ).isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `updating replaces the fields and moves only updatedAt`() = runTest {
        service.create(TEST_OWNER, request)
        clock.now = FIXED_NOW + 1.hours

        val updated = service.update(
            TEST_OWNER,
            id,
            request.copy(title = "Кухня", status = ObjectStatusDto.DONE),
        ).getOrThrow()

        assertThat(updated.record.fields.title).isEqualTo("Кухня")
        assertThat(updated.record.fields.status).isEqualTo(ObjectStatusDto.DONE)
        assertThat(updated.record.createdAt).isEqualTo(FIXED_NOW)
        assertThat(updated.record.updatedAt).isEqualTo(FIXED_NOW + 1.hours)
    }

    @Test
    fun `updating an unknown object is not found`() = runTest {
        assertThat(service.update(TEST_OWNER, UUID.randomUUID(), request).serviceError())
            .isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `an invalid update changes nothing`() = runTest {
        service.create(TEST_OWNER, request)

        val result = service.update(TEST_OWNER, id, request.copy(address = ""))

        assertThat(result.serviceError()).isInstanceOf(ServiceError.Validation::class.java)
        assertThat(objects.records.getValue(id).fields.address).isEqualTo("Тверская, 5")
    }

    @Test
    fun `deleting puts the object in the trash with its files, and the object is no longer found`() = runTest {
        service.create(TEST_OWNER, request)
        storage.write(id, "a.jpg", byteArrayOf(1))

        assertThat(service.delete(TEST_OWNER, id).isSuccess).isTrue()

        assertThat(objects.trashedAt).containsKey(id)
        assertThat(objects.records).containsKey(id)
        assertThat(storage.files).isNotEmpty()
        assertThat(service.get(TEST_OWNER, id).serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(service.list(TEST_OWNER, ObjectListQuery())).isEmpty()
    }

    @Test
    fun `deleting an object already in the trash is not found`() = runTest {
        service.create(TEST_OWNER, request)
        service.delete(TEST_OWNER, id)

        assertThat(service.delete(TEST_OWNER, id).serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `an object cannot be created again with the id of one in the trash`() = runTest {
        service.create(TEST_OWNER, request.copy(id = id.toString()))
        service.delete(TEST_OWNER, id)

        // A retried create finds what it made instead of failing on the key.
        assertThat(service.create(TEST_OWNER, request.copy(id = id.toString())).isSuccess).isTrue()
        assertThat(objects.records).hasSize(1)
    }

    @Test
    fun `deleting an unknown object is not found`() = runTest {
        assertThat(
            service.delete(TEST_OWNER, UUID.randomUUID()).serviceError(),
        ).isInstanceOf(ServiceError.NotFound::class.java)
    }
}
