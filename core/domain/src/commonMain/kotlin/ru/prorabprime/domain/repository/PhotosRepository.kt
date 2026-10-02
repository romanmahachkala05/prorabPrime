package ru.prorabprime.domain.repository

import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.ReceiptInfo

/** Photo writes. Each one makes [ObjectsRepository]'s observed flows reload. */
interface PhotosRepository {
    suspend fun upload(
        objectId: ObjectId,
        image: CompressedImage,
        kind: AttachmentKind = AttachmentKind.PHOTO,
        note: String? = null,
    ): Result<Photo>

    suspend fun delete(id: PhotoId): Result<Unit>

    /** Sets the sum and day of a receipt by hand; null clears them. A photo that is not a receipt is refused. */
    suspend fun setReceipt(id: PhotoId, receipt: ReceiptInfo?): Result<Unit>

    /** An empty note clears it. */
    suspend fun setNote(id: PhotoId, note: String?): Result<Unit>

    /** One quarter turn clockwise, kept: the server makes new files, and the phone shows the turn at once. */
    suspend fun rotate(id: PhotoId): Result<Unit>

    suspend fun setCover(objectId: ObjectId, photoId: PhotoId): Result<Unit>
}
