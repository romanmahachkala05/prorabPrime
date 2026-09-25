package ru.prorabprime.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.data.network.InvalidServerAddressException
import ru.prorabprime.data.network.apiCall
import ru.prorabprime.data.network.parseServerAddress
import ru.prorabprime.domain.ConnectionChecker
import ru.prorabprime.domain.model.ServerSettings

/**
 * Checks settings that are not saved yet, so it addresses the server directly instead of going
 * through the placeholder host: `/health` proves the server answers, an authenticated list call
 * proves the token.
 */
internal class KtorConnectionChecker(
    private val client: HttpClient,
) : ConnectionChecker {

    override suspend fun check(settings: ServerSettings): Result<Unit> = apiCall {
        val base = parseServerAddress(settings.baseUrl)?.toString()?.trimEnd('/')
            ?: throw InvalidServerAddressException(settings.baseUrl)
        client.get(base + ApiPaths.HEALTH)
        client.get(base + ApiPaths.OBJECTS) { bearerAuth(settings.apiToken) }
    }.map { }
}
