package ru.prorabprime.server.auth

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.service.AccountService

/** The name of the provider every `/api` and `/files` route is wrapped in. */
const val API_AUTH = "api-token"

/** Who is calling: the account the bearer token belongs to. */
class UserPrincipal(
    val owner: OwnerId,
) : Principal

/** The account of an authenticated call. Whose data a route touches comes from here and from nowhere else. */
val ApplicationCall.owner: OwnerId
    get() = checkNotNull(principal<UserPrincipal>()) { "A route outside the authenticated block asked for its owner" }
        .owner

fun Application.installTokenAuth(accounts: AccountService) {
    install(Authentication) {
        bearer(API_AUTH) {
            authenticate { credential -> accounts.authenticate(credential.token)?.let(::UserPrincipal) }
        }
    }
}
