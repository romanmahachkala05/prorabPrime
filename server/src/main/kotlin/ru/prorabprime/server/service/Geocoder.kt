package ru.prorabprime.server.service

import io.ktor.util.logging.KtorSimpleLogger
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.prorabprime.server.model.Coordinates

/** Turns an address into a point on the map. Anything that goes wrong is "not found", never a failure. */
interface Geocoder {
    suspend fun locate(address: String): Coordinates?
}

/** Used when the geocoder is switched off: nothing is ever located. */
object NoGeocoder : Geocoder {
    override suspend fun locate(address: String): Coordinates? = null
}

/**
 * OpenStreetMap's Nominatim, or any server that speaks its `search` API. Nominatim's usage policy
 * asks for an identifying User-Agent and at most one request a second; an object is geocoded
 * when it is created or its address changes, which is nowhere near that.
 */
class NominatimGeocoder(
    private val baseUrl: String,
    private val dispatcher: CoroutineDispatcher,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(
        Duration.ofSeconds(CONNECT_TIMEOUT_S),
    ).build(),
) : Geocoder {

    override suspend fun locate(address: String): Coordinates? = withContext(dispatcher) {
        try {
            val query = URLEncoder.encode(address, Charsets.UTF_8)
            val request = HttpRequest.newBuilder(
                URI.create("${baseUrl.trimEnd('/')}/search?format=jsonv2&limit=1&q=$query"),
            )
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_S))
                .GET()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == HTTP_OK) parse(response.body()) else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            log.warn("Could not geocode an address", failure)
            null
        }
    }

    private fun parse(body: String): Coordinates? {
        val first = (Json.parseToJsonElement(body) as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        val latitude = first.number("lat") ?: return null
        val longitude = first.number("lon") ?: return null
        return Coordinates(latitude, longitude).takeIf { it.latitude in LAT_RANGE && it.longitude in LON_RANGE }
    }

    private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.let { it.doubleOrNull }

    private companion object {
        fun defaultClient(): HttpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_S)).build()

        const val USER_AGENT = "ProrabPrime/1.0 (self-hosted renovation app)"
        const val CONNECT_TIMEOUT_S = 3L
        const val REQUEST_TIMEOUT_S = 5L
        const val HTTP_OK = 200
        val LAT_RANGE = -90.0..90.0
        val LON_RANGE = -180.0..180.0
        val log = KtorSimpleLogger(NominatimGeocoder::class.qualifiedName!!)
    }
}
