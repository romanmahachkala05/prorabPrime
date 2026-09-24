package ru.prorabprime.server.routes

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlin.time.Clock
import org.junit.Test
import org.koin.dsl.module
import ru.prorabprime.contract.ErrorCode
import ru.prorabprime.contract.ErrorDto
import ru.prorabprime.contract.FieldErrorDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.ObjectCreatedDto
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.ObjectSummaryDto
import ru.prorabprime.server.TEST_TOKEN
import ru.prorabprime.server.di.serviceModule
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.aPhotoRecord
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.testServer

class ObjectRoutesTest {

    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val fakes = module {
        single<ObjectRepository> { objects }
        single<PhotoRepository> { photos }
        single<Clock> { FixedClock() }
    }

    private fun server(block: suspend (HttpClient) -> Unit) =
        testServer(koinModules = listOf(fakes, serviceModule)) { client -> block(client) }

    private suspend fun HttpClient.create(address: String, title: String? = null): String = post("/api/objects") {
        bearerAuth(TEST_TOKEN)
        contentType(ContentType.Application.Json)
        setBody(ObjectRequestDto(title = title, address = address, status = ObjectStatusDto.IN_PROGRESS))
    }.body<ObjectCreatedDto>().id

    private suspend fun HttpClient.authedGet(path: String): HttpResponse = get(path) { bearerAuth(TEST_TOKEN) }

    @Test
    fun `the objects list needs a token`() = server { client ->
        assertThat(client.get("/api/objects").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `creating answers 201 with the new id, which can then be read`() = server { client ->
        val id = client.create("Тверская, 5", title = "Кухня")

        val details = client.authedGet("/api/objects/$id").body<ObjectDetailsDto>()

        assertThat(details.title).isEqualTo("Кухня")
        assertThat(details.address).isEqualTo("Тверская, 5")
        assertThat(details.photos).isEmpty()
    }

    @Test
    fun `an invalid object is 400 naming the field`() = server { client ->
        val response = client.post("/api/objects") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(ObjectRequestDto(address = " ", status = ObjectStatusDto.PLANNED))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.body<ErrorDto>().fieldErrors)
            .containsExactly(FieldErrorDto(ObjectFieldDto.ADDRESS, FieldProblemDto.REQUIRED))
    }

    @Test
    fun `the list searches, sorts and shows the cover thumbnail as a relative url`() = server { client ->
        val lenina = client.create("ул. Ленина, 1")
        client.create("Тверская, 5")
        client.create("пр. Ленина, 7")
        val photo = aPhotoRecord(UUID.fromString(lenina))
        photos.records[photo.id] = photo
        val record = objects.records.getValue(UUID.fromString(lenina))
        objects.records[record.id] = record.copy(coverPhotoId = photo.id)

        val found = client.authedGet("/api/objects?search=ЛЕНИНА&sort=address&order=desc")
            .body<List<ObjectSummaryDto>>()

        assertThat(found.map { it.address }).containsExactly("ул. Ленина, 1", "пр. Ленина, 7").inOrder()
        assertThat(found.first().coverThumbUrl).isEqualTo("/files/$lenina/${photo.thumbFileName}")
        assertThat(found.first().photoCount).isEqualTo(1)
    }

    @Test
    fun `an unknown sort is 400`() = server { client ->
        val response = client.authedGet("/api/objects?sort=price")

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.body<ErrorDto>().code).isEqualTo(ErrorCode.VALIDATION)
    }

    @Test
    fun `a malformed id is 404`() = server { client ->
        assertThat(client.authedGet("/api/objects/not-a-uuid").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `updating replaces the fields`() = server { client ->
        val id = client.create("Тверская, 5")

        val response = client.put("/api/objects/$id") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(ObjectRequestDto(address = "Тверская, 7", status = ObjectStatusDto.DONE, notes = "сдан"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val details = response.body<ObjectDetailsDto>()
        assertThat(details.address).isEqualTo("Тверская, 7")
        assertThat(details.status).isEqualTo(ObjectStatusDto.DONE)
        assertThat(details.notes).isEqualTo("сдан")
    }

    @Test
    fun `deleting answers 204 and the object is gone`() = server { client ->
        val id = client.create("Тверская, 5")

        val response = client.delete("/api/objects/$id") { bearerAuth(TEST_TOKEN) }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.authedGet("/api/objects/$id").status).isEqualTo(HttpStatusCode.NotFound)
    }
}
