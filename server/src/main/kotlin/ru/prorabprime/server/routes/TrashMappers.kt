package ru.prorabprime.server.routes

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import ru.prorabprime.contract.DeletedObjectDto
import ru.prorabprime.contract.DeletedPhotoDto
import ru.prorabprime.contract.TrashDto
import ru.prorabprime.contract.TrashLimits
import ru.prorabprime.server.model.TrashedObject
import ru.prorabprime.server.model.TrashedPhoto
import ru.prorabprime.server.service.Trash

/** Whole days left before the server removes what was deleted at [deletedAt]; a started day counts. */
internal fun daysLeft(deletedAt: Instant, now: Instant): Int {
    val remaining = deletedAt + TrashLimits.RETENTION_DAYS.days - now
    val days = remaining.inWholeSeconds.coerceAtLeast(0).let { (it + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY }
    return days.toInt().coerceAtLeast(1)
}

private const val SECONDS_PER_DAY = 86_400L

fun Trash.toDto(now: Instant) = TrashDto(
    objects = objects.map { it.toDto(now) },
    photos = photos.map { it.toDto(now) },
)

fun TrashedObject.toDto(now: Instant) = DeletedObjectDto(
    id = record.id.toString(),
    title = record.fields.title,
    address = record.fields.address,
    coverThumbUrl = coverThumbFileName?.let { fileUrl(record.id, it) },
    photoCount = photoCount,
    deletedAt = deletedAt,
    daysLeft = daysLeft(deletedAt, now),
)

fun TrashedPhoto.toDto(now: Instant) = DeletedPhotoDto(
    id = photo.id.toString(),
    objectId = photo.objectId.toString(),
    objectTitle = objectTitle,
    objectAddress = objectAddress,
    kind = photo.kind,
    thumbUrl = fileUrl(photo.objectId, photo.thumbFileName),
    deletedAt = deletedAt,
    daysLeft = daysLeft(deletedAt, now),
)
