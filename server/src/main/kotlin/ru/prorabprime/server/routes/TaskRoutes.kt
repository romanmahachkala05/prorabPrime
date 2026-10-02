package ru.prorabprime.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ApiQuery
import ru.prorabprime.contract.IdDto
import ru.prorabprime.contract.TaskDto
import ru.prorabprime.contract.TaskRequestDto
import ru.prorabprime.server.auth.owner
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord
import ru.prorabprime.server.service.TaskService

fun Route.taskRoutes() {
    val service by inject<TaskService>()

    get(ApiPaths.TASKS) {
        call.respond(service.list(call.owner, call.taskQuery()).map { it.toDto() })
    }
    post(ApiPaths.TASKS) {
        val created = service.create(call.owner, call.receive<TaskRequestDto>()).getOrThrow()
        call.respond(HttpStatusCode.Created, IdDto(created.id.toString()))
    }
    put(ApiPaths.TASK) {
        service.update(call.owner, call.uuidParam(ApiParams.ID), call.receive<TaskRequestDto>()).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    delete(ApiPaths.TASK) {
        service.delete(call.owner, call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
}

fun TaskRecord.toDto() = TaskDto(
    id = id.toString(),
    title = fields.title,
    day = fields.day.toString(),
    remindAtMinutes = fields.remindAtMinutes,
    done = fields.done,
)

private fun RoutingCall.taskQuery(): TaskQuery {
    val params = request.queryParameters
    return TaskQuery(
        from = params[ApiQuery.FROM]?.let { day(ApiQuery.FROM, it) },
        to = params[ApiQuery.TO]?.let { day(ApiQuery.TO, it) },
        openOnly = params[ApiQuery.OPEN_ONLY].toBoolean(),
    )
}

private fun day(name: String, value: String): LocalDate = try {
    LocalDate.parse(value)
} catch (@Suppress("SwallowedException") e: DateTimeParseException) {
    throw ServiceException(ServiceError.Validation("Unknown $name: $value"))
}
