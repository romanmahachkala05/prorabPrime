package ru.prorabprime.contract

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class ObjectStatusDto {
    PLANNED,
    IN_PROGRESS,
    DONE,
    PAUSED,
}

/** One row of the objects list. URLs are relative (`/files/...`); the client resolves them. */
@Serializable
data class ObjectSummaryDto(
    val id: String,
    val title: String? = null,
    val address: String,
    val status: ObjectStatusDto,
    val clientName: String? = null,
    val coverThumbUrl: String? = null,
    val photoCount: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class ObjectDetailsDto(
    val id: String,
    val title: String? = null,
    val address: String,
    val status: ObjectStatusDto,
    val clientName: String? = null,
    val clientPhone: String? = null,
    val notes: String? = null,
    val chatLink: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coverPhotoId: String? = null,
    val contacts: List<ContactDto> = emptyList(),
    val photos: List<PhotoDto>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Which folder of the object a file belongs to. */
@Serializable
enum class AttachmentKindDto {
    PHOTO,
    RECEIPT,
}

@Serializable
data class PhotoDto(
    val id: String,
    val url: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val createdAt: Instant,
    val kind: AttachmentKindDto = AttachmentKindDto.PHOTO,
    /** What the foreman wrote about it; absent when nothing is written. */
    val note: String? = null,
    /** Read from the QR code of a receipt; absent for a photo and for a receipt without a readable code. */
    val receipt: ReceiptDto? = null,
)

@Serializable
data class ReceiptDto(
    val amountKopecks: Long,
    /** Local time at the shop, `2026-10-01T15:26`; absent when the code has none. */
    val purchasedAt: String? = null,
)

/** Body of `POST /api/objects` and `PUT /api/objects/{id}`; a PUT replaces every field. */
@Serializable
data class ObjectRequestDto(
    val title: String? = null,
    val address: String,
    val status: ObjectStatusDto,
    val clientName: String? = null,
    val clientPhone: String? = null,
    val notes: String? = null,
    val chatLink: String? = null,
    /** The point picked on the map, both or neither; without it the server looks the address up. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Chosen by the client on create (a UUID), so a retry of the same create finds the record it made. */
    val id: String? = null,
)

/** The address found at a point of the map; null when there is none. */
@Serializable
data class AddressDto(
    val address: String? = null,
)

@Serializable
data class ObjectCreatedDto(
    val id: String,
)

@Serializable
data class SetCoverRequestDto(
    val photoId: String,
)

/** Body of `PUT /api/photos/{id}/note`; an empty or missing note clears it. */
@Serializable
data class PhotoNoteRequestDto(
    val note: String? = null,
)

/**
 * Body of `POST /api/photos/{id}/rotate`. [rotationId] (a UUID the client picks) names the file the
 * turn produces, so a retry of a turn the server already made changes nothing.
 */
@Serializable
data class RotatePhotoRequestDto(
    /** Clockwise, 1 to 3. */
    val quarterTurns: Int,
    val rotationId: String,
)

@Serializable
data class HealthDto(
    val status: String,
)
