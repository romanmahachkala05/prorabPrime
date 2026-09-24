package ru.prorabprime.server.fakes

import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListItem
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.PhotoRecord
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.repository.searchTextOf

val FIXED_NOW: Instant = Instant.parse("2026-09-25T12:00:00Z")

class FixedClock(
    var now: Instant = FIXED_NOW,
) : Clock {
    override fun now(): Instant = now
}

/** In-memory [ObjectRepository] with the same search and sort semantics as the SQL one. */
class FakeObjectRepository(
    private val photos: FakePhotoRepository = FakePhotoRepository(),
) : ObjectRepository {

    val records = linkedMapOf<UUID, ObjectRecord>()

    override suspend fun list(query: ObjectListQuery): List<ObjectListItem> {
        val search = query.search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val matching = records.values.filter { search == null || search in searchTextOf(it.fields) }
        val sorted = when (query.sort) {
            SortFieldDto.ADDRESS -> matching.sortedBy { it.fields.address }
            SortFieldDto.CREATED -> matching.sortedBy { it.createdAt }
            SortFieldDto.UPDATED -> matching.sortedBy { it.updatedAt }
        }.let { if (query.order == SortOrderDto.DESC) it.reversed() else it }
        return sorted.map { record ->
            val objectPhotos = photos.records.values.filter { it.objectId == record.id }
            ObjectListItem(
                record = record,
                coverThumbFileName = objectPhotos.find { it.id == record.coverPhotoId }?.thumbFileName,
                photoCount = objectPhotos.size,
            )
        }
    }

    override suspend fun find(id: UUID): ObjectRecord? = records[id]

    override suspend fun insert(record: ObjectRecord) {
        records[record.id] = record
    }

    override suspend fun update(
        id: UUID,
        fields: ObjectFields,
        updatedAt: Instant,
    ): Boolean {
        val record = records[id] ?: return false
        records[id] = record.copy(fields = fields, updatedAt = updatedAt)
        return true
    }

    override suspend fun delete(id: UUID): Boolean {
        photos.records.values.removeAll { it.objectId == id }
        return records.remove(id) != null
    }
}

class FakePhotoRepository : PhotoRepository {

    val records = linkedMapOf<UUID, PhotoRecord>()

    override suspend fun listByObject(objectId: UUID): List<PhotoRecord> =
        records.values.filter { it.objectId == objectId }.sortedBy { it.sortOrder }
}

fun aPhotoRecord(
    objectId: UUID,
    id: UUID = UUID.randomUUID(),
    sortOrder: Int = 1,
    createdAt: Instant = FIXED_NOW,
) = PhotoRecord(
    id = id,
    objectId = objectId,
    fileName = "$id.jpg",
    thumbFileName = "${id}_thumb.jpg",
    contentType = "image/jpeg",
    sizeBytes = 1024,
    width = 2048,
    height = 1536,
    sortOrder = sortOrder,
    createdAt = createdAt,
)
