package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.model.Coordinates
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListItem
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.TrashedObject

@Suppress("TooManyFunctions") // The interface's, see there.
class ExposedObjectRepository(
    private val db: DbExecutor,
) : ObjectRepository {

    override suspend fun list(owner: OwnerId, query: ObjectListQuery): List<ObjectListItem> = db.query {
        val search = query.search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val sortColumn = when (query.sort) {
            SortFieldDto.ADDRESS -> ObjectsTable.address
            SortFieldDto.CREATED -> ObjectsTable.createdAt
            SortFieldDto.UPDATED -> ObjectsTable.updatedAt
        }
        val order = if (query.order == SortOrderDto.ASC) SortOrder.ASC else SortOrder.DESC

        val live = ObjectsTable.deletedAt.isNull() and (ObjectsTable.ownerId eq owner.value)
        val records = ObjectsTable.selectAll()
            .where { if (search != null) live and (ObjectsTable.searchText like containing(search)) else live }
            // The id breaks ties, so equal addresses or timestamps keep a stable order.
            .orderBy(sortColumn to order, ObjectsTable.id to SortOrder.ASC)
            .map { it.toObjectRecord() }
        withPhotoFacts(records)
    }

    /** What a row of a list shows about the object's live photos. */
    private fun withPhotoFacts(records: List<ObjectRecord>): List<ObjectListItem> {
        if (records.isEmpty()) return emptyList()
        val ids = records.map { it.id }
        val photoCount = PhotosTable.objectId.count()
        val counts = PhotosTable.select(PhotosTable.objectId, photoCount)
            .where {
                (PhotosTable.objectId inList ids) and
                    (PhotosTable.kind eq AttachmentKindDto.PHOTO.name) and
                    PhotosTable.deletedAt.isNull()
            }
            .groupBy(PhotosTable.objectId)
            .associate { it[PhotosTable.objectId] to it[photoCount].toInt() }
        val coverIds = records.mapNotNull { it.coverPhotoId }
        val coverThumbs = if (coverIds.isEmpty()) {
            emptyMap()
        } else {
            PhotosTable.select(PhotosTable.id, PhotosTable.thumbFileName)
                .where { PhotosTable.id inList coverIds }
                .associate { it[PhotosTable.id] to it[PhotosTable.thumbFileName] }
        }
        return records.map { record ->
            ObjectListItem(
                record = record,
                coverThumbFileName = record.coverPhotoId?.let(coverThumbs::get),
                photoCount = counts[record.id] ?: 0,
            )
        }
    }

    /** The one object [id] names, if it belongs to [owner]. */
    private fun mine(owner: OwnerId, id: UUID): Op<Boolean> =
        (ObjectsTable.id eq id) and (ObjectsTable.ownerId eq owner.value)

    override suspend fun find(owner: OwnerId, id: UUID): ObjectRecord? = db.query {
        ObjectsTable.selectAll()
            .where { mine(owner, id) and ObjectsTable.deletedAt.isNull() }
            .singleOrNull()?.toObjectRecord()
    }

    override suspend fun findAny(owner: OwnerId, id: UUID): ObjectRecord? = db.query {
        ObjectsTable.selectAll().where { mine(owner, id) }.singleOrNull()?.toObjectRecord()
    }

    override suspend fun trash(
        owner: OwnerId,
        id: UUID,
        at: Instant,
    ): Boolean = db.query {
        ObjectsTable.update({ mine(owner, id) and ObjectsTable.deletedAt.isNull() }) {
            it[deletedAt] = at.toJavaInstant()
        } > 0
    }

    override suspend fun restore(owner: OwnerId, id: UUID): Boolean = db.query {
        ObjectsTable.update({ mine(owner, id) and ObjectsTable.deletedAt.isNotNull() }) {
            it[deletedAt] = null
        } > 0
    }

    override suspend fun findTrashed(owner: OwnerId, id: UUID): ObjectRecord? = db.query {
        ObjectsTable.selectAll()
            .where { mine(owner, id) and ObjectsTable.deletedAt.isNotNull() }
            .singleOrNull()?.toObjectRecord()
    }

    override suspend fun listTrashed(owner: OwnerId): List<TrashedObject> = db.query {
        val rows = ObjectsTable.selectAll()
            .where { ObjectsTable.deletedAt.isNotNull() and (ObjectsTable.ownerId eq owner.value) }
            .orderBy(ObjectsTable.deletedAt to SortOrder.DESC, ObjectsTable.id to SortOrder.ASC)
            .toList()
        val deletedAt = rows.associate {
            it[ObjectsTable.id] to
                checkNotNull(it[ObjectsTable.deletedAt]).toKotlinInstant()
        }
        withPhotoFacts(rows.map { it.toObjectRecord() }).map {
            TrashedObject(it.record, it.coverThumbFileName, it.photoCount, deletedAt.getValue(it.record.id))
        }
    }

    override suspend fun insert(record: ObjectRecord) {
        db.query {
            ObjectsTable.insert {
                it[ObjectsTable.id] = record.id
                it[ObjectsTable.ownerId] = record.ownerId.value
                it.setFields(record.fields)
                it[ObjectsTable.coverPhotoId] = record.coverPhotoId
                it[ObjectsTable.createdAt] = record.createdAt.toJavaInstant()
                it[ObjectsTable.updatedAt] = record.updatedAt.toJavaInstant()
            }
        }
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: ObjectFields,
        updatedAt: Instant,
    ): Boolean = db.query {
        ObjectsTable.update({ mine(owner, id) }) {
            it.setFields(fields)
            it[ObjectsTable.updatedAt] = updatedAt.toJavaInstant()
        } > 0
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean = db.query {
        ObjectsTable.deleteWhere { mine(owner, id) } > 0
    }

    override suspend fun deleteTrashedBefore(cutoff: Instant): List<UUID> = db.query {
        val expired = ObjectsTable.select(ObjectsTable.id)
            .where { ObjectsTable.deletedAt less cutoff.toJavaInstant() }
            .map { it[ObjectsTable.id] }
        if (expired.isNotEmpty()) ObjectsTable.deleteWhere { ObjectsTable.id inList expired }
        expired
    }

    override suspend fun setCover(
        owner: OwnerId,
        id: UUID,
        photoId: UUID?,
    ) {
        db.query { ObjectsTable.update({ mine(owner, id) }) { it[coverPhotoId] = photoId } }
    }

    override suspend fun setCoordinates(
        owner: OwnerId,
        id: UUID,
        coordinates: Coordinates?,
    ) {
        db.query {
            ObjectsTable.update({ mine(owner, id) }) {
                it[latitude] = coordinates?.latitude
                it[longitude] = coordinates?.longitude
            }
        }
    }

    override suspend fun touch(
        owner: OwnerId,
        id: UUID,
        at: Instant,
    ) {
        db.query { ObjectsTable.update({ mine(owner, id) }) { it[updatedAt] = at.toJavaInstant() } }
    }

    private fun UpdateBuilder<*>.setFields(fields: ObjectFields) {
        this[ObjectsTable.title] = fields.title
        this[ObjectsTable.address] = fields.address
        this[ObjectsTable.status] = fields.status.name
        this[ObjectsTable.clientName] = fields.clientName
        this[ObjectsTable.clientPhone] = fields.clientPhone
        this[ObjectsTable.notes] = fields.notes
        this[ObjectsTable.chatLink] = fields.chatLink
        this[ObjectsTable.searchText] = searchTextOf(fields)
    }
}

internal fun searchTextOf(fields: ObjectFields): String = listOfNotNull(fields.address, fields.title)
    .joinToString(separator = "\n")
    .lowercase()

/** A LIKE pattern matching [text] anywhere, with its own `%`, `_` and `\` taken literally. */
private fun containing(text: String): LikePattern {
    val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return LikePattern("%$escaped%", escapeChar = '\\')
}

private fun ResultRow.toObjectRecord() = ObjectRecord(
    id = this[ObjectsTable.id],
    ownerId = OwnerId(this[ObjectsTable.ownerId]),
    fields = ObjectFields(
        title = this[ObjectsTable.title],
        address = this[ObjectsTable.address],
        status = ObjectStatusDto.valueOf(this[ObjectsTable.status]),
        clientName = this[ObjectsTable.clientName],
        clientPhone = this[ObjectsTable.clientPhone],
        notes = this[ObjectsTable.notes],
        chatLink = this[ObjectsTable.chatLink],
    ),
    coverPhotoId = this[ObjectsTable.coverPhotoId],
    createdAt = this[ObjectsTable.createdAt].toKotlinInstant(),
    updatedAt = this[ObjectsTable.updatedAt].toKotlinInstant(),
    coordinates = this[ObjectsTable.latitude]?.let { lat ->
        this[ObjectsTable.longitude]?.let { lon -> Coordinates(lat, lon) }
    },
)
