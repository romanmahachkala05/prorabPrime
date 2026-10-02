package ru.prorabprime.data.sync

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.contract.ContactRoleDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.data.local.Keys
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.local.MemoryBlobStore
import ru.prorabprime.data.local.MemoryPersistence
import ru.prorabprime.data.local.ObjectRow
import ru.prorabprime.data.local.Operation
import ru.prorabprime.data.local.OperationState
import ru.prorabprime.data.remote.RemoteApi

class SyncEngineTest {

    private val server = FakeServer()
    private val db = LocalDb(MemoryPersistence(), MemoryBlobStore())
    private var serverKey = "http://one"

    private val engine: SyncEngine = RemoteApi(server.http.client).let { remote ->
        SyncEngine(db, remote, OperationRunner(remote, db.blobs), { serverKey })
    }

    private val objectId get() = server.objectId

    private fun HttpRequestData.bodyText() = (body as? TextContent)?.text.orEmpty()

    private fun request(address: String = "Ленина, 1", title: String? = null) =
        ObjectRequestDto(title = title, address = address, status = ObjectStatusDto.IN_PROGRESS)

    @Test
    fun `a first sync copies everything of an object to the phone`() = runTest {
        db.load()

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Synced)
        assertThat(db.objects.rows.value.getValue(objectId).clientPhone).isEqualTo("+7 900")
        assertThat(db.terms.rows.value.getValue(objectId).dto.clientTotalKopecks).isEqualTo(1_000L)
        assertThat(db.materials.rows.value.values.map { it.dto.title }).containsExactly("Ламинат")
        assertThat(db.tasks.rows.value.keys).containsExactly("t1")
        assertThat(db.metaRow.lastSyncAt).isNotNull()
        assertThat(db.metaRow.syncTried).isTrue()
    }

    @Test
    fun `an object that has not changed is not fetched again`() = runTest {
        db.load()
        engine.sync()
        server.http.requests.clear()

        engine.sync()

        val paths = server.http.requests.map { it.url.encodedPath }
        assertThat(paths).doesNotContain("/api/objects/$objectId")
        assertThat(paths).doesNotContain("/api/objects/$objectId/finance")
    }

    @Test
    fun `a changed object is fetched again`() = runTest {
        db.load()
        engine.sync()

        server.details = server.details.copy(title = "Кухня 2", updatedAt = server.later)
        server.objects = listOf(server.details)
        engine.sync()

        assertThat(db.objects.rows.value.getValue(objectId).title).isEqualTo("Кухня 2")
    }

    @Test
    fun `an object gone from the server leaves the phone with everything of it`() = runTest {
        db.load()
        engine.sync()

        server.objects = emptyList()
        engine.sync()

        assertThat(db.objects.rows.value).isEmpty()
        assertThat(db.materials.rows.value).isEmpty()
        assertThat(db.terms.rows.value).isEmpty()
    }

    @Test
    fun `queued changes go up in the order they were made, under the ids the phone chose`() = runTest {
        db.load()
        val newId = "22222222-2222-2222-2222-222222222222"
        val contactId = "33333333-3333-3333-3333-333333333333"
        db.outbox.enqueue(Operation.CreateObject(request("Тверская, 5").copy(id = newId)))
        val contact = ContactRequestDto(name = "Анна", role = ContactRoleDto.CLIENT, id = contactId)
        db.outbox.enqueue(Operation.CreateContact(newId, contact))

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Synced)
        val paths = server.writes.map { it.url.encodedPath }
        assertThat(paths).containsExactly("/api/objects", "/api/objects/$newId/contacts").inOrder()
        assertThat(server.writes[0].bodyText()).contains(newId)
        assertThat(server.writes[1].bodyText()).contains(contactId)
        assertThat(db.outbox.snapshot()).isEmpty()
    }

    @Test
    fun `without an answer nothing is lost and the copy is untouched`() = runTest {
        db.load()
        val row =
            ObjectRow(
                objectId,
                address = "Ленина, 1",
                status = ObjectStatusDto.PLANNED,
                createdAt = server.at,
                updatedAt = server.at,
            )
        db.objects.upsert(row)
        db.outbox.enqueue(Operation.DeleteObject("x"))
        server.offline = true

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Offline)
        assertThat(engine.state.value.offline).isTrue()
        assertThat(db.outbox.snapshot().single().state).isEqualTo(OperationState.PENDING)
        assertThat(db.objects.rows.value).hasSize(1)
    }

    @Test
    fun `a change the server refuses is marked, and the line carries on`() = runTest {
        db.load()
        db.outbox.enqueue(Operation.UpdateObject(objectId, request(address = "")))
        server.writeStatus = HttpStatusCode.BadRequest

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Synced)
        val left = db.outbox.snapshot()
        assertThat(left.single().state).isEqualTo(OperationState.FAILED)
        assertThat(left.single().reason).isEqualTo("validation")
    }

    @Test
    fun `one refused change does not hold up the next`() = runTest {
        db.load()
        db.outbox.enqueue(Operation.UpdateObject(objectId, request(address = "")))
        db.outbox.enqueue(Operation.DeleteContact("c1"))
        server.writeStatus = HttpStatusCode.BadRequest

        engine.sync()

        assertThat(server.writes).hasSize(2)
        assertThat(db.outbox.snapshot().map { it.state }).containsExactly(OperationState.FAILED, OperationState.FAILED)
    }

    @Test
    fun `deleting what is already gone counts as done`() = runTest {
        db.load()
        db.outbox.enqueue(Operation.DeleteObject("gone"))
        server.writeStatus = HttpStatusCode.NotFound

        engine.sync()

        assertThat(db.outbox.snapshot()).isEmpty()
    }

    @Test
    fun `a server error is retried a few times and then given up on`() = runTest {
        db.load()
        db.outbox.enqueue(Operation.DeleteObject("x"))
        server.writeStatus = HttpStatusCode.InternalServerError

        repeat(MAX_TRIES - 1) {
            assertThat(engine.sync()).isEqualTo(SyncOutcome.Offline)
        }
        assertThat(db.outbox.snapshot().single().state).isEqualTo(OperationState.PENDING)

        engine.sync()

        assertThat(db.outbox.snapshot().single().state).isEqualTo(OperationState.FAILED)
        assertThat(db.outbox.snapshot().single().reason).isEqualTo("server_500")
    }

    @Test
    fun `a row with a change waiting keeps the phone's version when the server has newer data`() = runTest {
        db.load()
        engine.sync()
        db.objects.change { it[objectId] = it.getValue(objectId).copy(title = "Моё название") }
        db.outbox.enqueue(Operation.UpdateObject(objectId, request(title = "Моё название")))
        server.offline = true
        engine.sync()
        server.offline = false
        server.writeStatus = HttpStatusCode.ServiceUnavailable
        server.details = server.details.copy(title = "С сервера", updatedAt = server.later)
        server.objects = listOf(server.details)

        engine.sync()

        assertThat(db.objects.rows.value.getValue(objectId).title).isEqualTo("Моё название")
        assertThat(Keys.obj(objectId)).isIn(db.outbox.dirtyKeys())
    }

    @Test
    fun `a refused token stops everything and says so`() = runTest {
        db.load()
        db.outbox.enqueue(Operation.DeleteObject("x"))
        server.writeStatus = HttpStatusCode.Unauthorized

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Unauthorized)
        assertThat(engine.state.value.unauthorized).isTrue()
        assertThat(db.outbox.snapshot().single().state).isEqualTo(OperationState.PENDING)
    }

    @Test
    fun `a copy pulled from another server is dropped`() = runTest {
        db.load()
        engine.sync()
        assertThat(db.objects.rows.value).hasSize(1)

        serverKey = "http://two"
        server.offline = true
        engine.sync()

        assertThat(db.objects.rows.value).isEmpty()
    }

    @Test
    fun `a copy is kept, not dropped, while changes are still waiting`() = runTest {
        db.load()
        engine.sync()
        db.outbox.enqueue(Operation.DeleteContact("c1"))

        serverKey = "http://two"
        server.offline = true
        engine.sync()

        assertThat(db.objects.rows.value).hasSize(1)
    }

    @Test
    fun `a queued picture is sent with its id and bytes, and the bytes are dropped afterwards`() = runTest {
        db.load()
        val photoId = "44444444-4444-4444-4444-444444444444"
        db.blobs.put("p1.jpg", byteArrayOf(1, 2, 3))
        db.outbox.enqueue(Operation.UploadPhoto(objectId, photoId, AttachmentKindDto.RECEIPT, "p1.jpg", "image/jpeg"))

        engine.sync()

        val upload = server.writes.single()
        assertThat(upload.url.encodedPath).isEqualTo("/api/objects/$objectId/photos")
        assertThat(upload.url.parameters["id"]).isEqualTo(photoId)
        assertThat(upload.url.parameters["kind"]).isEqualTo("RECEIPT")
        assertThat(db.blobs.get("p1.jpg")).isNull()
        assertThat(db.outbox.snapshot()).isEmpty()
    }

    private companion object {
        const val MAX_TRIES = 5
    }
}
