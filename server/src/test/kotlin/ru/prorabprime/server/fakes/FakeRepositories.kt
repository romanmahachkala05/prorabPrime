package ru.prorabprime.server.fakes

import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.model.ContactFields
import ru.prorabprime.server.model.ContactRecord
import ru.prorabprime.server.model.Coordinates
import ru.prorabprime.server.model.ExtraWorkFields
import ru.prorabprime.server.model.ExtraWorkRecord
import ru.prorabprime.server.model.FinanceTerms
import ru.prorabprime.server.model.MaterialFields
import ru.prorabprime.server.model.MaterialRecord
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListItem
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.PaymentFields
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.PaymentRevisionRecord
import ru.prorabprime.server.model.PhotoRecord
import ru.prorabprime.server.model.ReceiptData
import ru.prorabprime.server.model.TaskFields
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord
import ru.prorabprime.server.model.TrashedObject
import ru.prorabprime.server.model.TrashedPhoto
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ExtraWorkRepository
import ru.prorabprime.server.repository.FinanceTermsRepository
import ru.prorabprime.server.repository.MaterialRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PaymentRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.repository.TaskRepository
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

    /** When each object in the trash was put there. */
    val trashedAt = linkedMapOf<UUID, Instant>()

    init {
        photos.objects = this
    }

    override suspend fun list(owner: OwnerId, query: ObjectListQuery): List<ObjectListItem> {
        val search = query.search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val matching = records.values.filter {
            it.ownerId == owner && it.id !in trashedAt &&
                (search == null || search in searchTextOf(it.fields))
        }
        val sorted = when (query.sort) {
            SortFieldDto.ADDRESS -> matching.sortedBy { it.fields.address }
            SortFieldDto.CREATED -> matching.sortedBy { it.createdAt }
            SortFieldDto.UPDATED -> matching.sortedBy { it.updatedAt }
        }.let { if (query.order == SortOrderDto.DESC) it.reversed() else it }
        return sorted.map { record ->
            val objectPhotos = photos.records.values.filter { it.objectId == record.id && it.id !in photos.trashedAt }
            ObjectListItem(
                record = record,
                coverThumbFileName = objectPhotos.find { it.id == record.coverPhotoId }?.thumbFileName,
                photoCount = objectPhotos.count { it.kind == AttachmentKindDto.PHOTO },
            )
        }
    }

    private fun mine(owner: OwnerId, id: UUID): ObjectRecord? = records[id]?.takeIf { it.ownerId == owner }

    override suspend fun find(owner: OwnerId, id: UUID): ObjectRecord? = mine(owner, id)?.takeIf { id !in trashedAt }

    override suspend fun findAny(owner: OwnerId, id: UUID): ObjectRecord? = mine(owner, id)

    override suspend fun trash(
        owner: OwnerId,
        id: UUID,
        at: Instant,
    ): Boolean {
        if (mine(owner, id) == null || id in trashedAt) return false
        trashedAt[id] = at
        return true
    }

    override suspend fun restore(owner: OwnerId, id: UUID): Boolean =
        mine(owner, id) != null && trashedAt.remove(id) != null

    override suspend fun findTrashed(owner: OwnerId, id: UUID): ObjectRecord? =
        mine(owner, id)?.takeIf { id in trashedAt }

    override suspend fun listTrashed(owner: OwnerId): List<TrashedObject> = trashedAt.entries.filter {
        records.getValue(it.key).ownerId == owner
    }.sortedByDescending {
        it.value
    }.map { (id, at) ->
        val objectPhotos = photos.records.values.filter { it.objectId == id && it.id !in photos.trashedAt }
        val record = records.getValue(id)
        TrashedObject(
            record = record,
            coverThumbFileName = objectPhotos.find { it.id == record.coverPhotoId }?.thumbFileName,
            photoCount = objectPhotos.count { it.kind == AttachmentKindDto.PHOTO },
            deletedAt = at,
        )
    }

    override suspend fun insert(record: ObjectRecord) {
        records[record.id] = record
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: ObjectFields,
        updatedAt: Instant,
    ): Boolean {
        val record = mine(owner, id) ?: return false
        records[id] = record.copy(fields = fields, updatedAt = updatedAt)
        return true
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean {
        if (mine(owner, id) == null) return false
        removeWithPhotos(id)
        return true
    }

    override suspend fun deleteTrashedBefore(cutoff: Instant): List<UUID> {
        val expired = trashedAt.filterValues { it < cutoff }.keys.toList()
        expired.forEach(::removeWithPhotos)
        return expired
    }

    private fun removeWithPhotos(id: UUID) {
        photos.records.values.removeAll { it.objectId == id }
        trashedAt.remove(id)
        records.remove(id)
    }

    override suspend fun setCover(
        owner: OwnerId,
        id: UUID,
        photoId: UUID?,
    ) {
        mine(owner, id)?.let { records[id] = it.copy(coverPhotoId = photoId) }
    }

    override suspend fun touch(
        owner: OwnerId,
        id: UUID,
        at: Instant,
    ) {
        mine(owner, id)?.let { records[id] = it.copy(updatedAt = at) }
    }

    override suspend fun setCoordinates(
        owner: OwnerId,
        id: UUID,
        coordinates: Coordinates?,
    ) {
        mine(owner, id)?.let { records[id] = it.copy(coordinates = coordinates) }
    }
}

class FakePhotoRepository : PhotoRepository {

    val records = linkedMapOf<UUID, PhotoRecord>()

    /** When each photo in the trash was put there. */
    val trashedAt = linkedMapOf<UUID, Instant>()

    /** Set by the object repository that shares this one, so a trashed photo can say where it came from. */
    var objects: FakeObjectRepository? = null

    /** When set, [insert] throws it, as a failed database write would. */
    var insertFailure: Exception? = null

    override suspend fun listByObject(objectId: UUID): List<PhotoRecord> =
        records.values.filter { it.objectId == objectId && it.id !in trashedAt }.sortedBy { it.sortOrder }

    /**
     * Whose a photo is follows its object. A photo with no object repository linked to this one is nobody's in
     * particular, and passes: the filtering itself is tested on PostgreSQL, where it is real.
     */
    private fun mine(owner: OwnerId, id: UUID): PhotoRecord? = records[id]?.takeIf { photo ->
        objects?.records?.get(photo.objectId)?.let { it.ownerId == owner } ?: true
    }

    override suspend fun find(owner: OwnerId, id: UUID): PhotoRecord? = mine(owner, id)?.takeIf { id !in trashedAt }

    override suspend fun findAny(owner: OwnerId, id: UUID): PhotoRecord? = mine(owner, id)

    override suspend fun trash(
        owner: OwnerId,
        id: UUID,
        at: Instant,
    ): Boolean {
        if (mine(owner, id) == null || id in trashedAt) return false
        trashedAt[id] = at
        return true
    }

    override suspend fun restore(owner: OwnerId, id: UUID): Boolean =
        mine(owner, id) != null && trashedAt.remove(id) != null

    override suspend fun findTrashed(owner: OwnerId, id: UUID): PhotoRecord? = mine(owner, id)?.takeIf {
        id in trashedAt
    }

    override suspend fun listTrashed(owner: OwnerId): List<TrashedPhoto> = trashedAt.entries
        .filter { mine(owner, it.key) != null }
        .sortedByDescending { it.value }
        .mapNotNull { (id, at) ->
            val photo = records.getValue(id)
            val parent = objects?.find(owner, photo.objectId) ?: return@mapNotNull null
            TrashedPhoto(photo, parent.fields.title, parent.fields.address, at)
        }

    override suspend fun insert(photo: PhotoRecord) {
        insertFailure?.let { throw it }
        records[photo.id] = photo
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean {
        if (mine(owner, id) == null) return false
        trashedAt.remove(id)
        return records.remove(id) != null
    }

    override suspend fun deleteTrashedBefore(cutoff: Instant): List<PhotoRecord> {
        val expired = trashedAt.filterValues { it < cutoff }.keys
            .mapNotNull { records[it] }
            .filter { objects?.records?.get(it.objectId)?.let { parent -> parent.id !in objects!!.trashedAt } ?: true }
        expired.forEach {
            trashedAt.remove(it.id)
            records.remove(it.id)
        }
        return expired
    }

    override suspend fun setNote(
        owner: OwnerId,
        id: UUID,
        note: String?,
    ): Boolean {
        val record = mine(owner, id) ?: return false
        records[id] = record.copy(note = note)
        return true
    }

    override suspend fun setReceipt(
        owner: OwnerId,
        id: UUID,
        receipt: ReceiptData?,
    ): Boolean {
        val record = mine(owner, id) ?: return false
        records[id] = record.copy(receipt = receipt)
        return true
    }

    override suspend fun replaceFiles(owner: OwnerId, photo: PhotoRecord) {
        if (mine(owner, photo.id) != null) records[photo.id] = photo
    }

    override suspend fun nextSortOrder(objectId: UUID): Int =
        (records.values.filter { it.objectId == objectId }.maxOfOrNull { it.sortOrder } ?: 0) + 1
}

class FakeContactRepository : ContactRepository {

    val records = linkedMapOf<UUID, ContactRecord>()

    override suspend fun listByObject(objectId: UUID): List<ContactRecord> =
        records.values.filter { it.objectId == objectId }.sortedBy { it.sortOrder }

    override suspend fun find(owner: OwnerId, id: UUID): ContactRecord? = records[id]

    override suspend fun insert(contact: ContactRecord) {
        records[contact.id] = contact
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: ContactFields,
    ): Boolean {
        val record = records[id] ?: return false
        records[id] = record.copy(fields = fields)
        return true
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean = records.remove(id) != null

    override suspend fun nextSortOrder(objectId: UUID): Int =
        (records.values.filter { it.objectId == objectId }.maxOfOrNull { it.sortOrder } ?: 0) + 1
}

class FakeFinanceTermsRepository : FinanceTermsRepository {

    val saved = mutableMapOf<UUID, FinanceTerms>()

    override suspend fun find(objectId: UUID): FinanceTerms = saved[objectId] ?: FinanceTerms()

    override suspend fun save(objectId: UUID, terms: FinanceTerms) {
        saved[objectId] = terms
    }
}

class FakePaymentRepository : PaymentRepository {

    val records = linkedMapOf<UUID, PaymentRecord>()
    val revisions = mutableListOf<PaymentRevisionRecord>()

    override suspend fun listByObject(objectId: UUID): List<PaymentRecord> =
        records.values.filter { it.objectId == objectId }.sortedBy { it.fields.paidOn }

    override suspend fun find(owner: OwnerId, id: UUID): PaymentRecord? = records[id]

    override suspend fun insert(payment: PaymentRecord) {
        records[payment.id] = payment
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: PaymentFields,
    ): Boolean {
        val record = records[id] ?: return false
        records[id] = record.copy(fields = fields)
        return true
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean = records.remove(id) != null

    override suspend fun addRevision(revision: PaymentRevisionRecord) {
        revisions += revision
    }

    override suspend fun revisionsOf(objectId: UUID): List<PaymentRevisionRecord> =
        revisions.filter { it.objectId == objectId }.sortedByDescending { it.at }
}

class FakeExtraWorkRepository : ExtraWorkRepository {

    val records = linkedMapOf<UUID, ExtraWorkRecord>()

    override suspend fun listByObject(objectId: UUID): List<ExtraWorkRecord> =
        records.values.filter { it.objectId == objectId }.sortedBy { it.createdAt }

    override suspend fun find(owner: OwnerId, id: UUID): ExtraWorkRecord? = records[id]

    override suspend fun insert(work: ExtraWorkRecord) {
        records[work.id] = work
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: ExtraWorkFields,
    ): Boolean {
        val record = records[id] ?: return false
        records[id] = record.copy(fields = fields)
        return true
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean = records.remove(id) != null
}

class FakeMaterialRepository : MaterialRepository {

    val records = linkedMapOf<UUID, MaterialRecord>()

    override suspend fun listByObject(objectId: UUID): List<MaterialRecord> =
        records.values.filter { it.objectId == objectId }.sortedBy { it.sortOrder }

    override suspend fun find(owner: OwnerId, id: UUID): MaterialRecord? = records[id]

    override suspend fun insert(material: MaterialRecord) {
        records[material.id] = material
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: MaterialFields,
    ): Boolean {
        val record = records[id] ?: return false
        records[id] = record.copy(fields = fields)
        return true
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean = records.remove(id) != null

    override suspend fun nextSortOrder(objectId: UUID): Int =
        (records.values.filter { it.objectId == objectId }.maxOfOrNull { it.sortOrder } ?: 0) + 1
}

class FakeTaskRepository : TaskRepository {

    val records = linkedMapOf<UUID, TaskRecord>()

    override suspend fun list(owner: OwnerId, query: TaskQuery): List<TaskRecord> = records.values
        .filter { it.ownerId == owner }
        .filter { query.from == null || it.fields.day >= query.from }
        .filter { query.to == null || it.fields.day <= query.to }
        .filter { !query.openOnly || !it.fields.done }
        .sortedWith(
            compareBy<TaskRecord> { it.fields.day }
                .thenBy { it.fields.remindAtMinutes ?: Int.MAX_VALUE }
                .thenBy { it.createdAt },
        )

    override suspend fun find(owner: OwnerId, id: UUID): TaskRecord? = records[id]?.takeIf { it.ownerId == owner }

    override suspend fun insert(task: TaskRecord) {
        records[task.id] = task
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: TaskFields,
    ): Boolean {
        val record = records[id]?.takeIf { it.ownerId == owner } ?: return false
        records[id] = record.copy(fields = fields)
        return true
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean =
        records[id]?.takeIf { it.ownerId == owner }?.let { records.remove(id) } != null
}

/** Runs the block directly; the fakes have no transactions to join. */
object ImmediateTransactor : Transactor {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}

fun aPhotoRecord(
    objectId: UUID,
    id: UUID = UUID.randomUUID(),
    sortOrder: Int = 1,
    createdAt: Instant = FIXED_NOW,
    kind: AttachmentKindDto = AttachmentKindDto.PHOTO,
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
    kind = kind,
)

/** The finance repositories as fakes, for route tests that start the whole service module. */
fun financeFakes(): org.koin.core.module.Module = org.koin.dsl.module {
    single<FinanceTermsRepository> { FakeFinanceTermsRepository() }
    single<PaymentRepository> { FakePaymentRepository() }
    single<ExtraWorkRepository> { FakeExtraWorkRepository() }
    single<MaterialRepository> { FakeMaterialRepository() }
    single<TaskRepository> { FakeTaskRepository() }
    single<ru.prorabprime.server.db.Transactor> { ImmediateTransactor }
}
