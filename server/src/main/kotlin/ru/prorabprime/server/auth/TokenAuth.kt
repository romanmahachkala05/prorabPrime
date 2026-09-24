package ru.prorabprime.server.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import java.security.MessageDigest

/** The name of the provider every `/api` and `/files` route is wrapped in. */
const val API_AUTH = "api-token"

private const val OWNER = "owner"

fun Application.installTokenAuth(token: String) {
    val expected = token.encodeToByteArray()
    install(Authentication) {
        bearer(API_AUTH) {
            authenticate { credential ->
                // Constant time, so response timing does not reveal how much of a guess matched.
                val matches = MessageDigest.isEqual(credential.token.encodeToByteArray(), expected)
                if (matches) UserIdPrincipal(OWNER) else null
            }
        }
    }
}
