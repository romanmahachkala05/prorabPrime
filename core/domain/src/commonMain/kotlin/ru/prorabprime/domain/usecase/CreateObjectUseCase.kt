package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.ObjectsRepository

/** Normalizes and validates the draft; an invalid one never reaches the server. */
class CreateObjectUseCase(
    private val repository: ObjectsRepository,
) {
    suspend operator fun invoke(draft: ObjectDraft): Result<ObjectId> {
        val normalized = draft.normalized()
        val problems = normalized.validate()
        if (problems.isNotEmpty()) return AppError.Validation(problems).asFailure()
        return repository.create(normalized)
    }
}
