package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.ContactLimits
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.contract.ContactRoleDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.ObjectFieldDto
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

class ContactServiceTest {

    private val contacts = FakeContactRepository()
    private val objects = FakeObjectRepository(FakePhotoRepository())
    private val clock = FixedClock()
    private val objectService = ObjectService(objects, FakePhotoRepository(), contacts, clock)
    private val service = ContactService(objects, contacts, clock)

    private fun Result<*>.serviceError() = (exceptionOrNull() as? ServiceException)?.error

    private suspend fun anObject(): UUID = objectService
        .create(TEST_OWNER, ObjectRequestDto(address = "Тверская, 5", status = ObjectStatusDto.IN_PROGRESS))
        .getOrThrow().id

    @Test
    fun `a contact is trimmed, stored in order and moves the object's updated time`() = runTest {
        val objectId = anObject()
        clock.now = FIXED_NOW + 1.hours

        service.create(
            TEST_OWNER,
            objectId,
            ContactRequestDto("  Анна ", " 8 900 ", ContactRoleDto.CLIENT),
        ).getOrThrow()
        val second = service.create(
            TEST_OWNER,
            objectId,
            ContactRequestDto("Бригадир", null, ContactRoleDto.EXECUTOR),
        ).getOrThrow()

        val stored = contacts.listByObject(objectId)
        assertThat(stored.map { it.fields.name }).containsExactly("Анна", "Бригадир").inOrder()
        assertThat(stored.first().fields.phone).isEqualTo("8 900")
        assertThat(second.sortOrder).isEqualTo(2)
        assertThat(objects.records.getValue(objectId).updatedAt).isEqualTo(clock.now)
    }

    @Test
    fun `a contact of an unknown object is not found`() = runTest {
        val result = service.create(TEST_OWNER, UUID.randomUUID(), ContactRequestDto("Анна"))

        assertThat(result.serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `a blank name is required and an oversized phone is too long`() = runTest {
        val objectId = anObject()

        val blank = service.create(
            TEST_OWNER,
            objectId,
            ContactRequestDto("  "),
        ).serviceError() as ServiceError.Validation
        val long = service.create(TEST_OWNER, objectId, ContactRequestDto("Анна", "1".repeat(ContactLimits.PHONE + 1)))
            .serviceError() as ServiceError.Validation

        assertThat(blank.fieldErrors.map { it.field to it.problem })
            .containsExactly(ObjectFieldDto.CONTACT_NAME to FieldProblemDto.REQUIRED)
        assertThat(long.fieldErrors.map { it.field to it.problem })
            .containsExactly(ObjectFieldDto.CONTACT_PHONE to FieldProblemDto.TOO_LONG)
    }

    @Test
    fun `updating replaces the fields and deleting removes the contact`() = runTest {
        val objectId = anObject()
        val id = service.create(TEST_OWNER, objectId, ContactRequestDto("Анна")).getOrThrow().id

        service.update(TEST_OWNER, id, ContactRequestDto("Анна П.", "123", ContactRoleDto.CLIENT)).getOrThrow()
        assertThat(contacts.records.getValue(id).fields.name).isEqualTo("Анна П.")
        assertThat(contacts.records.getValue(id).fields.role).isEqualTo(ContactRoleDto.CLIENT)

        service.delete(TEST_OWNER, id).getOrThrow()
        assertThat(contacts.records).isEmpty()
        assertThat(service.delete(TEST_OWNER, id).serviceError()).isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `an object's details carry its contacts`() = runTest {
        val objectId = anObject()
        service.create(TEST_OWNER, objectId, ContactRequestDto("Анна")).getOrThrow()

        assertThat(objectService.get(TEST_OWNER, objectId).getOrThrow().contacts).hasSize(1)
    }
}
