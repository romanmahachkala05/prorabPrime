package ru.prorabprime.server.service

import io.ktor.util.logging.KtorSimpleLogger
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.PhotoLimits
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.PhotoRecord
import ru.prorabprime.server.model.ReceiptData
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.storage.FileStorage
import ru.prorabprime.server.storage.ImageFormat
import ru.prorabprime.server.storage.ImageProcessor
import ru.prorabprime.server.storage.ProcessedImage
import ru.prorabprime.server.storage.ReceiptReader

/**
 * Photos and the cover rules. Every operation touches both the database and the disk, and is
 * ordered so a failure halfway leaves no row pointing at a missing file (ARCHITECTURE.md §11):
 * files are written before their row and deleted after it.
 */
class PhotoService(
    private val objects: ObjectRepository,
    private val photos: PhotoRepository,
    private val storage: FileStorage,
    private val images: ImageProcessor,
    private val receipts: ReceiptReader,
    private val transactor: Transactor,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    /**
     * What ends an upload before any work: a file too big, an unknown object, or — a retried upload,
     * the phone never heard the answer — the photo this id already made.
     */
    private suspend fun refusalOrExisting(
        objectId: UUID,
        bytes: ByteArray,
        clientId: UUID?,
    ): Result<PhotoRecord>? = when {
        bytes.size > PhotoLimits.MAX_UPLOAD_BYTES ->
            ServiceError.TooLarge("A photo may be at most ${PhotoLimits.MAX_UPLOAD_BYTES} bytes").asFailure()

        objects.find(objectId) == null -> objectNotFound(objectId)

        else -> alreadyCreated(clientId?.let { photos.find(it) }) { it.objectId == objectId }
    }

    suspend fun upload(
        objectId: UUID,
        bytes: ByteArray,
        kind: AttachmentKindDto = AttachmentKindDto.PHOTO,
        clientId: UUID? = null,
        note: String? = null,
    ): Result<PhotoRecord> {
        val cleanNote = normalizedNote(note).getOrElse { return Result.failure(it) }
        return refusalOrExisting(objectId, bytes, clientId)
            ?: images.process(bytes).fold(
                onSuccess = { image ->
                    // A receipt's code is read for the sum and date; a code that cannot be read costs nothing.
                    val receipt = if (kind == AttachmentKindDto.RECEIPT) receipts.read(bytes) else null
                    store(objectId, bytes, image, UploadMeta(kind, clientId ?: newId(), cleanNote, receipt))
                },
                onFailure = { Result.failure(it) },
            )
    }

    private suspend fun store(
        objectId: UUID,
        bytes: ByteArray,
        image: ProcessedImage,
        meta: UploadMeta,
    ): Result<PhotoRecord> {
        val id = meta.id
        val fileName = "$id.${image.format.extension}"
        val thumbFileName = "${id}_thumb.jpg"
        return withFilesCompensated(objectId, listOf(fileName, thumbFileName)) {
            storage.write(objectId, fileName, bytes)
            storage.write(objectId, thumbFileName, image.thumbnail)
            transactor.inTransaction {
                val now = clock.now()
                val photo = PhotoRecord(
                    id = id,
                    objectId = objectId,
                    fileName = fileName,
                    thumbFileName = thumbFileName,
                    contentType = image.format.contentType,
                    sizeBytes = bytes.size.toLong(),
                    width = image.width,
                    height = image.height,
                    sortOrder = photos.nextSortOrder(objectId),
                    createdAt = now,
                    kind = meta.kind,
                    note = meta.note,
                    receipt = meta.receipt,
                )
                photos.insert(photo)
                // The first photo of an object without a cover becomes the cover; a receipt never does.
                if (meta.kind == AttachmentKindDto.PHOTO && objects.find(objectId)?.coverPhotoId == null) {
                    objects.setCover(objectId, id)
                }
                objects.touch(objectId, now)
                Result.success(photo)
            }
        }
    }

    suspend fun delete(photoId: UUID): Result<Unit> {
        val photo = transactor.inTransaction {
            val photo = photos.find(photoId) ?: return@inTransaction null
            val wasCover = objects.find(photo.objectId)?.coverPhotoId == photoId
            photos.delete(photoId)
            // A deleted cover is replaced by the newest remaining photo, or by none.
            if (wasCover) {
                objects.setCover(
                    photo.objectId,
                    photos.listByObject(photo.objectId)
                        .filter { it.kind == AttachmentKindDto.PHOTO }
                        .maxByOrNull { it.createdAt }?.id,
                )
            }
            objects.touch(photo.objectId, clock.now())
            photo
        } ?: return ServiceError.NotFound("No photo $photoId").asFailure()

        deleteFilesQuietly(photo.objectId, listOf(photo.fileName, photo.thumbFileName))
        return Result.success(Unit)
    }

    /** An empty note clears it. */
    suspend fun setNote(photoId: UUID, note: String?): Result<Unit> {
        val clean = normalizedNote(note).getOrElse { return Result.failure(it) }
        val photo = photos.find(photoId) ?: return ServiceError.NotFound("No photo $photoId").asFailure()
        transactor.inTransaction {
            photos.setNote(photoId, clean)
            objects.touch(photo.objectId, clock.now())
        }
        return Result.success(Unit)
    }

    /**
     * Turns the picture clockwise and keeps it that way: new files named after [rotationId], the old
     * ones removed once the row points at the new. A repeat of the same turn finds the work done.
     */
    suspend fun rotate(
        photoId: UUID,
        quarterTurns: Int,
        rotationId: UUID,
    ): Result<PhotoRecord> {
        if (quarterTurns !in MIN_TURNS..MAX_TURNS) {
            return ServiceError.Validation("quarterTurns must be $MIN_TURNS to $MAX_TURNS").asFailure()
        }
        val photo = photos.find(photoId) ?: return ServiceError.NotFound("No photo $photoId").asFailure()
        val fileName = "$rotationId.${ImageFormat.JPEG.extension}"
        if (photo.fileName == fileName) return Result.success(photo)
        val original = storage.read(photo.objectId, photo.fileName)
            ?: return ServiceError.NotFound("The file of photo $photoId is missing").asFailure()
        val turned = images.rotate(original, quarterTurns).getOrElse { return Result.failure(it) }
        val image = images.process(turned).getOrElse { return Result.failure(it) }
        val thumbFileName = "${rotationId}_thumb.jpg"
        val updated = photo.copy(
            fileName = fileName,
            thumbFileName = thumbFileName,
            contentType = image.format.contentType,
            sizeBytes = turned.size.toLong(),
            width = image.width,
            height = image.height,
        )
        withFilesCompensated(photo.objectId, listOf(fileName, thumbFileName)) {
            storage.write(photo.objectId, fileName, turned)
            storage.write(photo.objectId, thumbFileName, image.thumbnail)
            transactor.inTransaction {
                photos.replaceFiles(updated)
                objects.touch(photo.objectId, clock.now())
            }
            Result.success(Unit)
        }
        deleteFilesQuietly(photo.objectId, listOf(photo.fileName, photo.thumbFileName))
        return Result.success(updated)
    }

    suspend fun setCover(objectId: UUID, photoId: UUID): Result<Unit> {
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        val photo = photos.find(photoId)
        if (photo == null || photo.objectId != objectId) {
            return ServiceError.Validation("Photo $photoId does not belong to object $objectId").asFailure()
        }
        if (photo.kind != AttachmentKindDto.PHOTO) {
            return ServiceError.Validation("Receipt $photoId cannot be the cover").asFailure()
        }
        transactor.inTransaction {
            objects.setCover(objectId, photoId)
            objects.touch(objectId, clock.now())
        }
        return Result.success(Unit)
    }

    /** Runs [block]; if it fails, removes [fileNames] (any of them may not exist yet) and fails the same way. */
    private suspend fun <T> withFilesCompensated(
        objectId: UUID,
        fileNames: List<String>,
        block: suspend () -> Result<T>,
    ): Result<T> = try {
        block()
    } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
        // Cancellation included: the files must go whether the request failed or was abandoned.
        deleteFilesQuietly(objectId, fileNames)
        throw failure
    }

    /** A file left behind is harmless; failing the request over it would not be. */
    private suspend fun deleteFilesQuietly(objectId: UUID, fileNames: List<String>) = withContext(NonCancellable) {
        for (fileName in fileNames) {
            try {
                storage.delete(objectId, fileName)
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                log.warn("Could not delete $objectId/$fileName; it is now an orphan", failure)
            }
        }
    }

    private fun <T> objectNotFound(id: UUID): Result<T> = ServiceError.NotFound("No object $id").asFailure()

    private companion object {
        const val MIN_TURNS = 1
        const val MAX_TURNS = 3
        val log = KtorSimpleLogger(PhotoService::class.qualifiedName!!)
    }
}

/** What an upload brings besides the bytes. */
private data class UploadMeta(
    val kind: AttachmentKindDto,
    val id: UUID,
    val note: String?,
    val receipt: ReceiptData?,
)

/** Trimmed; blank is no note; too long is refused rather than cut. */
private fun normalizedNote(raw: String?): Result<String?> {
    val note = raw?.trim()?.takeIf { it.isNotEmpty() }
    return if (note != null && note.length > PhotoLimits.NOTE) {
        ServiceError.Validation("A note may be at most ${PhotoLimits.NOTE} characters").asFailure()
    } else {
        Result.success(note)
    }
}
