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
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.storage.FileStorage

class ObjectService(
    private val objects: ObjectRepository,
    private val photos: PhotoRepository,
    private val contacts: ContactRepository,
    private val storage: FileStorage,
    private val clock: Clock,
    private val geocoder: Geocoder = NoGeocoder,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun list(query: ObjectListQuery): List<ObjectListItem> = objects.list(query)

    suspend fun get(id: UUID): Result<ObjectDetails> {
        val record = objects.find(id) ?: return notFound(id)
        return Result.success(ObjectDetails(record, contacts.listByObject(id), photos.listByObject(id)))
    }

    suspend fun create(request: ObjectRequestDto): Result<ObjectRecord> {
        val fields = validateObject(request).getOrElse { return Result.failure(it) }
        val now = clock.now()
        val record = ObjectRecord(id = newId(), fields = fields, coverPhotoId = null, createdAt = now, updatedAt = now)
        objects.insert(record)
        locate(record.id, fields.address)
        return Result.success(record)
    }

    suspend fun update(id: UUID, request: ObjectRequestDto): Result<ObjectDetails> = validateObject(request).fold(
        onSuccess = { fields ->
            val before = objects.find(id)
            if (objects.update(id, fields, clock.now())) {
                // A new address is somewhere else: the old pin must not stay behind.
                if (before != null && before.fields.address != fields.address) locate(id, fields.address)
                get(id)
            } else {
                notFound(id)
            }
        },
        onFailure = { Result.failure(it) },
    )

    /** Looks the address up again, as the map's "find it" asks; an address nobody knows leaves no pin. */
    suspend fun geocode(id: UUID): Result<ObjectDetails> {
        val record = objects.find(id) ?: return notFound(id)
        locate(id, record.fields.address)
        return get(id)
    }

    /** Best effort: a geocoder that is down or does not know the address must never fail a save. */
    private suspend fun locate(id: UUID, address: String) {
        objects.setCoordinates(id, geocoder.locate(address))
    }

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
