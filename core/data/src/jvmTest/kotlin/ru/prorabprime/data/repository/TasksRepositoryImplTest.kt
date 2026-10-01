package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.data.remote.TasksApi
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId

class TasksRepositoryImplTest {

    private val day = LocalDay.of(2026, 9, 25)

    private val http = TestHttp { request ->
        if (request.method == HttpMethod.Get) json(TASKS) else respond("", HttpStatusCode.NoContent)
    }
    private val invalidator = Invalidator()
    private val tasks = TasksRepositoryImpl(TasksApi(http.client), invalidator, http.settings)

    @Test
    fun `a day asks for exactly that day, both ends`() = runTest {
        val list = tasks.observeDay(day).first().getOrThrow()

        val url = http.requests.single().url
        assertThat(url.encodedPath).isEqualTo("/api/tasks")
        assertThat(url.parameters["from"]).isEqualTo("2026-09-25")
        assertThat(url.parameters["to"]).isEqualTo("2026-09-25")
        assertThat(url.parameters.contains("open")).isFalse()
        assertThat(list.map { it.title }).containsExactly("Купить плитку", "Позвонить")
        assertThat(list.first().day).isEqualTo(day)
        assertThat(list.first().remindAtMinutes).isEqualTo(540)
        assertThat(list.last().remindAtMinutes).isNull()
        assertThat(list.last().done).isTrue()
    }

    @Test
    fun `the overdue are the open tasks up to yesterday`() = runTest {
        tasks.observeOverdue(day).first().getOrThrow()

        val url = http.requests.single().url
        assertThat(url.parameters.contains("from")).isFalse()
        assertThat(url.parameters["to"]).isEqualTo("2026-09-24")
        assertThat(url.parameters["open"]).isEqualTo("true")
    }

    @Test
    fun `the open tasks from a day on are what reminders need`() = runTest {
        tasks.observeOpenFrom(day).first().getOrThrow()

        val url = http.requests.single().url
        assertThat(url.parameters["from"]).isEqualTo("2026-09-25")
        assertThat(url.parameters.contains("to")).isFalse()
        assertThat(url.parameters["open"]).isEqualTo("true")
    }

    @Test
    fun `every write goes to its endpoint and invalidates the flows`() = runTest {
        val before = invalidator.changes.value
        val draft = TaskDraft("Позвонить", day, 570)

        tasks.add(draft).getOrThrow()
        tasks.update(TaskId("t1"), draft.copy(done = true)).getOrThrow()
        tasks.delete(TaskId("t1")).getOrThrow()

        assertThat(http.requests.map { it.method to it.url.encodedPath }).containsExactly(
            HttpMethod.Post to "/api/tasks",
            HttpMethod.Put to "/api/tasks/t1",
            HttpMethod.Delete to "/api/tasks/t1",
        ).inOrder()
        assertThat(invalidator.changes.value).isEqualTo(before + 3)
    }

    private companion object {
        const val TASKS = """[
            {"id":"t1","title":"Купить плитку","day":"2026-09-25","remindAtMinutes":540},
            {"id":"t2","title":"Позвонить","day":"2026-09-25","done":true}
        ]"""
    }
}
