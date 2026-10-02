package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.prorabprime.contract.ContactRoleDto
import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.RevisionActionDto
import ru.prorabprime.server.OTHER_OWNER
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeExtraWorkRepository
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeFinanceTermsRepository
import ru.prorabprime.server.fakes.FakeMaterialRepository
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePaymentRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FakeTaskRepository
import ru.prorabprime.server.fakes.FakeUserRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor
import ru.prorabprime.server.fakes.aPhotoRecord
import ru.prorabprime.server.model.ContactFields
import ru.prorabprime.server.model.ContactRecord
import ru.prorabprime.server.model.Coordinates
import ru.prorabprime.server.model.ExtraWorkFields
import ru.prorabprime.server.model.ExtraWorkRecord
import ru.prorabprime.server.model.FinanceTerms
import ru.prorabprime.server.model.MaterialFields
import ru.prorabprime.server.model.MaterialRecord
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.PaymentFields
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.PaymentRevisionRecord
import ru.prorabprime.server.model.TaskFields
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord

class AccountCopyServiceTest {

    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val contacts = FakeContactRepository()
    private val terms = FakeFinanceTermsRepository()
    private val payments = FakePaymentRepository()
    private val extras = FakeExtraWorkRepository()
    private val materials = FakeMaterialRepository()
    private val tasks = FakeTaskRepository()
    private val users = FakeUserRepository().apply {
        seed(TEST_OWNER.value, "owner", "an-owner-token-0123456789")
    }
    private val storage = FakeFileStorage()
    private var made = 0
    private val service = AccountCopyService(
        users,
        objects,
        photos,
        contacts,
        terms,
        payments,
        extras,
        materials,
        tasks,
        storage,
        ImmediateTransactor,
        FixedClock(),
        newId = { UUID.fromString("00000000-0000-0000-0000-%012d".format(1000 + ++made)) },
    )

    private val kitchen = UUID.randomUUID()
    private val bath = UUID.randomUUID()

    private fun anObject(
        id: UUID,
        title: String,
        minutes: Int = 0,
        owner: UUID = TEST_OWNER.value,
    ) {
        val at = FIXED_NOW + minutes.minutes
        objects.records[id] = ObjectRecord(
            id = id,
            ownerId = OwnerId(owner),
            fields = ObjectFields(title, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, "Иван", "+7 900", "заметка"),
            coverPhotoId = null,
            createdAt = at,
            updatedAt = at,
            coordinates = Coordinates(56.8, 60.6),
        )
    }

    private suspend fun aPhoto(objectId: UUID, sortOrder: Int = 1): UUID {
        val photo = aPhotoRecord(objectId, sortOrder = sortOrder)
        photos.insert(photo)
        storage.write(objectId, photo.fileName, byteArrayOf(sortOrder.toByte(), 7))
        storage.write(objectId, photo.thumbFileName, byteArrayOf(sortOrder.toByte()))
        return photo.id
    }

    private fun Result<*>.error() = (exceptionOrNull() as? ServiceException)?.error

    private fun theirs() = users.users.values.single { it.name == "Заказчик" }.owner

    @Test
    fun `an object is copied with its fields, its pin and its times, under a new id and the new owner`() = runTest {
        anObject(kitchen, "Кухня", minutes = 5)

        val report = service.copy("owner", "Заказчик").getOrThrow()

        assertThat(report).isEqualTo(CopyReport(objects = 1, photos = 0, tasks = 0))
        val copy = objects.list(theirs(), ObjectListQuery()).single().record
        val original = objects.records.getValue(kitchen)
        assertThat(copy.id).isNotEqualTo(kitchen)
        assertThat(copy.fields).isEqualTo(original.fields)
        assertThat(copy.coordinates).isEqualTo(original.coordinates)
        assertThat(copy.createdAt).isEqualTo(original.createdAt)
        assertThat(copy.updatedAt).isEqualTo(original.updatedAt)
    }

    @Test
    fun `the original is left exactly as it was`() = runTest {
        anObject(kitchen, "Кухня")
        aPhoto(kitchen)
        val before = objects.records.toMap() to photos.records.toMap()
        val filesBefore = storage.files.keys.toSet()

        service.copy("owner", "Заказчик").getOrThrow()

        assertThat(objects.records.filterKeys { it in before.first }).isEqualTo(before.first)
        assertThat(photos.records.filterKeys { it in before.second }).isEqualTo(before.second)
        assertThat(storage.files.keys.containsAll(filesBefore)).isTrue()
    }

    @Test
    fun `photos are copied with their files under new names, and the cover follows`() = runTest {
        anObject(kitchen, "Кухня")
        aPhoto(kitchen, sortOrder = 1)
        val cover = aPhoto(kitchen, sortOrder = 2)
        objects.setCover(TEST_OWNER, kitchen, cover)

        val report = service.copy("owner", "Заказчик").getOrThrow()

        assertThat(report.photos).isEqualTo(2)
        val copy = objects.list(theirs(), ObjectListQuery()).single().record
        val copied = photos.listByObject(copy.id)
        assertThat(copied.map { it.sortOrder }).containsExactly(1, 2).inOrder()
        assertThat(copy.coverPhotoId).isEqualTo(copied.last().id)
        for (photo in copied) {
            assertThat(photo.id).isNotIn(photos.listByObject(kitchen).map { it.id })
            assertThat(photo.fileName).startsWith(photo.id.toString())
            assertThat(photo.thumbFileName).isEqualTo("${photo.id}_thumb.jpg")
            assertThat(storage.files["${copy.id}/${photo.fileName}"]).isNotNull()
            assertThat(storage.files["${copy.id}/${photo.thumbFileName}"]).isNotNull()
        }
        assertThat(storage.files["${copy.id}/${copied.last().fileName}"]).isEqualTo(byteArrayOf(2, 7))
    }

    @Test
    fun `contacts, terms, extra works and materials come along`() = runTest {
        anObject(kitchen, "Кухня")
        contacts.insert(
            ContactRecord(UUID.randomUUID(), kitchen, ContactFields("Иван", "+7", ContactRoleDto.CLIENT), 1, FIXED_NOW),
        )
        terms.save(kitchen, FinanceTerms(clientTotalKopecks = 1_000_000, crewTotalKopecks = 400_000))
        extras.insert(
            ExtraWorkRecord(
                UUID.randomUUID(),
                kitchen,
                ExtraWorkFields("Штробы", 5_000, ExtraWorkStatusDto.AGREED),
                FIXED_NOW,
            ),
        )
        materials.insert(
            MaterialRecord(
                UUID.randomUUID(),
                kitchen,
                MaterialFields("Плитка", MaterialStatusDto.NOT_CHOSEN),
                1,
                FIXED_NOW,
            ),
        )

        service.copy("owner", "Заказчик").getOrThrow()

        val copy = objects.list(theirs(), ObjectListQuery()).single().record.id
        assertThat(contacts.listByObject(copy).map { it.fields.name }).containsExactly("Иван")
        assertThat(terms.find(copy)).isEqualTo(FinanceTerms(1_000_000, 400_000))
        assertThat(extras.listByObject(copy).map { it.fields.title }).containsExactly("Штробы")
        assertThat(materials.listByObject(copy).map { it.fields.title }).containsExactly("Плитка")
    }

    @Test
    fun `payments come along with their history, which keeps pointing at the copy`() = runTest {
        anObject(kitchen, "Кухня")
        val paymentFields =
            PaymentFields(PaymentSideDto.CLIENT, 100_000, PaymentMethodDto.CASH, LocalDate.of(2026, 9, 25), null)
        val payment = PaymentRecord(UUID.randomUUID(), kitchen, paymentFields, FIXED_NOW)
        payments.insert(payment)
        payments.addRevision(
            PaymentRevisionRecord(
                UUID.randomUUID(),
                kitchen,
                payment.id,
                RevisionActionDto.CREATED,
                paymentFields,
                FIXED_NOW,
            ),
        )
        val gone = UUID.randomUUID()
        payments.addRevision(
            PaymentRevisionRecord(
                UUID.randomUUID(),
                kitchen,
                gone,
                RevisionActionDto.DELETED,
                paymentFields,
                FIXED_NOW,
            ),
        )

        service.copy("owner", "Заказчик").getOrThrow()

        val copy = objects.list(theirs(), ObjectListQuery()).single().record.id
        val copiedPayment = payments.listByObject(copy).single()
        assertThat(copiedPayment.id).isNotEqualTo(payment.id)
        assertThat(copiedPayment.fields).isEqualTo(paymentFields)
        val history = payments.revisionsOf(copy)
        assertThat(history).hasSize(2)
        assertThat(history.map { it.paymentId }).contains(copiedPayment.id)
        // A payment that was deleted has no copy, and its history must not point at the original's.
        assertThat(history.map { it.paymentId }).containsNoneIn(listOf(payment.id, gone))
    }

    @Test
    fun `tasks are copied, and what is in the trash is not`() = runTest {
        anObject(kitchen, "Кухня")
        anObject(bath, "Ванная")
        objects.trash(TEST_OWNER, bath, FIXED_NOW)
        tasks.insert(
            TaskRecord(
                UUID.randomUUID(),
                TEST_OWNER,
                TaskFields("Позвонить", LocalDate.of(2026, 9, 25), 540, true),
                FIXED_NOW,
            ),
        )

        val report = service.copy("owner", "Заказчик").getOrThrow()

        assertThat(report).isEqualTo(CopyReport(objects = 1, photos = 0, tasks = 1))
        assertThat(tasks.list(theirs(), TaskQuery()).single().fields.title).isEqualTo("Позвонить")
        assertThat(objects.list(theirs(), ObjectListQuery()).map { it.record.fields.title }).containsExactly("Кухня")
    }

    @Test
    fun `another account's things are not taken`() = runTest {
        anObject(kitchen, "Кухня")
        anObject(bath, "Чужая", owner = OTHER_OWNER.value)

        service.copy("owner", "Заказчик").getOrThrow()

        assertThat(objects.list(theirs(), ObjectListQuery()).map { it.record.fields.title }).containsExactly("Кухня")
    }

    @Test
    fun `a name that is taken, or an account that is not there, is refused and makes nothing`() = runTest {
        anObject(kitchen, "Кухня")
        users.seed(OTHER_OWNER.value, "Пётр", "another-token-0123456789")

        assertThat(service.copy("owner", "Пётр").error()).isInstanceOf(ServiceError.Conflict::class.java)
        assertThat(service.copy("Никто", "Заказчик").error()).isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(service.copy("owner", "  ").error()).isInstanceOf(ServiceError.Validation::class.java)
        assertThat(users.users.values.map { it.name }).containsExactly("owner", "Пётр")
        assertThat(objects.records).hasSize(1)
    }

    @Test
    fun `a missing file stops the copy, removes the files already copied and leaves no trace`() = runTest {
        anObject(kitchen, "Кухня", minutes = 0)
        aPhoto(kitchen, sortOrder = 1)
        // The second photo has a row but no file on disk.
        photos.insert(aPhotoRecord(kitchen, sortOrder = 2))
        val filesBefore = storage.files.keys.toSet()

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { service.copy("owner", "Заказчик") }
        }

        assertThat(storage.files.keys).isEqualTo(filesBefore)
    }
}
