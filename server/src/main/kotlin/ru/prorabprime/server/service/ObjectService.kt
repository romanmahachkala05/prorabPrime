package ru.prorabprime.server.service

import java.util.UUID
import kotlin.time.Clock
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.Coordinates
import ru.prorabprime.server.model.ObjectDetails
import ru.prorabprime.server.model.ObjectListItem
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository

class ObjectService(
    private val objects: ObjectRepository,
    private val photos: PhotoRepository,
    private val contacts: ContactRepository,
    private val clock: Clock,
    private val geocoder: Geocoder = NoGeocoder,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun list(owner: OwnerId, query: ObjectListQuery): List<ObjectListItem> = objects.list(owner, query)

    suspend fun get(owner: OwnerId, id: UUID): Result<ObjectDetails> {
        val record = objects.find(owner, id) ?: return notFound(id)
        return Result.success(ObjectDetails(record, contacts.listByObject(id), photos.listByObject(id)))
    }

    suspend fun create(owner: OwnerId, request: ObjectRequestDto): Result<ObjectRecord> {
        val fields = validateObject(request).getOrElse { return Result.failure(it) }
        val clientId = parseClientId(request.id).getOrElse { return Result.failure(it) }
        alreadyCreated(clientId?.let { objects.findAny(owner, it) }) { true }?.let { return it }
        val now = clock.now()
        val record =
            ObjectRecord(
                id = clientId ?: newId(),
                ownerId = owner,
                fields = fields,
                coverPhotoId = null,
                createdAt = now,
                updatedAt = now,
            )
        objects.insert(record)
        placePin(owner, record.id, request.pinned(), fields.address)
        return Result.success(record)
    }

    suspend fun update(
        owner: OwnerId,
        id: UUID,
        request: ObjectRequestDto,
    ): Result<ObjectDetails> = validateObject(request).fold(
        onSuccess = { fields ->
            val before = objects.find(owner, id)
            if (objects.update(owner, id, fields, clock.now())) {
                // A new address is somewhere else: the old pin must not stay behind.
                val moved = before != null && before.fields.address != fields.address
                val pin = request.pinned()
                if (pin != null || moved) placePin(owner, id, pin, fields.address)
                get(owner, id)
            } else {
                notFound(id)
            }
        },
        onFailure = { Result.failure(it) },
    )

    /** Looks the address up again, as the map's "find it" asks; an address nobody knows leaves no pin. */
    suspend fun geocode(owner: OwnerId, id: UUID): Result<ObjectDetails> {
        val record = objects.find(owner, id) ?: return notFound(id)
        locate(owner, id, record.fields.address)
        // The pin is a change to the object: phones that copy it down learn by its `updatedAt`.
        objects.touch(owner, id, clock.now())
        return get(owner, id)
    }

    /** The address at a point of the map, for the form that picks a place there; null when unknown. */
    suspend fun addressAt(point: Coordinates): String? = geocoder.addressAt(point)

    /** A point picked on the map is used as it is; otherwise the address is looked up. */
    private suspend fun placePin(
        owner: OwnerId,
        id: UUID,
        pin: Coordinates?,
        address: String,
    ) {
        if (pin != null) objects.setCoordinates(owner, id, pin) else locate(owner, id, address)
    }

    /** Best effort: a geocoder that is down or does not know the address must never fail a save. */
    private suspend fun locate(
        owner: OwnerId,
        id: UUID,
        address: String,
    ) {
        objects.setCoordinates(owner, id, geocoder.locate(address))
    }

    /** Only moves the object to the trash; [TrashService] is what removes anything for good. */
    suspend fun delete(owner: OwnerId, id: UUID): Result<Unit> =
        if (objects.trash(owner, id, clock.now())) Result.success(Unit) else notFound(id)

    private fun <T> notFound(id: UUID): Result<T> = ServiceError.NotFound("No object $id").asFailure()

    private fun ObjectRequestDto.pinned(): Coordinates? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return Coordinates(lat, lon).takeIf { lat in LAT_RANGE && lon in LON_RANGE }
    }

    private companion object {
        val LAT_RANGE = -90.0..90.0
        val LON_RANGE = -180.0..180.0
    }
}
