package ru.prorabprime.data.repository

import ru.prorabprime.data.remote.GeocodeApi
import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.domain.repository.PlacesRepository

internal class PlacesRepositoryImpl(
    private val api: GeocodeApi,
) : PlacesRepository {
    override suspend fun addressAt(point: GeoPoint): Result<String?> = api.addressAt(point)
}
