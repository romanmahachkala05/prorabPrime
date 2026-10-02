package ru.prorabprime.domain.usecase

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.repository.ObjectsRepository

class ObserveObjectsUseCase(
    private val repository: ObjectsRepository,
) {
    operator fun invoke(query: ObjectQuery): Flow<Result<ImmutableList<ObjectSummary>>> =
        repository.observeObjects(query)
}
