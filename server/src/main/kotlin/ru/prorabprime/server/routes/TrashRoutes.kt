package ru.prorabprime.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.time.Clock
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.server.auth.owner
import ru.prorabprime.server.service.TrashService

fun Route.trashRoutes() {
    val service by inject<TrashService>()
    val clock by inject<Clock>()

    get(ApiPaths.TRASH) {
        call.respond(service.list(call.owner).toDto(clock.now()))
    }
    delete(ApiPaths.TRASH) {
        service.empty(call.owner)
        call.respond(HttpStatusCode.NoContent)
    }
    post(ApiPaths.TRASH_OBJECT_RESTORE) {
        service.restoreObject(call.owner, call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    delete(ApiPaths.TRASH_OBJECT) {
        service.purgeObject(call.owner, call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    post(ApiPaths.TRASH_PHOTO_RESTORE) {
        service.restorePhoto(call.owner, call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    delete(ApiPaths.TRASH_PHOTO) {
        service.purgePhoto(call.owner, call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
}
