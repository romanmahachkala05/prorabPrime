package ru.prorabprime.domain.model

import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** An object that was deleted and can still be restored. */
data class DeletedObject(
    val id: ObjectId,
    val title: String?,
    val address: String,
    val coverThumbPath: ServerFilePath?,
    val photoCount: Int,
    val deletedAt: Instant,
    /** Whole days until the server removes it for good. */
    val daysLeft: Int,
)

/** A photo or receipt that was deleted from an object that still exists. */
data class DeletedPhoto(
    val id: PhotoId,
    val objectId: ObjectId,
    val objectTitle: String?,
    val objectAddress: String,
    val kind: AttachmentKind,
    val thumbPath: ServerFilePath,
    val deletedAt: Instant,
    val daysLeft: Int,
)

/** What the trash holds, the most recently deleted first. */
data class Trash(
    val objects: ImmutableList<DeletedObject> = persistentListOf(),
    val photos: ImmutableList<DeletedPhoto> = persistentListOf(),
) {
    val isEmpty: Boolean get() = objects.isEmpty() && photos.isEmpty()
}
