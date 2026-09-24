package ru.prorabprime.server.routes

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.dsl.module
import ru.prorabprime.contract.ErrorCode
import ru.prorabprime.contract.ErrorDto
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PhotoDto
import ru.prorabprime.contract.PhotoLimits
import ru.prorabprime.contract.SetCoverRequestDto
import ru.prorabprime.server.TEST_TOKEN
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.di.serviceModule
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.storage.FileStorage
import ru.prorabprime.server.storage.LocalFileStorage
import ru.prorabprime.server.storage.TestImages
import ru.prorabprime.server.testServer

/** Real storage in a temporary folder and the real image processor; fake repositories. */
class PhotoRoutesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val objectId: UUID = UUID.randomUUID()

    private fun server(block: suspend (HttpClient) -> Unit) {
        objects.records[objectId] = ObjectRecord(
            id = objectId,
            fields = ObjectFields(null, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
            coverPhotoId = null,
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
        val fakes = module {
            single<ObjectRepository> { objects }
            single<PhotoRepository> { photos }
            single<Transactor> { ImmediateTransactor }
            single<Clock> { FixedClock() }
            single<FileStorage> { LocalFileStorage(folder.root.toPath(), Dispatchers.IO) }
        }
        testServer(koinModules = listOf(fakes, serviceModule)) { client -> block(client) }
    }

    private suspend fun HttpClient.upload(
        bytes: ByteArray,
        target: UUID = objectId,
        field: String = "file",
    ): HttpResponse = submitFormWithBinaryData(
        url = "/api/objects/$target/photos",
        formData = formData {
            append(
                field,
                bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                },
            )
        },
    ) { bearerAuth(TEST_TOKEN) }

    private suspend fun HttpClient.details(): ObjectDetailsDto =
        get("/api/objects/$objectId") { bearerAuth(TEST_TOKEN) }.body()

    @Test
    fun `an upload answers 201 and the photo shows up as the cover`() = server { client ->
        val response = client.upload(TestImages.jpeg(800, 600))

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val photo = response.body<PhotoDto>()
        assertThat(photo.url).isEqualTo("/files/$objectId/${photo.id}.jpg")
        assertThat(photo.thumbUrl).isEqualTo("/files/$objectId/${photo.id}_thumb.jpg")
        assertThat(photo.width to photo.height).isEqualTo(800 to 600)
        val details = client.details()
        assertThat(details.photos.map { it.id }).containsExactly(photo.id)
        assertThat(details.coverPhotoId).isEqualTo(photo.id)
    }

    @Test
    fun `an uploaded file is served back byte for byte, under the token`() = server { client ->
        val bytes = TestImages.jpeg(64, 48)
        val photo = client.upload(bytes).body<PhotoDto>()

        val served = client.get(photo.url) { bearerAuth(TEST_TOKEN) }

        assertThat(served.status).isEqualTo(HttpStatusCode.OK)
        assertThat(served.contentType()?.withoutParameters()).isEqualTo(ContentType.Image.JPEG)
        assertThat(served.headers[HttpHeaders.CacheControl]).contains("immutable")
        assertThat(served.readRawBytes()).isEqualTo(bytes)
        assertThat(client.get(photo.url).status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `a path that tries to leave the storage is 404`() = server { client ->
        val photo = client.upload(TestImages.jpeg(10, 10)).body<PhotoDto>()

        val encodedDots = client.get("/files/$objectId/..%2F..%2F${photo.id}.jpg") { bearerAuth(TEST_TOKEN) }
        val notAnId = client.get("/files/..%2F$objectId/${photo.id}.jpg") { bearerAuth(TEST_TOKEN) }

        assertThat(encodedDots.status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(notAnId.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `a request without the file part is 400`() = server { client ->
        val response = client.upload(TestImages.jpeg(10, 10), field = "picture")

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `a file that is not an image is 415`() = server { client ->
        val response = client.upload("definitely not a jpeg".toByteArray())

        assertThat(response.status).isEqualTo(HttpStatusCode.UnsupportedMediaType)
        assertThat(response.body<ErrorDto>().code).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA)
    }

    @Test
    fun `a file over the limit is 413`() = server { client ->
        val response = client.upload(ByteArray(PhotoLimits.MAX_UPLOAD_BYTES.toInt() + 1))

        assertThat(response.status).isEqualTo(HttpStatusCode.PayloadTooLarge)
    }

    @Test
    fun `an upload to an unknown object is 404`() = server { client ->
        assertThat(client.upload(TestImages.jpeg(10, 10), target = UUID.randomUUID()).status)
            .isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `the cover can be moved to another photo`() = server { client ->
        client.upload(TestImages.jpeg(10, 10))
        val second = client.upload(TestImages.jpeg(10, 10)).body<PhotoDto>()

        val response = client.put("/api/objects/$objectId/cover") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(SetCoverRequestDto(second.id))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.details().coverPhotoId).isEqualTo(second.id)
    }

    @Test
    fun `a cover that is not a photo id is 400`() = server { client ->
        val response = client.put("/api/objects/$objectId/cover") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(SetCoverRequestDto("nope"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `deleting a photo answers 204 and removes its file`() = server { client ->
        val photo = client.upload(TestImages.jpeg(10, 10)).body<PhotoDto>()

        val response = client.delete("/api/photos/${photo.id}") { bearerAuth(TEST_TOKEN) }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.get(photo.url) { bearerAuth(TEST_TOKEN) }.status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(client.details().photos).isEmpty()
    }
}
