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
    val coverPhotoId: String? = null,
    val photos: List<PhotoDto>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class PhotoDto(
    val id: String,
    val url: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val createdAt: Instant,
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
)

@Serializable
data class ObjectCreatedDto(
    val id: String,
)

@Serializable
data class SetCoverRequestDto(
    val photoId: String,
)

@Serializable
data class HealthDto(
    val status: String,
)
