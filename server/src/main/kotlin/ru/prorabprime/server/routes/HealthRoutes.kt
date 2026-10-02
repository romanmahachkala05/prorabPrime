package ru.prorabprime.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.HealthDto

/** Unauthenticated: the app's "check connection" asks this first. */
fun Route.healthRoutes() {
    get(ApiPaths.HEALTH) {
        call.respond(HealthDto(status = "ok"))
    }
}
