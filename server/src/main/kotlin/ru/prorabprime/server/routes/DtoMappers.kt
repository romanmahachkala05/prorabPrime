package ru.prorabprime.server.routes

import java.util.UUID
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.contract.ObjectSummaryDto
import ru.prorabprime.contract.PhotoDto
import ru.prorabprime.server.model.ObjectDetails
import ru.prorabprime.server.model.ObjectListItem
import ru.prorabprime.server.model.PhotoRecord

/** Relative on purpose: the client knows the address it reached the server by (ADR-0003). */
fun fileUrl(objectId: UUID, fileName: String) = "/files/$objectId/$fileName"

fun ObjectListItem.toSummaryDto() = ObjectSummaryDto(
    id = record.id.toString(),
    title = record.fields.title,
    address = record.fields.address,
    status = record.fields.status,
    clientName = record.fields.clientName,
    coverThumbUrl = coverThumbFileName?.let { fileUrl(record.id, it) },
    photoCount = photoCount,
    createdAt = record.createdAt,
    updatedAt = record.updatedAt,
)

fun ObjectDetails.toDetailsDto() = ObjectDetailsDto(
    id = record.id.toString(),
    title = record.fields.title,
    address = record.fields.address,
    status = record.fields.status,
    clientName = record.fields.clientName,
    clientPhone = record.fields.clientPhone,
    notes = record.fields.notes,
    coverPhotoId = record.coverPhotoId?.toString(),
    photos = photos.map { it.toDto() },
    createdAt = record.createdAt,
    updatedAt = record.updatedAt,
)

fun PhotoRecord.toDto() = PhotoDto(
    id = id.toString(),
    url = fileUrl(objectId, fileName),
    thumbUrl = fileUrl(objectId, thumbFileName),
    width = width,
    height = height,
    createdAt = createdAt,
)
