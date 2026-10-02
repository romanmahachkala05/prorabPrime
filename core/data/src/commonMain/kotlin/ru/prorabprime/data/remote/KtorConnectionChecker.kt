package ru.prorabprime.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import ru.prorabprime.contract.AccountDto
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.data.network.InvalidServerAddressException
import ru.prorabprime.data.network.apiCall
import ru.prorabprime.data.network.parseServerAddress
import ru.prorabprime.data.repository.toDomain
import ru.prorabprime.domain.ConnectionChecker
import ru.prorabprime.domain.model.Account
import ru.prorabprime.domain.model.ServerSettings

/**
 * Checks settings that are not saved yet, so it addresses the server directly instead of going
 * through the placeholder host: `/health` proves the server answers, an authenticated list call
 * proves the token.
 */
internal class KtorConnectionChecker(
    private val client: HttpClient,
) : ConnectionChecker {

    override suspend fun check(settings: ServerSettings): Result<Account?> = apiCall {
        val base = parseServerAddress(settings.baseUrl)?.toString()?.trimEnd('/')
            ?: throw InvalidServerAddressException(settings.baseUrl)
        client.get(base + ApiPaths.HEALTH)
        client.get(base + ApiPaths.OBJECTS) { bearerAuth(settings.apiToken) }
        base
    }.map { base ->
        // The token is proven; whose it is is a bonus, and an older server that does not say is no failure.
        apiCall { client.get(base + ApiPaths.ACCOUNT) { bearerAuth(settings.apiToken) }.body<AccountDto>() }
            .getOrNull()?.toDomain()
    }
}
