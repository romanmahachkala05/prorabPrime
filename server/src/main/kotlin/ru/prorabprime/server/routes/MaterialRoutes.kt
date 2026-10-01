package ru.prorabprime.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.IdDto
import ru.prorabprime.contract.MaterialDto
import ru.prorabprime.contract.MaterialRequestDto
import ru.prorabprime.server.model.MaterialRecord
import ru.prorabprime.server.service.MaterialService

fun Route.materialRoutes() {
    val service by inject<MaterialService>()

    get(ApiPaths.OBJECT_MATERIALS) {
        call.respond(service.list(call.uuidParam(ApiParams.ID)).getOrThrow().map { it.toDto() })
    }
    post(ApiPaths.OBJECT_MATERIALS) {
        val created = service.create(call.uuidParam(ApiParams.ID), call.receive<MaterialRequestDto>()).getOrThrow()
        call.respond(HttpStatusCode.Created, IdDto(created.id.toString()))
    }
    post(ApiPaths.OBJECT_MATERIAL_DEFAULTS) {
        call.respond(service.addDefaults(call.uuidParam(ApiParams.ID)).getOrThrow().map { it.toDto() })
    }
    put(ApiPaths.MATERIAL) {
        service.update(call.uuidParam(ApiParams.ID), call.receive<MaterialRequestDto>()).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    delete(ApiPaths.MATERIAL) {
        service.delete(call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
}

fun MaterialRecord.toDto() = MaterialDto(id.toString(), fields.title, fields.status)
