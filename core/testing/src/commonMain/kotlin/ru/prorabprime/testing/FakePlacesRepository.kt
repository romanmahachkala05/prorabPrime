package ru.prorabprime.testing

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.PlacesRepository

/** Answers every point with [address] unless [error] is set. */
class FakePlacesRepository(
    var address: String? = null,
    var error: AppError? = null,
) : PlacesRepository {
    val asked = mutableListOf<GeoPoint>()

    override suspend fun addressAt(point: GeoPoint): Result<String?> {
        asked += point
        return error?.asFailure() ?: Result.success(address)
    }
}
