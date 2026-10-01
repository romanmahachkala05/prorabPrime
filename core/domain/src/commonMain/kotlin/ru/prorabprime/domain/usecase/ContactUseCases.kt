package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactId
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.ContactsRepository

/** Normalizes and validates the draft; an invalid one never reaches the server. */
class SaveContactUseCase(
    private val repository: ContactsRepository,
) {
    suspend fun create(objectId: ObjectId, draft: ContactDraft): Result<ContactId> {
        val normalized = draft.normalized()
        val problems = normalized.validate()
        if (problems.isNotEmpty()) return AppError.Validation(problems).asFailure()
        return repository.create(objectId, normalized)
    }

    suspend fun update(id: ContactId, draft: ContactDraft): Result<Unit> {
        val normalized = draft.normalized()
        val problems = normalized.validate()
        if (problems.isNotEmpty()) return AppError.Validation(problems).asFailure()
        return repository.update(id, normalized)
    }
}

class DeleteContactUseCase(
    private val repository: ContactsRepository,
) {
    suspend operator fun invoke(id: ContactId): Result<Unit> = repository.delete(id)
}
