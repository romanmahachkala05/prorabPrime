package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.model.PhotoRecord

class ExposedPhotoRepository(
    private val db: DbExecutor,
) : PhotoRepository {

    override suspend fun listByObject(objectId: UUID): List<PhotoRecord> = db.query {
        PhotosTable.selectAll()
            .where { PhotosTable.objectId eq objectId }
            .orderBy(PhotosTable.sortOrder to SortOrder.ASC)
            .map { it.toPhotoRecord() }
    }

    override suspend fun find(id: UUID): PhotoRecord? = db.query {
        PhotosTable.selectAll().where { PhotosTable.id eq id }.singleOrNull()?.toPhotoRecord()
    }

    override suspend fun insert(photo: PhotoRecord) {
        db.query {
            PhotosTable.insert {
                it[id] = photo.id
                it[objectId] = photo.objectId
                it[fileName] = photo.fileName
                it[thumbFileName] = photo.thumbFileName
                it[contentType] = photo.contentType
                it[sizeBytes] = photo.sizeBytes
                it[width] = photo.width
                it[height] = photo.height
                it[sortOrder] = photo.sortOrder
                it[createdAt] = photo.createdAt.toJavaInstant()
            }
        }
    }

    override suspend fun delete(id: UUID): Boolean = db.query {
        PhotosTable.deleteWhere { PhotosTable.id eq id } > 0
    }

    override suspend fun nextSortOrder(objectId: UUID): Int = db.query {
        val max = PhotosTable.sortOrder.max()
        val highest = PhotosTable.select(max).where { PhotosTable.objectId eq objectId }.single()[max]
        (highest ?: 0) + 1
    }
}

internal fun ResultRow.toPhotoRecord() = PhotoRecord(
    id = this[PhotosTable.id],
    objectId = this[PhotosTable.objectId],
    fileName = this[PhotosTable.fileName],
    thumbFileName = this[PhotosTable.thumbFileName],
    contentType = this[PhotosTable.contentType],
    sizeBytes = this[PhotosTable.sizeBytes],
    width = this[PhotosTable.width],
    height = this[PhotosTable.height],
    sortOrder = this[PhotosTable.sortOrder],
    createdAt = this[PhotosTable.createdAt].toKotlinInstant(),
)
