package ru.prorabprime.server.routes

import io.ktor.http.Parameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.AddressDto
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ApiQuery
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.model.Coordinates
import ru.prorabprime.server.service.ObjectService

fun Route.geocodeRoutes() {
    val service by inject<ObjectService>()

    get(ApiPaths.GEOCODE_REVERSE) {
        val params = call.request.queryParameters
        val point = Coordinates(params.degrees(ApiQuery.LAT, LAT_LIMIT), params.degrees(ApiQuery.LON, LON_LIMIT))
        call.respond(AddressDto(service.addressAt(point)))
    }
}

private const val LAT_LIMIT = 90.0
private const val LON_LIMIT = 180.0

private fun Parameters.degrees(name: String, limit: Double): Double =
    this[name]?.toDoubleOrNull()?.takeIf { it in -limit..limit }
        ?: throw ServiceException(ServiceError.Validation("$name must be a number between -$limit and $limit"))
