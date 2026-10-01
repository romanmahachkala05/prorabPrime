package ru.prorabprime.domain.repository

import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId

/** Photo writes. Each one makes [ObjectsRepository]'s observed flows reload. */
interface PhotosRepository {
    suspend fun upload(
        objectId: ObjectId,
        image: CompressedImage,
        kind: AttachmentKind = AttachmentKind.PHOTO,
    ): Result<Photo>

    suspend fun delete(id: PhotoId): Result<Unit>

    /** One quarter turn clockwise, kept: the server makes new files, and the phone shows the turn at once. */
    suspend fun rotate(id: PhotoId): Result<Unit>

    suspend fun setCover(objectId: ObjectId, photoId: PhotoId): Result<Unit>
}
