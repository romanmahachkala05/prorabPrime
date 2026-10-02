package ru.prorabprime.server.repository

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.prorabprime.contract.ContactRoleDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.model.ContactFields
import ru.prorabprime.server.model.ContactRecord
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord

class ExposedContactRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var objects: ExposedObjectRepository
    private lateinit var contacts: ExposedContactRepository

    private val base = Instant.parse("2026-09-25T10:00:00Z")
    private val objectId = UUID.randomUUID()

    @Before
    fun setUp() = runTest {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
        val db = DbExecutor(Database.connect(dataSource), Dispatchers.IO)
        objects = ExposedObjectRepository(db)
        contacts = ExposedContactRepository(db)
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

    private fun contact(sortOrder: Int, name: String = "Анна") = ContactRecord(
        id = UUID.randomUUID(),
        objectId = objectId,
        fields = ContactFields(name, "+7 900 000-00-00", ContactRoleDto.CLIENT),
        sortOrder = sortOrder,
        createdAt = base,
    )

    @Test
    fun `an inserted contact reads back unchanged and lists in order`() = runTest {
        val second = contact(sortOrder = 2, name = "Бригадир")
        val first = contact(sortOrder = 1)
        contacts.insert(second)
        contacts.insert(first)

        assertThat(contacts.find(TEST_OWNER, first.id)).isEqualTo(first)
        assertThat(contacts.listByObject(objectId)).containsExactly(first, second).inOrder()
    }

    @Test
    fun `updating and deleting report whether there was a contact`() = runTest {
        val contact = contact(sortOrder = 1)
        contacts.insert(contact)

        val changed = ContactFields("Анна П.", null, ContactRoleDto.EXECUTOR)
        assertThat(contacts.update(TEST_OWNER, contact.id, changed)).isTrue()
        assertThat(contacts.find(TEST_OWNER, contact.id)?.fields).isEqualTo(changed)
        assertThat(contacts.update(TEST_OWNER, UUID.randomUUID(), changed)).isFalse()

        assertThat(contacts.delete(TEST_OWNER, contact.id)).isTrue()
        assertThat(contacts.delete(TEST_OWNER, contact.id)).isFalse()
    }

    @Test
    fun `the next sort order follows the highest one, and deleting the object deletes its contacts`() = runTest {
        assertThat(contacts.nextSortOrder(objectId)).isEqualTo(1)
        contacts.insert(contact(sortOrder = 4))
        assertThat(contacts.nextSortOrder(objectId)).isEqualTo(5)

        objects.delete(TEST_OWNER, objectId)

        assertThat(contacts.listByObject(objectId)).isEmpty()
    }
}
