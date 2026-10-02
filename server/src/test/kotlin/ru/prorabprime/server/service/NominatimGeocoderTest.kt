package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.prorabprime.server.model.Coordinates

/** The real HTTP client against a local stand-in for Nominatim. */
class NominatimGeocoderTest {

    private val requests = mutableListOf<Pair<String, String?>>()
    private var status = 200
    private var body = """[{"lat":"55.7558","lon":"37.6173","display_name":"Москва"}]"""

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        for (path in listOf("/search", "/reverse")) {
            createContext(path) { exchange ->
                requests += exchange.requestURI.rawQuery to exchange.requestHeaders.getFirst("User-Agent")
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        start()
    }

    private val geocoder = NominatimGeocoder("http://127.0.0.1:${server.address.port}/", Dispatchers.IO)

    @After
    fun tearDown() = server.stop(0)

    @Test
    fun `an address becomes coordinates, asked for with an identifying user agent`() = runTest {
        val point = geocoder.locate("Москва, Тверская 5 & кв. 3")

        assertThat(point).isEqualTo(Coordinates(55.7558, 37.6173))
        val (query, agent) = requests.single()
        assertThat(URLDecoder.decode(query, Charsets.UTF_8)).contains("q=Москва, Тверская 5 & кв. 3")
        assertThat(query).contains("limit=1")
        assertThat(agent).contains("ProrabPrime")
    }

    @Test
    fun `a home point is only a preference and the search is not bounded to it`() = runTest {
        val local =
            NominatimGeocoder("http://127.0.0.1:${server.address.port}/", Dispatchers.IO, Coordinates(56.0, 60.0))

        local.locate("Ленина 5")

        val query = requests.single().first
        assertThat(query).contains("viewbox=59.5,56.3,60.5,55.7")
        assertThat(query).doesNotContain("bounded")
        assertThat(requests.size).isEqualTo(1)
    }

    @Test
    fun `the address at a point is the city, the street and the house`() = runTest {
        body = """{"display_name":"5, улица Ленина, Ленинский район, Екатеринбург","address":{"house_number":"5",
            |"road":"улица Ленина","city":"Екатеринбург"}}
        """.trimMargin()

        assertThat(geocoder.addressAt(Coordinates(56.84, 60.61))).isEqualTo("Екатеринбург, улица Ленина, 5")

        val query = requests.single().first
        assertThat(query).contains("lat=56.84")
        assertThat(query).contains("lon=60.61")
    }

    @Test
    fun `a point without a street falls back to the display name, and nothing known is null`() = runTest {
        body = """{"display_name":"Лес, Свердловская область","address":{"state":"Свердловская область"}}"""
        assertThat(geocoder.addressAt(Coordinates(1.0, 2.0))).isEqualTo("Лес, Свердловская область")

        body = """{"error":"Unable to geocode"}"""
        assertThat(geocoder.addressAt(Coordinates(1.0, 2.0))).isNull()

        body = "<html>"
        assertThat(geocoder.addressAt(Coordinates(1.0, 2.0))).isNull()
    }

    @Test
    fun `without a home point the search carries no viewbox`() = runTest {
        geocoder.locate("Ленина 5")

        assertThat(requests.single().first).doesNotContain("viewbox")
    }

    @Test
    fun `no match, a bad status, garbage or a point off the globe all mean not found`() = runTest {
        body = "[]"
        assertThat(geocoder.locate("нигде")).isNull()

        body = """[{"lat":"abc","lon":"37"}]"""
        assertThat(geocoder.locate("x")).isNull()

        body = """[{"lat":"95","lon":"37"}]"""
        assertThat(geocoder.locate("x")).isNull()

        body = "<html>"
        assertThat(geocoder.locate("x")).isNull()

        status = 503
        body = """[{"lat":"1","lon":"2"}]"""
        assertThat(geocoder.locate("x")).isNull()
    }

    @Test
    fun `a server that is not there means not found`() = runTest {
        val unreachable = NominatimGeocoder("http://127.0.0.1:1", Dispatchers.IO)

        assertThat(unreachable.locate("x")).isNull()
    }

    @Test
    fun `the off switch locates nothing`() = runTest {
        assertThat(NoGeocoder.locate("Москва")).isNull()
    }
}
