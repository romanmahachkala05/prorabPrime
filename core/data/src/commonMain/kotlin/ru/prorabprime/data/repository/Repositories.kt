package ru.prorabprime.data.repository

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ContactDto
import ru.prorabprime.data.local.ContactRow
import ru.prorabprime.data.local.IdFactory
import ru.prorabprime.data.local.Keys
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.local.LocalSnapshot
import ru.prorabprime.data.local.ObjectRow
import ru.prorabprime.data.local.Operation
import ru.prorabprime.data.local.PhotoRow
import ru.prorabprime.data.local.forgetOrQueueDelete
import ru.prorabprime.data.local.matching
import ru.prorabprime.data.local.observeSnapshot
import ru.prorabprime.data.local.toDetails
import ru.prorabprime.data.local.toDomain
import ru.prorabprime.data.local.toSummary
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.mapper.toDto
import ru.prorabprime.data.mapper.toRequestDto
import ru.prorabprime.data.remote.RemoteApi
import ru.prorabprime.data.sync.SyncEngine
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactId
import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.ContactsRepository
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.repository.PhotosRepository

/**
 * Objects as the phone has them. Screens read the local copy and never wait for the network; a change
 * is written to the copy at once and queued for the server (ADR-0017). The queue entry is made first:
 * a sync that runs in between then already knows the row is waiting and leaves it alone.
 */
internal class ObjectsRepositoryImpl(
    private val db: LocalDb,
    private val engine: SyncEngine,
    private val remote: RemoteApi,
    private val clock: Clock,
    private val ids: IdFactory,
) : ObjectsRepository {

    private val snapshot: Flow<LocalSnapshot> get() = db.observeSnapshot(engine.state)

    override fun observeObjects(query: ObjectQuery): Flow<Result<ImmutableList<ObjectSummary>>> =
        snapshot.transform { s ->
            val error = readiness(s)
            when {
                error != null -> emit(error.asFailure())

                s.objects.isNotEmpty() || s.meta.lastSyncAt != null ->
                    emit(Result.success(summaries(s).matching(query).toImmutableList()))
            }
        }

    private fun summaries(s: LocalSnapshot): List<ObjectSummary> = s.objects.values.map { row ->
        row.toSummary(s.photos.filter { it.objectId == row.id }, Keys.obj(row.id) in s.dirty, db.blobs)
    }

    override fun observeObject(id: ObjectId): Flow<Result<ObjectDetails>> = snapshot.transform { s ->
        val row = s.objects[id.value]
        when {
            row != null -> emit(Result.success(row.toDetails(s.contactsOf(id), s.photosOf(id), s.dirty, db.blobs)))
            s.meta.syncTried -> emit(AppError.NotFound.asFailure())
        }
    }

    override suspend fun refresh() {
        engine.sync()
    }

    override suspend fun create(draft: ObjectDraft): Result<ObjectId> {
        val id = ids.next()
        db.outbox.enqueue(Operation.CreateObject(draft.toRequestDto().copy(id = id)))
        val now = clock.now()
        db.objects.upsert(draft.toRow(id, createdAt = now, updatedAt = now, serverUpdatedAt = null))
        return Result.success(ObjectId(id))
    }

    override suspend fun update(id: ObjectId, draft: ObjectDraft): Result<Unit> {
        val row = db.objects.rows.value[id.value] ?: return AppError.NotFound.asFailure()
        db.outbox.enqueue(Operation.UpdateObject(id.value, draft.toRequestDto()))
        // A new address is somewhere else: its old pin goes, and the server looks the new one up.
        val moved = draft.address != row.address
        val point = draft.point ?: if (moved) null else row.point()
        db.objects.upsert(
            draft.toRow(id.value, row.createdAt, clock.now(), row.serverUpdatedAt).copy(
                coverPhotoId = row.coverPhotoId,
                latitude = point?.latitude,
                longitude = point?.longitude,
            ),
        )
        return Result.success(Unit)
    }

    override suspend fun delete(id: ObjectId): Result<Unit> {
        val row = db.objects.rows.value[id.value] ?: return Result.success(Unit)
        val children = childKeys(id.value)
        // Changes to the object's own records have nothing left to change once it is gone.
        db.outbox.removeWhere { op -> op.operation.touched.any { it in children } }
        db.photos.rows.value.values.filter {
            it.objectId == id.value
        }.forEach { it.localBlob?.let { b -> db.blobs.delete(b) } }
        if (row.serverUpdatedAt == null) {
            // Only the phone ever knew it: forget it, say nothing to the server.
            db.outbox.removeWhere { Keys.obj(id.value) in it.operation.touched }
        } else {
            db.outbox.enqueue(Operation.DeleteObject(id.value))
        }
        db.objects.remove(id.value)
        removeChildRows(id.value)
        return Result.success(Unit)
    }

    /** Online only: the server's map lookup is not something the phone can do. */
    override suspend fun geocode(id: ObjectId): Result<Unit> =
        remote.geocodeObject(id.value).onSuccess { engine.sync() }

    private fun childKeys(objectId: String): Set<String> =
        db.contacts.rows.value.values.filter { it.objectId == objectId }.map { Keys.contact(it.dto.id) }.toSet() +
            db.photos.rows.value.values.filter { it.objectId == objectId }.map { Keys.photo(it.id) }.toSet() +
            db.payments.rows.value.values.filter { it.objectId == objectId }.map { Keys.payment(it.dto.id) }.toSet() +
            db.extraWorks.rows.value.values.filter { it.objectId == objectId }.map { Keys.extra(it.dto.id) }.toSet() +
            db.materials.rows.value.values.filter { it.objectId == objectId }.map { Keys.material(it.dto.id) }.toSet() +
            setOf(Keys.terms(objectId), Keys.materialsOf(objectId))

    private suspend fun removeChildRows(objectId: String) {
        db.contacts.change { rows -> rows.values.removeAll { it.objectId == objectId } }
        db.photos.change { rows -> rows.values.removeAll { it.objectId == objectId } }
        db.payments.change { rows -> rows.values.removeAll { it.objectId == objectId } }
        db.extraWorks.change { rows -> rows.values.removeAll { it.objectId == objectId } }
        db.materials.change { rows -> rows.values.removeAll { it.objectId == objectId } }
        db.terms.remove(objectId)
        db.history.remove(objectId)
    }
}

/** An empty copy that never managed to sync is an error to show, not an empty list to believe. */
private fun readiness(s: LocalSnapshot): AppError? = when {
    s.objects.isNotEmpty() || s.meta.lastSyncAt != null -> null
    s.sync.unauthorized -> AppError.Unauthorized
    s.meta.syncTried -> AppError.Network
    else -> null
}

private fun ObjectRow.point(): GeoPoint? = latitude?.let { lat -> longitude?.let { lon -> GeoPoint(lat, lon) } }

private fun LocalSnapshot.contactsOf(id: ObjectId) = contacts.filter { it.objectId == id.value }

private fun LocalSnapshot.photosOf(id: ObjectId) = photos.filter { it.objectId == id.value }

private fun ObjectDraft.toRow(
    id: String,
    createdAt: Instant,
    updatedAt: Instant,
    serverUpdatedAt: Instant?,
) = ObjectRow(
    id = id,
    title = title,
    address = address,
    status = status.toDto(),
    clientName = clientName,
    clientPhone = clientPhone,
    notes = notes,
    chatLink = chatLink,
    latitude = point?.latitude,
    longitude = point?.longitude,
    createdAt = createdAt,
    updatedAt = updatedAt,
    serverUpdatedAt = serverUpdatedAt,
)

/**
 * Pictures. One taken on the phone is saved as a file at once and queued; it shows with a mark until the
 * server has it, and nothing else about the object has to wait for it.
 */
internal class PhotosRepositoryImpl(
    private val db: LocalDb,
    private val clock: Clock,
    private val ids: IdFactory,
) : PhotosRepository {

    override suspend fun upload(
        objectId: ObjectId,
        image: CompressedImage,
        kind: AttachmentKind,
        note: String?,
    ): Result<Photo> {
        val obj = db.objects.rows.value[objectId.value] ?: return AppError.NotFound.asFailure()
        val id = ids.next()
        val text = note?.trim()?.takeIf { it.isNotEmpty() }
        val blob = "$id.${extensionOf(image.mimeType)}"
        db.blobs.put(blob, image.bytes)
        db.outbox.enqueue(Operation.UploadPhoto(objectId.value, id, kind.toDto(), blob, image.mimeType, text))
        val siblings = db.photos.rows.value.values.filter { it.objectId == objectId.value }
        val row = PhotoRow(
            objectId = objectId.value,
            order = (siblings.maxOfOrNull { it.order } ?: -1) + 1,
            id = id,
            kind = kind.toDto(),
            width = 0,
            height = 0,
            createdAt = clock.now(),
            localBlob = blob,
            note = text,
        )
        db.photos.upsert(row)
        // The first photo of an object without a cover becomes the cover; a receipt never does.
        if (kind == AttachmentKind.PHOTO && obj.coverPhotoId == null) {
            db.objects.upsert(obj.copy(coverPhotoId = id))
        }
        return Result.success(row.toDomain(db.blobs))
    }

    override suspend fun delete(id: PhotoId): Result<Unit> {
        val row = db.photos.rows.value[id.value] ?: return Result.success(Unit)
        if (row.localBlob != null) {
            // Never left the phone: forget it, say nothing to the server.
            db.outbox.removeWhere { Keys.photo(id.value) in it.operation.touched }
            db.blobs.delete(row.localBlob)
        } else {
            db.outbox.enqueue(Operation.DeletePhoto(id.value))
        }
        db.photos.remove(id.value)
        replaceCoverIfNeeded(row)
        return Result.success(Unit)
    }

    override suspend fun setNote(id: PhotoId, note: String?): Result<Unit> {
        val row = db.photos.rows.value[id.value] ?: return AppError.NotFound.asFailure()
        val text = note?.trim()?.takeIf { it.isNotEmpty() }
        db.outbox.enqueue(Operation.SetPhotoNote(id.value, text))
        db.photos.upsert(row.copy(note = text))
        return Result.success(Unit)
    }

    override suspend fun rotate(id: PhotoId): Result<Unit> {
        val row = db.photos.rows.value[id.value] ?: return AppError.NotFound.asFailure()
        db.outbox.enqueue(Operation.RotatePhoto(id.value, quarterTurns = 1, rotationId = ids.next()))
        db.photos.upsert(row.copy(quarterTurns = (row.quarterTurns + 1) % FULL_TURN))
        return Result.success(Unit)
    }

    override suspend fun setCover(objectId: ObjectId, photoId: PhotoId): Result<Unit> {
        val obj = db.objects.rows.value[objectId.value] ?: return AppError.NotFound.asFailure()
        db.outbox.enqueue(Operation.SetCover(objectId.value, photoId.value))
        db.objects.upsert(obj.copy(coverPhotoId = photoId.value))
        return Result.success(Unit)
    }

    /** A deleted cover is replaced by the newest remaining photo, or by none, as the server does. */
    private suspend fun replaceCoverIfNeeded(deleted: PhotoRow) {
        val obj = db.objects.rows.value[deleted.objectId] ?: return
        if (obj.coverPhotoId != deleted.id) return
        val next = db.photos.rows.value.values
            .filter { it.objectId == obj.id && it.kind == AttachmentKindDto.PHOTO }
            .maxByOrNull { it.order }
        db.objects.upsert(obj.copy(coverPhotoId = next?.id))
    }

    private fun extensionOf(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
}

/** A contact shows on the object's card, and is written the way the object is. */
internal class ContactsRepositoryImpl(
    private val db: LocalDb,
    private val ids: IdFactory,
) : ContactsRepository {

    override suspend fun create(objectId: ObjectId, draft: ContactDraft): Result<ContactId> {
        if (db.objects.rows.value[objectId.value] == null) return AppError.NotFound.asFailure()
        val id = ids.next()
        db.outbox.enqueue(Operation.CreateContact(objectId.value, draft.toRequestDto().copy(id = id)))
        val siblings = db.contacts.rows.value.values.filter { it.objectId == objectId.value }
        val order = (siblings.maxOfOrNull { it.order } ?: -1) + 1
        db.contacts.upsert(ContactRow(objectId.value, order, draft.toDto(id)))
        return Result.success(ContactId(id))
    }

    override suspend fun update(id: ContactId, draft: ContactDraft): Result<Unit> {
        val row = db.contacts.rows.value[id.value] ?: return AppError.NotFound.asFailure()
        db.outbox.enqueue(Operation.UpdateContact(id.value, draft.toRequestDto()))
        db.contacts.upsert(row.copy(dto = draft.toDto(id.value)))
        return Result.success(Unit)
    }

    override suspend fun delete(id: ContactId): Result<Unit> {
        db.contacts.rows.value[id.value] ?: return Result.success(Unit)
        db.forgetOrQueueDelete(Keys.contact(id.value), Operation.DeleteContact(id.value))
        db.contacts.remove(id.value)
        return Result.success(Unit)
    }

    private fun ContactDraft.toDto(id: String) = ContactDto(id, name, phone, role.toDto())
}

private const val FULL_TURN = 4
