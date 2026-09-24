package ru.prorabprime.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.repository.ObjectsRepository

class ObserveObjectUseCase(
    private val repository: ObjectsRepository,
) {
    operator fun invoke(id: ObjectId): Flow<Result<ObjectDetails>> = repository.observeObject(id)
}
