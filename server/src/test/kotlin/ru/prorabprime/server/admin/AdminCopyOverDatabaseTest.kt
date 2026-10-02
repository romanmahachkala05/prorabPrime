package ru.prorabprime.server.admin

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.contract.ContactRoleDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.fakes.aPhotoRecord
import ru.prorabprime.server.model.ContactFields
import ru.prorabprime.server.model.ContactRecord
import ru.prorabprime.server.model.Coordinates
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.PaymentFields
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.TaskFields
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord
import ru.prorabprime.server.repository.ExposedContactRepository
import ru.prorabprime.server.repository.ExposedObjectRepository
import ru.prorabprime.server.repository.ExposedPaymentRepository
import ru.prorabprime.server.repository.ExposedPhotoRepository
import ru.prorabprime.server.repository.ExposedTaskRepository
import ru.prorabprime.server.repository.ExposedUserRepository
import ru.prorabprime.server.service.AccountService
import ru.prorabprime.server.storage.LocalFileStorage

/** `user copy` as the owner runs it: a real database, real files, and the environment naming both. */
class AdminCopyOverDatabaseTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var dataSource: HikariDataSource
    private lateinit var db: DbExecutor
    private lateinit var ownerId: UUID
    private val owner get() = OwnerId(ownerId)

    private val objects get() = ExposedObjectRepository(db)
    private val photos get() = ExposedPhotoRepository(db)
    private val storage by lazy { LocalFileStorage(folder.root.toPath(), Dispatchers.IO) }
    private val at = Clock.System.now()

    @Before
    fun setUp() {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
        db = DbExecutor(Database.connect(dataSource), Dispatchers.IO)
        ownerId = runBlocking { checkNotNull(ExposedUserRepository(db).findByName("owner")).id }
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun env() = TestPostgres.config().let {
        mapOf(
            "DB_URL" to it.url,
            "DB_USER" to it.user,
            "DB_PASSWORD" to it.password.ifEmpty { "unused" },
            "STORAGE_DIR" to folder.root.path,
        )
    }

    private fun accounts() = AccountService(ExposedUserRepository(db), db, Clock.System)

    /** The owner's: a kitchen with a pin, a photo with files, a contact, a payment, and a task. */
    private suspend fun theOwnersWorld(): UUID {
        val id = UUID.randomUUID()
        objects.insert(
            ObjectRecord(
                id = id,
                ownerId = owner,
                fields = ObjectFields("Кухня", "Тверская, 5", ObjectStatusDto.IN_PROGRESS, "Иван", null, null),
                coverPhotoId = null,
                createdAt = at,
                updatedAt = at,
            ),
        )
        objects.setCoordinates(owner, id, Coordinates(56.8, 60.6))
        val photo = aPhotoRecord(id)
        storage.write(id, photo.fileName, byteArrayOf(1, 2, 3))
        storage.write(id, photo.thumbFileName, byteArrayOf(4))
        photos.insert(photo.copy(sizeBytes = 3))
        objects.setCover(owner, id, photo.id)
        ExposedContactRepository(db).insert(
            ContactRecord(UUID.randomUUID(), id, ContactFields("Иван", "+7", ContactRoleDto.CLIENT), 1, at),
        )
        ExposedPaymentRepository(db).insert(
            PaymentRecord(
                UUID.randomUUID(),
                id,
                PaymentFields(PaymentSideDto.CLIENT, 100, PaymentMethodDto.CASH, LocalDate.of(2026, 9, 25), null),
                at,
            ),
        )
        ExposedTaskRepository(db).insert(
            TaskRecord(UUID.randomUUID(), owner, TaskFields("Позвонить", LocalDate.of(2026, 9, 25), null, false), at),
        )
        return id
    }

    @Test
    fun `the new account has its own copy of everything, files included, and the token opens only it`() {
        val original = runBlocking { theOwnersWorld() }
        val output = mutableListOf<String>()

        val code = runAdmin(listOf("user", "copy", "owner", "Заказчик"), env(), output::add)

        assertThat(code).isEqualTo(0)
        runBlocking {
            val theirs = checkNotNull(ExposedUserRepository(db).findByName("Заказчик")).owner
            assertThat(accounts().authenticate(output.last())).isEqualTo(theirs)

            val copy = objects.list(theirs, ObjectListQuery()).single().record
            assertThat(copy.id).isNotEqualTo(original)
            assertThat(copy.coordinates).isEqualTo(Coordinates(56.8, 60.6))
            val copiedPhoto = photos.listByObject(copy.id).single()
            assertThat(copy.coverPhotoId).isEqualTo(copiedPhoto.id)
            assertThat(storage.read(copy.id, copiedPhoto.fileName)).isEqualTo(byteArrayOf(1, 2, 3))
            assertThat(storage.read(copy.id, copiedPhoto.thumbFileName)).isEqualTo(byteArrayOf(4))
            assertThat(ExposedContactRepository(db).listByObject(copy.id)).hasSize(1)
            assertThat(ExposedPaymentRepository(db).listByObject(copy.id)).hasSize(1)
            assertThat(ExposedTaskRepository(db).list(theirs, TaskQuery())).hasSize(1)
            assertThat(photos.usedBytes(theirs)).isEqualTo(3L)

            // The owner still has exactly what it had, and the two share no id.
            assertThat(objects.list(owner, ObjectListQuery()).single().record.id).isEqualTo(original)
            assertThat(photos.listByObject(original)).hasSize(1)
            assertThat(objects.find(theirs, original)).isNull()
            assertThat(objects.find(owner, copy.id)).isNull()
        }
    }

    @Test
    fun `a copy that fails leaves no account, no rows and no files behind`() {
        val original = runBlocking { theOwnersWorld() }
        // A second photo whose files are not on disk.
        runBlocking { photos.insert(aPhotoRecord(original, sortOrder = 2)) }
        val before = folder.root.walkTopDown().filter { it.isFile }.map { it.path }.toSet()

        assertThrows(IllegalStateException::class.java) {
            runAdmin(listOf("user", "copy", "owner", "Заказчик"), env()) {}
        }

        runBlocking {
            assertThat(ExposedUserRepository(db).findByName("Заказчик")).isNull()
            assertThat(objects.list(owner, ObjectListQuery())).hasSize(1)
        }
        assertThat(folder.root.walkTopDown().filter { it.isFile }.map { it.path }.toSet()).isEqualTo(before)
    }

    @Test
    fun `a copy needs to be told where the files are`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            runAdmin(listOf("user", "copy", "owner", "Заказчик"), env() - "STORAGE_DIR") {}
        }

        assertThat(failure).hasMessageThat().contains("STORAGE_DIR")
    }
}
