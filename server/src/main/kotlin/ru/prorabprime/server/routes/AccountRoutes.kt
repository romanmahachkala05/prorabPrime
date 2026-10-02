package ru.prorabprime.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.AccountDto
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.server.auth.owner
import ru.prorabprime.server.service.ProfileService

fun Route.accountRoutes() {
    val service by inject<ProfileService>()

    get(ApiPaths.ACCOUNT) {
        val info = service.get(call.owner).getOrThrow()
        call.respond(AccountDto(info.name, info.usedBytes, info.limitBytes))
    }
}
