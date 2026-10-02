package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.repository.PhotosRepository

class DeletePhotoUseCase(
    private val repository: PhotosRepository,
) {
    suspend operator fun invoke(id: PhotoId): Result<Unit> = repository.delete(id)
}
