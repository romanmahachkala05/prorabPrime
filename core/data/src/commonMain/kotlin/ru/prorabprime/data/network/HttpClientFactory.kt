package ru.prorabprime.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ru.prorabprime.domain.model.ServerSettings

internal val ApiJson = Json {
    // A newer server may add fields; an older app must keep working.
    ignoreUnknownKeys = true
}

private const val CONNECT_TIMEOUT_MS = 10_000L
private const val SOCKET_TIMEOUT_MS = 30_000L

// Generous: a photo upload over a slow site connection.
private const val REQUEST_TIMEOUT_MS = 120_000L

/** The one client for the API and for images (ADR-0003). */
fun createHttpClient(engine: HttpClientEngine, settings: suspend () -> ServerSettings): HttpClient =
    HttpClient(engine) {
        // Non-2xx responses throw, so the error mapper sees every failure in one place.
        expectSuccess = true
        install(ContentNegotiation) { json(ApiJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(ServerAddress) { this.settings = settings }
    }
