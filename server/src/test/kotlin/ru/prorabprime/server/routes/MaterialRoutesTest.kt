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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlin.time.Clock
import org.junit.Test
import org.koin.dsl.module
import ru.prorabprime.contract.IdDto
import ru.prorabprime.contract.MaterialDto
import ru.prorabprime.contract.MaterialRequestDto
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.TEST_TOKEN
import ru.prorabprime.server.di.serviceModule
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.financeFakes
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.service.DEFAULT_MATERIALS
import ru.prorabprime.server.testServer

class MaterialRoutesTest {

    private val objectId: UUID = UUID.randomUUID()
    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos).also {
        it.records[objectId] = ObjectRecord(
            id = objectId,
            fields = ObjectFields(null, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
            coverPhotoId = null,
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
    }
    private val fakes = module {
        single<ObjectRepository> { objects }
        single<PhotoRepository> { photos }
        single<ContactRepository> { FakeContactRepository() }
        single<Clock> { FixedClock() }
    }

    private fun server(block: suspend (HttpClient) -> Unit) =
        testServer(koinModules = listOf(fakes, financeFakes(), serviceModule)) { client -> block(client) }

    private suspend fun HttpClient.materials(): List<MaterialDto> =
        get("/api/objects/$objectId/materials") { bearerAuth(TEST_TOKEN) }.body()

    @Test
    fun `the checklist needs a token`() = server { client ->
        assertThat(client.get("/api/objects/$objectId/materials").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `a material is added, moved along its three states and deleted`() = server { client ->
        val created = client.post("/api/objects/$objectId/materials") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(MaterialRequestDto("Плитка"))
        }
        assertThat(created.status).isEqualTo(HttpStatusCode.Created)
        val id = created.body<IdDto>().id
        assertThat(client.materials().single().status).isEqualTo(MaterialStatusDto.NOT_CHOSEN)

        for (status in listOf(MaterialStatusDto.CHOSEN, MaterialStatusDto.IN_APARTMENT)) {
            val updated = client.put("/api/materials/$id") {
                bearerAuth(TEST_TOKEN)
                contentType(ContentType.Application.Json)
                setBody(MaterialRequestDto("Плитка", status))
            }
            assertThat(updated.status).isEqualTo(HttpStatusCode.NoContent)
            assertThat(client.materials().single().status).isEqualTo(status)
        }

        assertThat(client.delete("/api/materials/$id") { bearerAuth(TEST_TOKEN) }.status)
            .isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.materials()).isEmpty()
    }

    @Test
    fun `the defaults come back as the whole list, and a blank title is 400`() = server { client ->
        val defaults = client.post("/api/objects/$objectId/materials/defaults") { bearerAuth(TEST_TOKEN) }
        assertThat(defaults.status).isEqualTo(HttpStatusCode.OK)
        assertThat(defaults.body<List<MaterialDto>>()).hasSize(DEFAULT_MATERIALS.size)

        val bad = client.post("/api/objects/$objectId/materials") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(MaterialRequestDto(" "))
        }
        assertThat(bad.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `an unknown object is 404`() = server { client ->
        val response = client.get("/api/objects/${UUID.randomUUID()}/materials") { bearerAuth(TEST_TOKEN) }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }
}
