package ru.prorabprime.contract

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** How long a deleted object or photo waits in the trash before the server removes it for good. */
object TrashLimits {
    const val RETENTION_DAYS = 30
}

/** An object that was deleted. URLs are relative (`/files/...`), as everywhere. */
@Serializable
data class DeletedObjectDto(
    val id: String,
    val title: String? = null,
    val address: String,
    val coverThumbUrl: String? = null,
    val photoCount: Int,
    val deletedAt: Instant,
    /** Whole days until the server removes it for good; at least 1 while it is still here. */
    val daysLeft: Int,
)

/** A photo or receipt that was deleted from an object that still exists. */
@Serializable
data class DeletedPhotoDto(
    val id: String,
    val objectId: String,
    val objectTitle: String? = null,
    val objectAddress: String,
    val kind: AttachmentKindDto,
    val thumbUrl: String,
    val deletedAt: Instant,
    val daysLeft: Int,
)

/** Body of `GET /api/trash`: the most recently deleted first. */
@Serializable
data class TrashDto(
    val objects: List<DeletedObjectDto>,
    val photos: List<DeletedPhotoDto>,
)
