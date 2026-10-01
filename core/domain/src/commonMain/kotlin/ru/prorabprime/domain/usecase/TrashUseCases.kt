package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.Trash
import ru.prorabprime.domain.repository.TrashRepository

class LoadTrashUseCase(
    private val repository: TrashRepository,
) {
    suspend operator fun invoke(): Result<Trash> = repository.load()
}

class RestoreFromTrashUseCase(
    private val repository: TrashRepository,
) {
    suspend fun obj(id: ObjectId): Result<Unit> = repository.restoreObject(id)

    suspend fun photo(id: PhotoId): Result<Unit> = repository.restorePhoto(id)
}

class PurgeFromTrashUseCase(
    private val repository: TrashRepository,
) {
    suspend fun obj(id: ObjectId): Result<Unit> = repository.purgeObject(id)

    suspend fun photo(id: PhotoId): Result<Unit> = repository.purgePhoto(id)

    suspend fun all(): Result<Unit> = repository.empty()
}
