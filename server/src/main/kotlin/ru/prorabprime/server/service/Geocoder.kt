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
import kotlinx.serialization.json.jsonPrimitive
import ru.prorabprime.server.model.Coordinates

/** Turns an address into a point on the map. Anything that goes wrong is "not found", never a failure. */
interface Geocoder {
    suspend fun locate(address: String): Coordinates?

    /** The street address at a point, or null when nothing is known there. */
    suspend fun addressAt(point: Coordinates): String?
}

/** Used when the geocoder is switched off: nothing is ever located. */
object NoGeocoder : Geocoder {
    override suspend fun locate(address: String): Coordinates? = null

    override suspend fun addressAt(point: Coordinates): String? = null
}

/**
 * OpenStreetMap's Nominatim, or any server that speaks its `search` and `reverse` API. Nominatim's
 * usage policy asks for an identifying User-Agent and at most one request a second; an object is
 * geocoded when it is created or its address changes, which is nowhere near that.
 */
class NominatimGeocoder(
    private val baseUrl: String,
    private val dispatcher: CoroutineDispatcher,
    /** Where addresses without a city are looked for first; results elsewhere are still accepted. */
    private val near: Coordinates? = null,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(
        Duration.ofSeconds(CONNECT_TIMEOUT_S),
    ).build(),
) : Geocoder {

    override suspend fun locate(address: String): Coordinates? {
        val query = URLEncoder.encode(address, Charsets.UTF_8)
        return fetch("search?format=jsonv2&limit=1&q=$query${viewbox()}")?.let(::parse)
    }

    override suspend fun addressAt(point: Coordinates): String? =
        fetch("reverse?format=jsonv2&zoom=18&accept-language=ru&lat=${point.latitude}&lon=${point.longitude}")
            ?.let(::parseAddress)

    /** The body of a successful answer; every failure, however it happens, is null. */
    private suspend fun fetch(path: String): String? = withContext(dispatcher) {
        try {
            val request = HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}/$path"))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_S))
                .GET()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            response.body().takeIf { response.statusCode() == HTTP_OK }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            log.warn("Could not reach the geocoder", failure)
            null
        }
    }

    // A soft preference (no `bounded`): "Ленина 5" resolves in the home city, "Пермь, Ленина 5" in Perm.
    private fun viewbox(): String = near?.let {
        "&viewbox=${it.longitude - BOX_LON},${it.latitude + BOX_LAT},${it.longitude + BOX_LON},${it.latitude - BOX_LAT}"
    }.orEmpty()

    private fun parse(body: String): Coordinates? {
        val first = (parseJson(body) as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val latitude = first.number("lat") ?: return null
        val longitude = first.number("lon") ?: return null
        return Coordinates(latitude, longitude).takeIf { it.latitude in LAT_RANGE && it.longitude in LON_RANGE }
    }

    /** "Екатеринбург, улица Ленина, 5": the place, then the street and house when Nominatim knows them. */
    private fun parseAddress(body: String): String? {
        val root = parseJson(body) as? JsonObject ?: return null
        val parts = root["address"] as? JsonObject
        val street = listOfNotNull(parts?.text("road"), parts?.text("house_number"))
        val city = parts?.let { it.text("city") ?: it.text("town") ?: it.text("village") }
        val composed = listOfNotNull(city, street.joinToString(", ").ifEmpty { null }).joinToString(", ")
        return composed.ifEmpty { root["display_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } }
    }

    private fun parseJson(body: String) = runCatching { Json.parseToJsonElement(body) }.getOrNull()

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.let { it.doubleOrNull }

    private companion object {
        fun defaultClient(): HttpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_S)).build()

        const val USER_AGENT = "ProrabPrime/1.0 (self-hosted renovation app)"
        const val CONNECT_TIMEOUT_S = 3L
        const val REQUEST_TIMEOUT_S = 5L
        const val HTTP_OK = 200
        const val BOX_LAT = 0.3
        const val BOX_LON = 0.5
        val LAT_RANGE = -90.0..90.0
        val LON_RANGE = -180.0..180.0
        val log = KtorSimpleLogger(NominatimGeocoder::class.qualifiedName!!)
    }
}
