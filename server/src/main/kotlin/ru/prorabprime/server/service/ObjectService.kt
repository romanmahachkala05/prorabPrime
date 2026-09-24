package ru.prorabprime.server.service

import io.ktor.util.logging.KtorSimpleLogger
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.ObjectDetails
import ru.prorabprime.server.model.ObjectListItem
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.storage.FileStorage

class ObjectService(
    private val objects: ObjectRepository,
    private val photos: PhotoRepository,
    private val storage: FileStorage,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun list(query: ObjectListQuery): List<ObjectListItem> = objects.list(query)

    suspend fun get(id: UUID): Result<ObjectDetails> {
        val record = objects.find(id) ?: return notFound(id)
        return Result.success(ObjectDetails(record, photos.listByObject(id)))
    }

    suspend fun create(request: ObjectRequestDto): Result<ObjectRecord> {
        val fields = validateObject(request).getOrElse { return Result.failure(it) }
        val now = clock.now()
        val record = ObjectRecord(id = newId(), fields = fields, coverPhotoId = null, createdAt = now, updatedAt = now)
        objects.insert(record)
        return Result.success(record)
    }

    suspend fun update(id: UUID, request: ObjectRequestDto): Result<ObjectDetails> = validateObject(request).fold(
        onSuccess = { fields -> if (objects.update(id, fields, clock.now())) get(id) else notFound(id) },
        onFailure = { Result.failure(it) },
    )

    /** The row (and, by cascade, its photo rows) first, then the files: an orphan file is harmless. */
    suspend fun delete(id: UUID): Result<Unit> {
        if (!objects.delete(id)) return notFound(id)
        runCatching { storage.deleteAll(id) }
            .onFailure { if (it is CancellationException) throw it else log.warn("Could not delete files of $id", it) }
        return Result.success(Unit)
    }

    private fun <T> notFound(id: UUID): Result<T> = ServiceError.NotFound("No object $id").asFailure()

    private companion object {
        val log = KtorSimpleLogger(ObjectService::class.qualifiedName!!)
    }
}
