package ru.prorabprime.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import ru.prorabprime.data.network.createHttpClient
import ru.prorabprime.testing.FakeSettingsRepository

/** A client over [MockEngine] wired exactly as the app wires the real one. */
class TestHttp(
    val settings: FakeSettingsRepository = FakeSettingsRepository(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()

    val client: HttpClient = createHttpClient(
        MockEngine { request ->
            requests += request
            handler(request)
        },
    ) { settings.serverSettings.first() }
}

fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
