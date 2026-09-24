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
import java.util.UUID
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ApiQuery
import ru.prorabprime.contract.ObjectCreatedDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.service.ObjectService

fun Route.objectRoutes() {
    val service by inject<ObjectService>()

    get(ApiPaths.OBJECTS) {
        call.respond(service.list(call.objectListQuery()).map { it.toSummaryDto() })
    }
    get(ApiPaths.OBJECT) {
        call.respond(service.get(call.uuidParam(ApiParams.ID)).getOrThrow().toDetailsDto())
    }
    post(ApiPaths.OBJECTS) {
        val created = service.create(call.receive<ObjectRequestDto>()).getOrThrow()
        call.respond(HttpStatusCode.Created, ObjectCreatedDto(created.id.toString()))
    }
    put(ApiPaths.OBJECT) {
        val id = call.uuidParam(ApiParams.ID)
        call.respond(service.update(id, call.receive<ObjectRequestDto>()).getOrThrow().toDetailsDto())
    }
    delete(ApiPaths.OBJECT) {
        service.delete(call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
}

/** A malformed id cannot name anything that exists, so it is a 404 like any unknown id. */
fun RoutingCall.uuidParam(name: String): UUID {
    val raw = parameters[name].orEmpty()
    return runCatching { UUID.fromString(raw) }.getOrNull()
        ?: throw ServiceException(ServiceError.NotFound("No such id: $raw"))
}

private fun RoutingCall.objectListQuery(): ObjectListQuery {
    val params = request.queryParameters
    val sort = params[ApiQuery.SORT]?.let { value ->
        SortFieldDto.entries.find { it.wireName == value } ?: invalidParameter(ApiQuery.SORT, value)
    }
    val order = params[ApiQuery.ORDER]?.let { value ->
        SortOrderDto.entries.find { it.wireName == value } ?: invalidParameter(ApiQuery.ORDER, value)
    }
    val defaults = ObjectListQuery()
    return ObjectListQuery(
        search = params[ApiQuery.SEARCH],
        sort = sort ?: defaults.sort,
        order = order ?: defaults.order,
    )
}

private fun invalidParameter(name: String, value: String): Nothing =
    throw ServiceException(ServiceError.Validation("Unknown $name: $value"))
