package ru.prorabprime.server.service

import io.ktor.util.logging.KtorSimpleLogger
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ru.prorabprime.contract.PhotoLimits
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.PhotoRecord
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.storage.FileStorage
import ru.prorabprime.server.storage.ImageProcessor

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
    private val transactor: Transactor,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun upload(objectId: UUID, bytes: ByteArray): Result<PhotoRecord> {
        if (bytes.size > PhotoLimits.MAX_UPLOAD_BYTES) {
            return ServiceError.TooLarge("A photo may be at most ${PhotoLimits.MAX_UPLOAD_BYTES} bytes").asFailure()
        }
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        val image = images.process(bytes).getOrElse { return Result.failure(it) }

        val id = newId()
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
                )
                photos.insert(photo)
                // The first photo of an object without a cover becomes the cover.
                if (objects.find(objectId)?.coverPhotoId == null) objects.setCover(objectId, id)
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
                    photos.listByObject(photo.objectId).maxByOrNull {
                        it.createdAt
                    }?.id,
                )
            }
            objects.touch(photo.objectId, clock.now())
            photo
        } ?: return ServiceError.NotFound("No photo $photoId").asFailure()

        deleteFilesQuietly(photo.objectId, listOf(photo.fileName, photo.thumbFileName))
        return Result.success(Unit)
    }

    suspend fun setCover(objectId: UUID, photoId: UUID): Result<Unit> {
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        val photo = photos.find(photoId)
        if (photo == null || photo.objectId != objectId) {
            return ServiceError.Validation("Photo $photoId does not belong to object $objectId").asFailure()
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
        val log = KtorSimpleLogger(PhotoService::class.qualifiedName!!)
    }
}
