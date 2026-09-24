package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
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
