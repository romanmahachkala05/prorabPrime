package ru.prorabprime.testing

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.PhotosRepository

class FakePhotosRepository : PhotosRepository {

    /** When set, every call fails with it instead of writing. */
    var error: AppError? = null

    val uploaded = mutableListOf<Pair<ObjectId, CompressedImage>>()
    val deleted = mutableListOf<PhotoId>()
    val covers = mutableListOf<Pair<ObjectId, PhotoId>>()

    override suspend fun upload(objectId: ObjectId, image: CompressedImage): Result<Photo> {
        error?.let { return it.asFailure() }
        uploaded += objectId to image
        return Result.success(aPhoto(id = "uploaded-${uploaded.size}", objectId = objectId.value))
    }

    override suspend fun delete(id: PhotoId): Result<Unit> {
        error?.let { return it.asFailure() }
        deleted += id
        return Result.success(Unit)
    }

    override suspend fun setCover(objectId: ObjectId, photoId: PhotoId): Result<Unit> {
        error?.let { return it.asFailure() }
        covers += objectId to photoId
        return Result.success(Unit)
    }
}
