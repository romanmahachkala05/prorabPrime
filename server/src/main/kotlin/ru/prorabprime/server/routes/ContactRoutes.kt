package ru.prorabprime.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ContactCreatedDto
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.server.service.ContactService

fun Route.contactRoutes() {
    val service by inject<ContactService>()

    post(ApiPaths.OBJECT_CONTACTS) {
        val objectId = call.uuidParam(ApiParams.ID)
        val created = service.create(objectId, call.receive<ContactRequestDto>()).getOrThrow()
        call.respond(HttpStatusCode.Created, ContactCreatedDto(created.id.toString()))
    }
    put(ApiPaths.CONTACT) {
        service.update(call.uuidParam(ApiParams.ID), call.receive<ContactRequestDto>()).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    delete(ApiPaths.CONTACT) {
        service.delete(call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
}
