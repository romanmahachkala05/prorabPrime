package ru.prorabprime.domain.model

import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList

enum class ObjectStatus {
    PLANNED,
    IN_PROGRESS,
    DONE,
    PAUSED,
}

/** One row of the objects list. [coverThumbPath] is relative to the server (`/files/...`). */
data class ObjectSummary(
    val id: ObjectId,
    val title: String?,
    val address: String,
    val status: ObjectStatus,
    val clientName: String?,
    val coverThumbPath: String?,
    val photoCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** What the list shows as the heading: the title, or the address when there is none. */
    val displayTitle: String get() = title ?: address
}

data class ObjectDetails(
    val id: ObjectId,
    val title: String?,
    val address: String,
    val status: ObjectStatus,
    val clientName: String?,
    val clientPhone: String?,
    val notes: String?,
    val coverPhotoId: PhotoId?,
    /** In carousel order. */
    val photos: ImmutableList<Photo>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val displayTitle: String get() = title ?: address
}

/** Paths are relative to the server (`/files/...`); the HTTP client resolves them. */
data class Photo(
    val id: PhotoId,
    val path: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val createdAt: Instant,
)
