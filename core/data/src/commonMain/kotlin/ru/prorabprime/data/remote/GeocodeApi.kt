package ru.prorabprime.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import ru.prorabprime.contract.AddressDto
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ApiQuery
import ru.prorabprime.data.network.SERVER_BASE
import ru.prorabprime.data.network.apiCall
import ru.prorabprime.domain.model.GeoPoint

/** The address lookup of the map picker; every call is classified by [apiCall]. */
internal class GeocodeApi(
    private val client: HttpClient,
) {
    suspend fun addressAt(point: GeoPoint): Result<String?> = apiCall {
        client.get(SERVER_BASE + ApiPaths.GEOCODE_REVERSE) {
            parameter(ApiQuery.LAT, point.latitude)
            parameter(ApiQuery.LON, point.longitude)
        }.body<AddressDto>().address
    }
}
