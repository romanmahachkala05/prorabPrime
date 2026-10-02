package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.data.local.Keys
import ru.prorabprime.data.local.Operation
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.domain.model.asAppError

class ObjectsRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val phone by lazy { OfflineFixture(folder.root).also { runTest { it.db.load() } } }

    private val draft = ObjectDraft(address = "Тверская, 5", title = "Кухня", status = ObjectStatus.PLANNED)

    @Test
    fun `after a sync the list shows what the server has, and it is still there without a signal`() = runTest {
        phone.engine.sync()
        phone.server.offline = true

        val list = phone.objects.observeObjects(ObjectQuery()).first().getOrThrow()

        assertThat(list.map { it.title }).containsExactly("Кухня")
        assertThat(list.single().isPending).isFalse()
    }

    @Test
    fun `an object made without a signal shows at once, marked, and is sent when the signal is back`() = runTest {
        phone.server.offline = true

        val id = phone.objects.create(draft).getOrThrow()

        val shown = phone.objects.observeObject(id).first().getOrThrow()
        assertThat(shown.address).isEqualTo("Тверская, 5")
        assertThat(shown.isPending).isTrue()
        val queued = phone.db.outbox.snapshot().single().operation as Operation.CreateObject
        assertThat(queued.request.id).isEqualTo(id.value)

        phone.server.offline = false
        phone.engine.sync()

        assertThat(phone.db.outbox.snapshot()).isEmpty()
        assertThat(phone.server.writes.single().url.encodedPath).isEqualTo("/api/objects")
    }

    @Test
    fun `the list is searched and sorted on the phone as the server would`() = runTest {
        phone.objects.create(ObjectDraft(address = "Арбат, 3", title = "Кухня")).getOrThrow()
        phone.objects.create(ObjectDraft(address = "Тверская, 5", title = "Ванная")).getOrThrow()
        phone.objects.create(ObjectDraft(address = "Бульвар, 1")).getOrThrow()

        val byAddress = phone.objects.observeObjects(ObjectQuery(sort = ObjectSort.ADDRESS_ASC)).first().getOrThrow()
        val found = phone.objects.observeObjects(ObjectQuery(search = " ВАНН ")).first().getOrThrow()

        assertThat(byAddress.map { it.address }).containsExactly("Арбат, 3", "Бульвар, 1", "Тверская, 5").inOrder()
        assertThat(found.map { it.address }).containsExactly("Тверская, 5")
    }

    @Test
    fun `the list can be narrowed to some statuses`() = runTest {
        phone.objects.create(ObjectDraft(address = "Арбат, 3", status = ObjectStatus.DONE)).getOrThrow()
        phone.objects.create(ObjectDraft(address = "Тверская, 5", status = ObjectStatus.PAUSED)).getOrThrow()
        phone.objects.create(ObjectDraft(address = "Бульвар, 1", status = ObjectStatus.PLANNED)).getOrThrow()

        val query = ObjectQuery(sort = ObjectSort.ADDRESS_ASC, statuses = setOf(ObjectStatus.DONE, ObjectStatus.PAUSED))
        val shown = phone.objects.observeObjects(query).first().getOrThrow()

        assertThat(shown.map { it.address }).containsExactly("Арбат, 3", "Тверская, 5").inOrder()
    }

    @Test
    fun `a change is written to the copy and queued, and a new address drops the old pin`() = runTest {
        phone.engine.sync()
        val id = ObjectId(phone.server.objectId)
        phone.db.objects.change { it[id.value] = it.getValue(id.value).copy(latitude = 56.0, longitude = 60.0) }

        phone.objects.update(id, ObjectDraft(address = "Новая, 2", title = "Новое")).getOrThrow()
        val shown = phone.objects.observeObject(id).first().getOrThrow()

        assertThat(shown.title).isEqualTo("Новое")
        assertThat(shown.point).isNull()
        assertThat(shown.isPending).isTrue()
        assertThat(Keys.obj(id.value)).isIn(phone.db.outbox.dirtyKeys())
    }

    @Test
    fun `the same address keeps its pin, and a pin picked on the map is used as it is`() = runTest {
        phone.engine.sync()
        val id = ObjectId(phone.server.objectId)
        phone.db.objects.change { it[id.value] = it.getValue(id.value).copy(latitude = 56.0, longitude = 60.0) }

        phone.objects.update(id, ObjectDraft(address = "Ленина, 1", title = "Новое")).getOrThrow()
        assertThat(phone.objects.observeObject(id).first().getOrThrow().point).isEqualTo(GeoPoint(56.0, 60.0))

        phone.objects.update(id, ObjectDraft(address = "Другой", point = GeoPoint(57.0, 61.0))).getOrThrow()
        assertThat(phone.objects.observeObject(id).first().getOrThrow().point).isEqualTo(GeoPoint(57.0, 61.0))
    }

    @Test
    fun `an object only the phone knew is forgotten without a word to the server`() = runTest {
        phone.server.offline = true
        val id = phone.objects.create(draft).getOrThrow()
        phone.contacts.create(id, ru.prorabprime.domain.model.ContactDraft("Анна")).getOrThrow()

        phone.objects.delete(id).getOrThrow()

        assertThat(phone.db.outbox.snapshot()).isEmpty()
        assertThat(phone.db.objects.rows.value).isEmpty()
        assertThat(phone.db.contacts.rows.value).isEmpty()
    }

    @Test
    fun `deleting a synced object queues the delete and takes it and its records off the phone`() = runTest {
        phone.engine.sync()
        val id = ObjectId(phone.server.objectId)
        phone.server.offline = true

        phone.objects.delete(id).getOrThrow()

        assertThat(phone.db.objects.rows.value).isEmpty()
        assertThat(phone.db.materials.rows.value).isEmpty()
        assertThat(phone.db.outbox.snapshot().single().operation).isEqualTo(Operation.DeleteObject(id.value))
    }

    @Test
    fun `an empty copy that could not sync is an error to show, not an empty list`() = runTest {
        phone.server.offline = true
        phone.engine.sync()

        val result = phone.objects.observeObjects(ObjectQuery()).first()

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Network)
    }

    @Test
    fun `nothing is said before the first sync has been tried`() = runTest {
        val first = withTimeoutOrNull(1) { phone.objects.observeObjects(ObjectQuery()).first() }

        assertThat(first).isNull()
    }

    @Test
    fun `an object the server does not have is not found, once a sync has been tried`() = runTest {
        phone.engine.sync()

        val result = phone.objects.observeObject(ObjectId("nobody")).first()

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.NotFound)
    }
}
