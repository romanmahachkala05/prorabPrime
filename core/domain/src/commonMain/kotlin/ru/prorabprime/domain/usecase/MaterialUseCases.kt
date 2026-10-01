package ru.prorabprime.domain.usecase

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.Material
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.MaterialsRepository

class ObserveMaterialsUseCase(
    private val repository: MaterialsRepository,
) {
    operator fun invoke(objectId: ObjectId): Flow<Result<ImmutableList<Material>>> =
        repository.observeMaterials(objectId)
}

/** Normalizes and validates the draft; an invalid one never reaches the server. */
class SaveMaterialUseCase(
    private val repository: MaterialsRepository,
) {
    suspend fun create(objectId: ObjectId, draft: MaterialDraft): Result<Unit> = checked(draft) {
        repository.add(objectId, it)
    }

    suspend fun update(id: MaterialId, draft: MaterialDraft): Result<Unit> = checked(draft) {
        repository.update(id, it)
    }

    private suspend fun checked(draft: MaterialDraft, write: suspend (MaterialDraft) -> Result<Unit>): Result<Unit> {
        val normalized = draft.normalized()
        val problems = normalized.validate()
        if (problems.isNotEmpty()) return AppError.Validation(problems).asFailure()
        return write(normalized)
    }
}

class DeleteMaterialUseCase(
    private val repository: MaterialsRepository,
) {
    suspend operator fun invoke(id: MaterialId): Result<Unit> = repository.delete(id)
}

class AddDefaultMaterialsUseCase(
    private val repository: MaterialsRepository,
) {
    suspend operator fun invoke(objectId: ObjectId): Result<Unit> = repository.addDefaults(objectId)
}
