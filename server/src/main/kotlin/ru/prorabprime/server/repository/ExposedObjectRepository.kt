package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListItem
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord

class ExposedObjectRepository(
    private val db: DbExecutor,
) : ObjectRepository {

    override suspend fun list(query: ObjectListQuery): List<ObjectListItem> = db.query {
        val search = query.search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val sortColumn = when (query.sort) {
            SortFieldDto.ADDRESS -> ObjectsTable.address
            SortFieldDto.CREATED -> ObjectsTable.createdAt
            SortFieldDto.UPDATED -> ObjectsTable.updatedAt
        }
        val order = if (query.order == SortOrderDto.ASC) SortOrder.ASC else SortOrder.DESC

        val records = ObjectsTable.selectAll()
            .apply { if (search != null) where { ObjectsTable.searchText like containing(search) } }
            // The id breaks ties, so equal addresses or timestamps keep a stable order.
            .orderBy(sortColumn to order, ObjectsTable.id to SortOrder.ASC)
            .map { it.toObjectRecord() }
        if (records.isEmpty()) return@query emptyList()

        val ids = records.map { it.id }
        val photoCount = PhotosTable.objectId.count()
        val counts = PhotosTable.select(PhotosTable.objectId, photoCount)
            .where { PhotosTable.objectId inList ids }
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

        records.map { record ->
            ObjectListItem(
                record = record,
                coverThumbFileName = record.coverPhotoId?.let(coverThumbs::get),
                photoCount = counts[record.id] ?: 0,
            )
        }
    }

    override suspend fun find(id: UUID): ObjectRecord? = db.query {
        ObjectsTable.selectAll().where { ObjectsTable.id eq id }.singleOrNull()?.toObjectRecord()
    }

    override suspend fun insert(record: ObjectRecord) {
        db.query {
            ObjectsTable.insert {
                it[ObjectsTable.id] = record.id
                it.setFields(record.fields)
                it[ObjectsTable.coverPhotoId] = record.coverPhotoId
                it[ObjectsTable.createdAt] = record.createdAt.toJavaInstant()
                it[ObjectsTable.updatedAt] = record.updatedAt.toJavaInstant()
            }
        }
    }

    override suspend fun update(
        id: UUID,
        fields: ObjectFields,
        updatedAt: Instant,
    ): Boolean = db.query {
        ObjectsTable.update({ ObjectsTable.id eq id }) {
            it.setFields(fields)
            it[ObjectsTable.updatedAt] = updatedAt.toJavaInstant()
        } > 0
    }

    override suspend fun delete(id: UUID): Boolean = db.query {
        ObjectsTable.deleteWhere { ObjectsTable.id eq id } > 0
    }

    private fun UpdateBuilder<*>.setFields(fields: ObjectFields) {
        this[ObjectsTable.title] = fields.title
        this[ObjectsTable.address] = fields.address
        this[ObjectsTable.status] = fields.status.name
        this[ObjectsTable.clientName] = fields.clientName
        this[ObjectsTable.clientPhone] = fields.clientPhone
        this[ObjectsTable.notes] = fields.notes
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
    fields = ObjectFields(
        title = this[ObjectsTable.title],
        address = this[ObjectsTable.address],
        status = ObjectStatusDto.valueOf(this[ObjectsTable.status]),
        clientName = this[ObjectsTable.clientName],
        clientPhone = this[ObjectsTable.clientPhone],
        notes = this[ObjectsTable.notes],
    ),
    coverPhotoId = this[ObjectsTable.coverPhotoId],
    createdAt = this[ObjectsTable.createdAt].toKotlinInstant(),
    updatedAt = this[ObjectsTable.updatedAt].toKotlinInstant(),
)
