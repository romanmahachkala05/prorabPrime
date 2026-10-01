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
import ru.prorabprime.contract.ErrorDto
import ru.prorabprime.contract.IdDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.TaskDto
import ru.prorabprime.contract.TaskRequestDto
import ru.prorabprime.server.TEST_TOKEN
import ru.prorabprime.server.di.serviceModule
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.financeFakes
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.testServer

class TaskRoutesTest {

    private val photos = FakePhotoRepository()
    private val fakes = module {
        single<ObjectRepository> { FakeObjectRepository(photos) }
        single<PhotoRepository> { photos }
        single<ContactRepository> { FakeContactRepository() }
        single<Clock> { FixedClock() }
    }

    private fun server(block: suspend (HttpClient) -> Unit) =
        testServer(koinModules = listOf(fakes, financeFakes(), serviceModule)) { client -> block(client) }

    private suspend fun HttpClient.add(request: TaskRequestDto): String = post("/api/tasks") {
        bearerAuth(TEST_TOKEN)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body<IdDto>().id

    private suspend fun HttpClient.tasks(query: String = ""): List<TaskDto> =
        get("/api/tasks$query") { bearerAuth(TEST_TOKEN) }.body()

    @Test
    fun `the tasks need a token`() = server { client ->
        assertThat(client.get("/api/tasks").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `a task is created, listed by day, ticked off, edited and deleted`() = server { client ->
        val id = client.add(TaskRequestDto("Купить плитку", "2026-09-25", 9 * 60))
        client.add(TaskRequestDto("Завтра", "2026-09-26"))

        val today = client.tasks("?from=2026-09-25&to=2026-09-25")
        assertThat(today.map { it.title }).containsExactly("Купить плитку")
        assertThat(today.single().remindAtMinutes).isEqualTo(540)
        assertThat(today.single().done).isFalse()

        val ticked = client.put("/api/tasks/$id") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(TaskRequestDto("Купить плитку", "2026-09-25", 9 * 60, done = true))
        }
        assertThat(ticked.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.tasks("?open=true").map { it.title }).containsExactly("Завтра")
        assertThat(client.tasks().first().done).isTrue()

        assertThat(client.delete("/api/tasks/$id") { bearerAuth(TEST_TOKEN) }.status)
            .isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.tasks().map { it.title }).containsExactly("Завтра")
    }

    @Test
    fun `a bad task is 400 naming the field, a bad day in the query is 400 and an unknown task is 404`() =
        server { client ->
            val bad = client.post("/api/tasks") {
                bearerAuth(TEST_TOKEN)
                contentType(ContentType.Application.Json)
                setBody(TaskRequestDto(" ", "2026-09-25"))
            }
            assertThat(bad.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(bad.body<ErrorDto>().fieldErrors.map { it.field }).containsExactly(ObjectFieldDto.TASK_TITLE)

            assertThat(client.get("/api/tasks?from=вчера") { bearerAuth(TEST_TOKEN) }.status)
                .isEqualTo(HttpStatusCode.BadRequest)

            val missing = client.put("/api/tasks/${UUID.randomUUID()}") {
                bearerAuth(TEST_TOKEN)
                contentType(ContentType.Application.Json)
                setBody(TaskRequestDto("x", "2026-09-25"))
            }
            assertThat(missing.status).isEqualTo(HttpStatusCode.NotFound)
        }
}
