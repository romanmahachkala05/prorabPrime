package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.model.PhotoRecord
import ru.prorabprime.server.model.ReceiptData
import ru.prorabprime.server.model.TrashedPhoto

@Suppress("TooManyFunctions") // The interface's, see there.
class ExposedPhotoRepository(
    private val db: DbExecutor,
) : PhotoRepository {

    override suspend fun listByObject(objectId: UUID): List<PhotoRecord> = db.query {
        PhotosTable.selectAll()
            .where { (PhotosTable.objectId eq objectId) and PhotosTable.deletedAt.isNull() }
            .orderBy(PhotosTable.sortOrder to SortOrder.ASC)
            .map { it.toPhotoRecord() }
    }

    override suspend fun find(id: UUID): PhotoRecord? = db.query {
        PhotosTable.selectAll()
            .where { (PhotosTable.id eq id) and PhotosTable.deletedAt.isNull() }
            .singleOrNull()?.toPhotoRecord()
    }

    override suspend fun findAny(id: UUID): PhotoRecord? = db.query {
        PhotosTable.selectAll().where { PhotosTable.id eq id }.singleOrNull()?.toPhotoRecord()
    }

    override suspend fun trash(id: UUID, at: Instant): Boolean = db.query {
        PhotosTable.update({ (PhotosTable.id eq id) and PhotosTable.deletedAt.isNull() }) {
            it[deletedAt] = at.toJavaInstant()
        } > 0
    }

    override suspend fun restore(id: UUID): Boolean = db.query {
        PhotosTable.update({ (PhotosTable.id eq id) and PhotosTable.deletedAt.isNotNull() }) {
            it[deletedAt] = null
        } > 0
    }

    override suspend fun findTrashed(id: UUID): PhotoRecord? = db.query {
        PhotosTable.selectAll()
            .where { (PhotosTable.id eq id) and PhotosTable.deletedAt.isNotNull() }
            .singleOrNull()?.toPhotoRecord()
    }

    override suspend fun listTrashed(): List<TrashedPhoto> = db.query {
        val rows = PhotosTable.selectAll()
            .where { PhotosTable.deletedAt.isNotNull() }
            .orderBy(PhotosTable.deletedAt to SortOrder.DESC, PhotosTable.id to SortOrder.ASC)
            .toList()
        val owners = ObjectsTable.select(ObjectsTable.id, ObjectsTable.title, ObjectsTable.address)
            .where {
                (ObjectsTable.id inList rows.map { it[PhotosTable.objectId] }.distinct()) and
                    ObjectsTable.deletedAt.isNull()
            }
            .associateBy { it[ObjectsTable.id] }
        rows.mapNotNull { row ->
            val owner = owners[row[PhotosTable.objectId]] ?: return@mapNotNull null
            TrashedPhoto(
                photo = row.toPhotoRecord(),
                objectTitle = owner[ObjectsTable.title],
                objectAddress = owner[ObjectsTable.address],
                deletedAt = checkNotNull(row[PhotosTable.deletedAt]).toKotlinInstant(),
            )
        }
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
                it[kind] = photo.kind.name
                it[note] = photo.note
                it[receiptAmountKopecks] = photo.receipt?.amountKopecks
                it[receiptAt] = photo.receipt?.purchasedAt
                it[receiptQr] = photo.receipt?.qr
                it[createdAt] = photo.createdAt.toJavaInstant()
            }
        }
    }

    override suspend fun delete(id: UUID): Boolean = db.query {
        PhotosTable.deleteWhere { PhotosTable.id eq id } > 0
    }

    override suspend fun replaceFiles(photo: PhotoRecord) {
        db.query {
            PhotosTable.update({ PhotosTable.id eq photo.id }) {
                it[fileName] = photo.fileName
                it[thumbFileName] = photo.thumbFileName
                it[contentType] = photo.contentType
                it[sizeBytes] = photo.sizeBytes
                it[width] = photo.width
                it[height] = photo.height
            }
        }
    }

    override suspend fun setNote(id: UUID, note: String?): Boolean = db.query {
        PhotosTable.update({ PhotosTable.id eq id }) { it[PhotosTable.note] = note } > 0
    }

    override suspend fun setReceipt(id: UUID, receipt: ReceiptData?): Boolean = db.query {
        PhotosTable.update({ PhotosTable.id eq id }) {
            it[receiptAmountKopecks] = receipt?.amountKopecks
            it[receiptAt] = receipt?.purchasedAt
            it[receiptQr] = receipt?.qr
        } > 0
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
    kind = AttachmentKindDto.valueOf(this[PhotosTable.kind]),
    note = this[PhotosTable.note],
    receipt = this[PhotosTable.receiptAmountKopecks]?.let {
        ReceiptData(it, this[PhotosTable.receiptAt], this[PhotosTable.receiptQr].orEmpty())
    },
    createdAt = this[PhotosTable.createdAt].toKotlinInstant(),
)
