package ru.prorabprime.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ApiQuery
import ru.prorabprime.contract.TaskDto
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.mapper.toRequestDto
import ru.prorabprime.data.network.SERVER_BASE
import ru.prorabprime.data.network.apiCall
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Task
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId

/** The task endpoints; every call is classified by [apiCall]. */
internal class TasksApi(
    private val client: HttpClient,
) {
    /** The tasks from [from] to [to] (each end inclusive, either may be open), without the done ones if [openOnly]. */
    suspend fun list(
        from: LocalDay?,
        to: LocalDay?,
        openOnly: Boolean,
    ): Result<ImmutableList<Task>> = apiCall {
        client.get(SERVER_BASE + ApiPaths.TASKS) {
            from?.let { parameter(ApiQuery.FROM, it.toIso()) }
            to?.let { parameter(ApiQuery.TO, it.toIso()) }
            if (openOnly) parameter(ApiQuery.OPEN_ONLY, true)
        }.body<List<TaskDto>>().map { it.toDomain() }.toImmutableList()
    }

    suspend fun add(draft: TaskDraft): Result<Unit> = apiCall {
        client.post(SERVER_BASE + ApiPaths.TASKS) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun update(id: TaskId, draft: TaskDraft): Result<Unit> = apiCall {
        client.put(url(id)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun delete(id: TaskId): Result<Unit> = apiCall {
        client.delete(url(id))
    }.map { }

    private fun url(id: TaskId): String = SERVER_BASE + ApiPaths.TASK.replace("{${ApiParams.ID}}", id.value)
}
