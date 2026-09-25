package ru.prorabprime.data.mapper

import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.ObjectSummaryDto
import ru.prorabprime.contract.PhotoDto
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.ServerFilePath

internal fun ObjectSummaryDto.toDomain() = ObjectSummary(
    id = ObjectId(id),
    title = title,
    address = address,
    status = status.toDomain(),
    clientName = clientName,
    coverThumbPath = coverThumbUrl?.let(::ServerFilePath),
    photoCount = photoCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ObjectDetailsDto.toDomain() = ObjectDetails(
    id = ObjectId(id),
    title = title,
    address = address,
    status = status.toDomain(),
    clientName = clientName,
    clientPhone = clientPhone,
    notes = notes,
    coverPhotoId = coverPhotoId?.let(::PhotoId),
    photos = photos.map { it.toDomain() }.toImmutableList(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun PhotoDto.toDomain() = Photo(
    id = PhotoId(id),
    path = ServerFilePath(url),
    thumbPath = ServerFilePath(thumbUrl),
    width = width,
    height = height,
    createdAt = createdAt,
)

internal fun ObjectDraft.toRequestDto() = ObjectRequestDto(
    title = title,
    address = address,
    status = status.toDto(),
    clientName = clientName,
    clientPhone = clientPhone,
    notes = notes,
)

internal fun ObjectStatusDto.toDomain(): ObjectStatus = when (this) {
    ObjectStatusDto.PLANNED -> ObjectStatus.PLANNED
    ObjectStatusDto.IN_PROGRESS -> ObjectStatus.IN_PROGRESS
    ObjectStatusDto.DONE -> ObjectStatus.DONE
    ObjectStatusDto.PAUSED -> ObjectStatus.PAUSED
}

internal fun ObjectStatus.toDto(): ObjectStatusDto = when (this) {
    ObjectStatus.PLANNED -> ObjectStatusDto.PLANNED
    ObjectStatus.IN_PROGRESS -> ObjectStatusDto.IN_PROGRESS
    ObjectStatus.DONE -> ObjectStatusDto.DONE
    ObjectStatus.PAUSED -> ObjectStatusDto.PAUSED
}

internal fun ObjectFieldDto.toDomain(): ObjectField = when (this) {
    ObjectFieldDto.TITLE -> ObjectField.TITLE
    ObjectFieldDto.ADDRESS -> ObjectField.ADDRESS
    ObjectFieldDto.CLIENT_NAME -> ObjectField.CLIENT_NAME
    ObjectFieldDto.CLIENT_PHONE -> ObjectField.CLIENT_PHONE
    ObjectFieldDto.NOTES -> ObjectField.NOTES
}

internal fun FieldProblemDto.toDomain(): FieldProblem = when (this) {
    FieldProblemDto.REQUIRED -> FieldProblem.REQUIRED
    FieldProblemDto.TOO_LONG -> FieldProblem.TOO_LONG
    FieldProblemDto.INVALID -> FieldProblem.INVALID
}

/** The query parameters one [ObjectSort] option asks for. */
internal fun ObjectSort.toQuery(): Pair<SortFieldDto, SortOrderDto> = when (this) {
    ObjectSort.ADDRESS_ASC -> SortFieldDto.ADDRESS to SortOrderDto.ASC
    ObjectSort.ADDRESS_DESC -> SortFieldDto.ADDRESS to SortOrderDto.DESC
    ObjectSort.CREATED_NEWEST -> SortFieldDto.CREATED to SortOrderDto.DESC
    ObjectSort.UPDATED_NEWEST -> SortFieldDto.UPDATED to SortOrderDto.DESC
}
