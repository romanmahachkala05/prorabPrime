package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.MaterialRequestDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.TaskRequestDto
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeExtraWorkRepository
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeMaterialRepository
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePaymentRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FakeTaskRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor

/** The phone creates records offline under ids of its own; sending the same create again must change nothing. */
class ClientIdCreateTest {

    private val clock = FixedClock()
    private val objects = FakeObjectRepository(FakePhotoRepository())
    private val contactRepo = FakeContactRepository()
    private val materialRepo = FakeMaterialRepository()
    private val paymentRepo = FakePaymentRepository()
    private val extraRepo = FakeExtraWorkRepository()
    private val taskRepo = FakeTaskRepository()

    private val objectService = ObjectService(objects, FakePhotoRepository(), contactRepo, clock)
    private val contacts = ContactService(objects, contactRepo, clock)
    private val materials = MaterialService(objects, materialRepo, clock)
    private val payments = PaymentService(objects, paymentRepo, ImmediateTransactor, clock)
    private val extras = ExtraWorkService(objects, extraRepo, clock)
    private val tasks = TaskService(taskRepo, clock)

    private fun objectRequest(id: String? = null) =
        ObjectRequestDto(id = id, address = "Тверская, 5", status = ObjectStatusDto.IN_PROGRESS)

    private fun Result<*>.error() = (exceptionOrNull() as? ServiceException)?.error

    private suspend fun anObject(): UUID = objectService.create(TEST_OWNER, objectRequest()).getOrThrow().id

    @Test
    fun `an object is created under the id the client chose, once`() = runTest {
        val id = UUID.randomUUID()

        val first = objectService.create(TEST_OWNER, objectRequest(id.toString())).getOrThrow()
        val again = objectService.create(TEST_OWNER, objectRequest(id.toString())).getOrThrow()

        assertThat(first.id).isEqualTo(id)
        assertThat(again.id).isEqualTo(id)
        assertThat(objects.records).hasSize(1)
    }

    @Test
    fun `an id that is not a UUID is a validation error, and none means the server picks`() = runTest {
        assertThat(
            objectService.create(TEST_OWNER, objectRequest("not-a-uuid")).error(),
        ).isInstanceOf(ServiceError.Validation::class.java)

        val picked = objectService.create(TEST_OWNER, objectRequest()).getOrThrow()

        assertThat(picked.id).isNotNull()
    }

    @Test
    fun `a contact, a material, a payment and an extra work are each created once per id`() = runTest {
        val objectId = anObject()
        val contactId = UUID.randomUUID()
        val materialId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        val extraId = UUID.randomUUID()
        val payment = PaymentRequestDto(
            id = paymentId.toString(),
            side = PaymentSideDto.CLIENT,
            amountKopecks = 100_00,
            method = PaymentMethodDto.CASH,
            paidOn = "2026-09-25",
        )

        repeat(2) {
            contacts.create(
                TEST_OWNER,
                objectId,
                ContactRequestDto(id = contactId.toString(), name = "Анна"),
            ).getOrThrow()
            materials.create(
                TEST_OWNER,
                objectId,
                MaterialRequestDto(id = materialId.toString(), title = "Ламинат"),
            ).getOrThrow()
            payments.create(TEST_OWNER, objectId, payment).getOrThrow()
            extras.create(
                TEST_OWNER,
                objectId,
                ExtraWorkRequestDto(id = extraId.toString(), title = "Штробление", amountKopecks = 50_000),
            ).getOrThrow()
        }

        assertThat(contactRepo.listByObject(objectId).map { it.id }).containsExactly(contactId)
        assertThat(materialRepo.listByObject(objectId).map { it.id }).containsExactly(materialId)
        assertThat(paymentRepo.listByObject(objectId).map { it.id }).containsExactly(paymentId)
        // One create, one history entry: the retry added nothing.
        assertThat(paymentRepo.revisionsOf(objectId)).hasSize(1)
        assertThat(extraRepo.listByObject(objectId).map { it.id }).containsExactly(extraId)
    }

    @Test
    fun `a task is created once per id`() = runTest {
        val id = UUID.randomUUID()
        val request = TaskRequestDto(id = id.toString(), title = "Позвонить", day = "2026-09-25")

        tasks.create(TEST_OWNER, request).getOrThrow()
        tasks.create(TEST_OWNER, request).getOrThrow()

        assertThat(taskRepo.list(TEST_OWNER, ru.prorabprime.server.model.TaskQuery()).map { it.id }).containsExactly(id)
    }

    @Test
    fun `an id already used under another object is a conflict, not a second record`() = runTest {
        val first = anObject()
        val second = anObject()
        val id = UUID.randomUUID()
        contacts.create(TEST_OWNER, first, ContactRequestDto(id = id.toString(), name = "Анна")).getOrThrow()

        val result = contacts.create(TEST_OWNER, second, ContactRequestDto(id = id.toString(), name = "Анна"))

        assertThat(result.error()).isInstanceOf(ServiceError.Conflict::class.java)
        assertThat(contactRepo.listByObject(second)).isEmpty()
    }
}
