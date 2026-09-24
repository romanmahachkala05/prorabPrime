package ru.prorabprime.testing

import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId

val TEST_INSTANT: Instant = Instant.parse("2026-09-25T10:00:00Z")

fun anObjectSummary(
    id: String = "object-1",
    title: String? = null,
    address: String = "ул. Ленина, 1",
    status: ObjectStatus = ObjectStatus.IN_PROGRESS,
    coverThumbPath: String? = null,
    photoCount: Int = 0,
) = ObjectSummary(
    id = ObjectId(id),
    title = title,
    address = address,
    status = status,
    clientName = null,
    coverThumbPath = coverThumbPath,
    photoCount = photoCount,
    createdAt = TEST_INSTANT,
    updatedAt = TEST_INSTANT,
)

fun anObjectDetails(
    id: String = "object-1",
    title: String? = null,
    address: String = "ул. Ленина, 1",
    status: ObjectStatus = ObjectStatus.IN_PROGRESS,
    coverPhotoId: String? = null,
    photos: ImmutableList<Photo> = persistentListOf(),
) = ObjectDetails(
    id = ObjectId(id),
    title = title,
    address = address,
    status = status,
    clientName = null,
    clientPhone = null,
    notes = null,
    coverPhotoId = coverPhotoId?.let(::PhotoId),
    photos = photos,
    createdAt = TEST_INSTANT,
    updatedAt = TEST_INSTANT,
)

fun aPhoto(id: String = "photo-1", objectId: String = "object-1") = Photo(
    id = PhotoId(id),
    path = "/files/$objectId/$id.jpg",
    thumbPath = "/files/$objectId/${id}_thumb.jpg",
    width = 2048,
    height = 1536,
    createdAt = TEST_INSTANT,
)
