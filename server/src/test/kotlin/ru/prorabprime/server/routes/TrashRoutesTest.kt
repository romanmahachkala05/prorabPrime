package ru.prorabprime.server.routes

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import org.junit.Test
import org.koin.dsl.module
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.TrashDto
import ru.prorabprime.server.TEST_TOKEN
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.di.serviceModule
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeImageProcessor
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FakeReceiptReader
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor
import ru.prorabprime.server.fakes.financeFakes
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.PhotoRecord
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.storage.FileStorage
import ru.prorabprime.server.storage.ImageProcessor
import ru.prorabprime.server.storage.ReceiptReader
import ru.prorabprime.server.testServer

class TrashRoutesTest {

    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val storage = FakeFileStorage()
    private val objectId: UUID = UUID.randomUUID()

    private fun server(block: suspend (HttpClient) -> Unit) {
        objects.records[objectId] = ObjectRecord(
            id = objectId,
            fields = ObjectFields("Кухня", "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
            coverPhotoId = null,
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
        val fakes = module {
            single<ObjectRepository> { objects }
            single<PhotoRepository> { photos }
            single<ContactRepository> { FakeContactRepository() }
            single<Transactor> { ImmediateTransactor }
            single<Clock> { FixedClock() }
            single<FileStorage> { storage }
            single<ImageProcessor> { FakeImageProcessor() }
            single<ReceiptReader> { FakeReceiptReader() }
        }
        testServer(koinModules = listOf(fakes, financeFakes(), serviceModule)) { client -> block(client) }
    }

    private suspend fun HttpClient.trash(): TrashDto = get("/api/trash") { bearerAuth(TEST_TOKEN) }.body()

    @Test
    fun `deleting an object moves it to the trash with the days it has left`() = server { client ->
        assertThat(client.delete("/api/objects/$objectId") { bearerAuth(TEST_TOKEN) }.status)
            .isEqualTo(HttpStatusCode.NoContent)

        val trash = client.trash()

        assertThat(trash.objects.map { it.id }).containsExactly(objectId.toString())
        assertThat(trash.objects.single().title).isEqualTo("Кухня")
        assertThat(trash.objects.single().daysLeft).isEqualTo(30)
        assertThat(client.get("/api/objects/$objectId") { bearerAuth(TEST_TOKEN) }.status)
            .isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `an object is restored from the trash and then shows again`() = server { client ->
        client.delete("/api/objects/$objectId") { bearerAuth(TEST_TOKEN) }

        val restored = client.post("/api/trash/objects/$objectId/restore") { bearerAuth(TEST_TOKEN) }

        assertThat(restored.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.trash().objects).isEmpty()
        assertThat(client.get("/api/objects/$objectId") { bearerAuth(TEST_TOKEN) }.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `an object is removed for good from the trash, and a live one is not found there`() = server { client ->
        val notThere = client.delete("/api/trash/objects/$objectId") { bearerAuth(TEST_TOKEN) }
        assertThat(notThere.status).isEqualTo(HttpStatusCode.NotFound)

        client.delete("/api/objects/$objectId") { bearerAuth(TEST_TOKEN) }
        val removed = client.delete("/api/trash/objects/$objectId") { bearerAuth(TEST_TOKEN) }

        assertThat(removed.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.trash().objects).isEmpty()
        assertThat(objects.records).isEmpty()
    }

    @Test
    fun `a deleted photo is in the trash, can be restored, and can be removed for good`() = server { client ->
        val photoId = UUID.randomUUID()
        photos.records[photoId] = aPhoto(photoId)

        client.delete("/api/photos/$photoId") { bearerAuth(TEST_TOKEN) }
        val inTrash = client.trash().photos.single()
        assertThat(inTrash.id).isEqualTo(photoId.toString())
        assertThat(inTrash.objectTitle).isEqualTo("Кухня")
        assertThat(inTrash.thumbUrl).isEqualTo("/files/$objectId/${photoId}_thumb.jpg")

        assertThat(client.post("/api/trash/photos/$photoId/restore") { bearerAuth(TEST_TOKEN) }.status)
            .isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.trash().photos).isEmpty()

        client.delete("/api/photos/$photoId") { bearerAuth(TEST_TOKEN) }
        assertThat(client.delete("/api/trash/photos/$photoId") { bearerAuth(TEST_TOKEN) }.status)
            .isEqualTo(HttpStatusCode.NoContent)
        assertThat(photos.records).isEmpty()
    }

    @Test
    fun `emptying the trash removes everything in it`() = server { client ->
        client.delete("/api/objects/$objectId") { bearerAuth(TEST_TOKEN) }

        assertThat(client.delete("/api/trash") { bearerAuth(TEST_TOKEN) }.status).isEqualTo(HttpStatusCode.NoContent)

        assertThat(client.trash().objects).isEmpty()
        assertThat(objects.records).isEmpty()
    }

    @Test
    fun `the trash is behind the token`() = server { client ->
        assertThat(client.get("/api/trash").status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(client.delete("/api/trash").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `the days left count a started day and never drop below one`() {
        val at = FIXED_NOW
        assertThat(daysLeft(at, at)).isEqualTo(30)
        assertThat(daysLeft(at, at + 1.days)).isEqualTo(29)
        assertThat(daysLeft(at, at + 29.days)).isEqualTo(1)
        assertThat(daysLeft(at, at + 40.days)).isEqualTo(1)
    }

    private fun aPhoto(id: UUID) = PhotoRecord(
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
    )
}
