package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.ImageCompressor
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.repository.PhotosRepository

/** Compresses a picture from the device, then uploads it. Nothing is sent if compression fails. */
class UploadPhotoUseCase(
    private val compressor: ImageCompressor,
    private val repository: PhotosRepository,
) {
    suspend operator fun invoke(objectId: ObjectId, image: LocalImageRef): Result<Photo> {
        val compressed = compressor.compress(image).getOrElse { return Result.failure(it) }
        return repository.upload(objectId, compressed)
    }
}
