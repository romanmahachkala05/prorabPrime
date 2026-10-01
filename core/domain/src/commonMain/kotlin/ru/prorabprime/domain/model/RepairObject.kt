package ru.prorabprime.domain.model

import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class ObjectStatus {
    PLANNED,
    IN_PROGRESS,
    DONE,
    PAUSED,
}

/** A point on the globe, in degrees. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** One row of the objects list. */
data class ObjectSummary(
    val id: ObjectId,
    val title: String?,
    val address: String,
    val status: ObjectStatus,
    val clientName: String?,
    val coverThumbPath: ServerFilePath?,
    val photoCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Made or changed on the phone and not yet accepted by the server. */
    val isPending: Boolean = false,
) {
    /** Where the object is on the map; null while its address has not been located. */
    val point: GeoPoint? get() = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null

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
    val chatLink: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coverPhotoId: PhotoId?,
    val contacts: ImmutableList<Contact> = persistentListOf(),
    /** In carousel order. */
    val photos: ImmutableList<Photo>,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Made or changed on the phone and not yet accepted by the server. */
    val isPending: Boolean = false,
) {
    /** Where the object is on the map; null while its address has not been located. */
    val point: GeoPoint? get() = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null

    val displayTitle: String get() = title ?: address
}

/** Which folder of the object a file is in. */
enum class AttachmentKind {
    PHOTO,
    RECEIPT,
}

data class Photo(
    val id: PhotoId,
    val path: ServerFilePath,
    val thumbPath: ServerFilePath,
    val width: Int,
    val height: Int,
    val createdAt: Instant,
    val kind: AttachmentKind = AttachmentKind.PHOTO,
    /** Taken on the phone and not yet on the server; its paths are then files on the phone. */
    val isPending: Boolean = false,
    /** Clockwise quarter turns asked for and not yet made by the server; the picture is shown turned by them. */
    val quarterTurns: Int = 0,
    /** What the foreman wrote about it. */
    val note: String? = null,
)
