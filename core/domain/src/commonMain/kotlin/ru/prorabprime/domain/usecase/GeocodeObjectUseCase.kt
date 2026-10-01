package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.repository.ObjectsRepository

/** Looks an object's address up on the map again; the list reloads with the result. */
class GeocodeObjectUseCase(
    private val repository: ObjectsRepository,
) {
    suspend operator fun invoke(id: ObjectId): Result<Unit> = repository.geocode(id)
}
