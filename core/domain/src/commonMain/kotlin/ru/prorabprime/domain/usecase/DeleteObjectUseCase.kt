package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.repository.ObjectsRepository

class DeleteObjectUseCase(
    private val repository: ObjectsRepository,
) {
    suspend operator fun invoke(id: ObjectId): Result<Unit> = repository.delete(id)
}
