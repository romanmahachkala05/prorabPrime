package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.repository.PhotosRepository

class SetCoverPhotoUseCase(
    private val repository: PhotosRepository,
) {
    suspend operator fun invoke(objectId: ObjectId, photoId: PhotoId): Result<Unit> =
        repository.setCover(objectId, photoId)
}
