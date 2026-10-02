package ru.prorabprime.server.service

import io.ktor.util.logging.KtorSimpleLogger
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import ru.prorabprime.contract.TrashLimits
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.TrashedObject
import ru.prorabprime.server.model.TrashedPhoto
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.storage.FileStorage

/** What is in the trash, the most recently deleted first. */
data class Trash(
    val objects: List<TrashedObject>,
    val photos: List<TrashedPhoto>,
)

/**
 * The trash: an object or a photo that was deleted waits here for [TrashLimits.RETENTION_DAYS] days, and
 * can be restored. Only here is anything removed for good, and the same order as everywhere holds
 * (ARCHITECTURE.md §11): the row first, then the files, and a file that will not go is only logged.
 */
class TrashService(
    private val objects: ObjectRepository,
    private val photos: PhotoRepository,
    private val storage: FileStorage,
    private val transactor: Transactor,
    private val clock: Clock,
) {
    suspend fun list(): Trash = Trash(objects.listTrashed(), photos.listTrashed())

    /** Moves `updated_at`, so phones that copy objects down learn the object is back. */
    suspend fun restoreObject(id: UUID): Result<Unit> {
        val restored = transactor.inTransaction {
            (objects.restore(id)).also { if (it) objects.touch(id, clock.now()) }
        }
        return if (restored) Result.success(Unit) else ServiceError.NotFound("No object $id in the trash").asFailure()
    }

    /** A photo whose object is itself in the trash comes back with that object, not alone. */
    suspend fun restorePhoto(id: UUID): Result<Unit> {
        val photo = photos.findTrashed(id) ?: return photoNotFound(id)
        if (objects.find(photo.objectId) == null) return photoNotFound(id)
        transactor.inTransaction {
            photos.restore(id)
            objects.touch(photo.objectId, clock.now())
        }
        return Result.success(Unit)
    }

    suspend fun purgeObject(id: UUID): Result<Unit> {
        objects.findTrashed(id) ?: return ServiceError.NotFound("No object $id in the trash").asFailure()
        objects.delete(id)
        quietly("files of $id") { storage.deleteAll(id) }
        return Result.success(Unit)
    }

    suspend fun purgePhoto(id: UUID): Result<Unit> {
        val photo = photos.findTrashed(id) ?: return photoNotFound(id)
        photos.delete(id)
        quietly("files of photo $id") {
            storage.delete(photo.objectId, photo.fileName)
            storage.delete(photo.objectId, photo.thumbFileName)
        }
        return Result.success(Unit)
    }

    /** Removes everything in the trash for good; returns how many objects and photos went. */
    suspend fun empty(): Int = purgeWhere { true }

    /** Removes what has waited longer than the retention period; returns how many objects and photos went. */
    suspend fun purgeExpired(): Int {
        val cutoff = clock.now() - TrashLimits.RETENTION_DAYS.days
        return purgeWhere { it < cutoff }
    }

    private suspend fun purgeWhere(expired: (Instant) -> Boolean): Int {
        var count = 0
        // Objects first: their trashed photos go with them, and are then no longer there to purge.
        for (trashed in objects.listTrashed().filter { expired(it.deletedAt) }) {
            if (purgeObject(trashed.record.id).isSuccess) count++
        }
        for (trashed in photos.listTrashed().filter { expired(it.deletedAt) }) {
            if (purgePhoto(trashed.photo.id).isSuccess) count++
        }
        return count
    }

    /** A file left behind is harmless; failing the request over it would not be. */
    private suspend fun quietly(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            log.warn("Could not delete $what; it is now an orphan", failure)
        }
    }

    private fun photoNotFound(id: UUID): Result<Unit> = ServiceError.NotFound("No photo $id in the trash").asFailure()

    private companion object {
        val log = KtorSimpleLogger(TrashService::class.qualifiedName!!)
    }
}
