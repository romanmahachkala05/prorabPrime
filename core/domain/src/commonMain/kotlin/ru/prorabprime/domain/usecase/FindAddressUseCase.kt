package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.domain.repository.PlacesRepository

/** The address under a tapped point of the map, for the form that picks an object's place there. */
class FindAddressUseCase(
    private val repository: PlacesRepository,
) {
    suspend operator fun invoke(point: GeoPoint): Result<String?> = repository.addressAt(point)
}
