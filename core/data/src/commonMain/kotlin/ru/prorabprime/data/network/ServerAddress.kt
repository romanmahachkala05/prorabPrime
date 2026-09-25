package ru.prorabprime.data.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.io.IOException
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.model.ServerSettings

/**
 * Every request to our server is made against this placeholder host; [ServerAddress] swaps in
 * the configured address and token as the request goes out (ADR-0008). `.invalid` never
 * resolves (RFC 2606), so a request that somehow skipped the plugin fails rather than going
 * somewhere else.
 */
internal const val SERVER_HOST = "prorab-server.invalid"
const val SERVER_BASE = "http://$SERVER_HOST"

/** The URL the app's `HttpClient` — and so Coil — loads [this] file from. */
fun ServerFilePath.toRequestUrl(): String = SERVER_BASE + value

class ServerAddressConfig {
    /** Read on every request, so a change in the settings applies to the very next one. */
    var settings: suspend () -> ServerSettings = { error("ServerAddress needs a settings source") }
}

/** Thrown when the configured address is not an http(s) URL; classified as a network failure. */
class InvalidServerAddressException(
    address: String,
) : IOException("Not a server address: '$address'")

/**
 * Resolves requests to [SERVER_HOST] against the configured server and adds its token.
 * Requests to any other host pass through untouched — the token never leaves for them.
 */
val ServerAddress = createClientPlugin("ServerAddress", ::ServerAddressConfig) {
    val settings = pluginConfig.settings

    onRequest { request, _ ->
        if (request.url.host != SERVER_HOST) return@onRequest
        val current = settings()
        val base = parseServerAddress(current.baseUrl) ?: throw InvalidServerAddressException(current.baseUrl)
        request.url.protocol = base.protocol
        request.url.host = base.host
        request.url.port = base.port
        request.url.encodedPath = base.encodedPath.trimEnd('/') + request.url.encodedPath
        request.headers[HttpHeaders.Authorization] = "Bearer ${current.apiToken}"
    }
}

/** [address] as a base URL, or null unless it is an absolute http(s) URL with a host. */
fun parseServerAddress(address: String): Url? {
    val url = runCatching { Url(address.trim()) }.getOrNull() ?: return null
    val schemeGiven = address.trim().startsWith("http://") || address.trim().startsWith("https://")
    val supported = url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS
    return url.takeIf { schemeGiven && supported && it.host.isNotBlank() }
}
