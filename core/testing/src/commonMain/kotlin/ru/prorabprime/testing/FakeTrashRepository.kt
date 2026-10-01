package ru.prorabprime.testing

import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.DeletedObject
import ru.prorabprime.domain.model.DeletedPhoto
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.model.Trash
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.TrashRepository

/** In-memory [TrashRepository]: what is in [objects] and [photos] is the trash, and every call is recorded. */
class FakeTrashRepository : TrashRepository {

    val objects = mutableListOf<DeletedObject>()
    val photos = mutableListOf<DeletedPhoto>()

    /** When set, every call fails with it. */
    var error: AppError? = null

    val restoredObjects = mutableListOf<ObjectId>()
    val restoredPhotos = mutableListOf<PhotoId>()
    val purgedObjects = mutableListOf<ObjectId>()
    val purgedPhotos = mutableListOf<PhotoId>()
    var emptied = 0
        private set

    override suspend fun load(): Result<Trash> =
        error?.asFailure() ?: Result.success(Trash(objects.toImmutableList(), photos.toImmutableList()))

    override suspend fun restoreObject(id: ObjectId): Result<Unit> = write {
        restoredObjects += id
        objects.removeAll { it.id == id }
    }

    override suspend fun restorePhoto(id: PhotoId): Result<Unit> = write {
        restoredPhotos += id
        photos.removeAll { it.id == id }
    }

    override suspend fun purgeObject(id: ObjectId): Result<Unit> = write {
        purgedObjects += id
        objects.removeAll { it.id == id }
    }

    override suspend fun purgePhoto(id: PhotoId): Result<Unit> = write {
        purgedPhotos += id
        photos.removeAll { it.id == id }
    }

    override suspend fun empty(): Result<Unit> = write {
        emptied++
        objects.clear()
        photos.clear()
    }

    private fun write(block: () -> Unit): Result<Unit> {
        error?.let { return it.asFailure() }
        block()
        return Result.success(Unit)
    }
}

private val DELETED_AT = Instant.parse("2026-10-01T10:00:00Z")

fun aDeletedObject(
    id: String = "object-1",
    title: String? = "Кухня",
    daysLeft: Int = 30,
    photoCount: Int = 2,
) = DeletedObject(ObjectId(id), title, "ул. Ленина, 1", null, photoCount, DELETED_AT, daysLeft)

fun aDeletedPhoto(
    id: String = "photo-1",
    objectId: String = "object-1",
    kind: AttachmentKind = AttachmentKind.PHOTO,
    daysLeft: Int = 30,
) = DeletedPhoto(
    PhotoId(id),
    ObjectId(objectId),
    "Кухня",
    "ул. Ленина, 1",
    kind,
    ServerFilePath("/files/$objectId/${id}_thumb.jpg"),
    DELETED_AT,
    daysLeft,
)
