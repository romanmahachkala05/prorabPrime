package ru.prorabprime.data.local

import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.network.localFilePath
import ru.prorabprime.domain.model.Contact
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.ServerFilePath

/*
 * What a screen shows, put together from the rows on the phone with the same rules the server uses,
 * so that the list reads the same offline as online.
 */

internal fun PhotoRow.thumbPath(blobs: BlobStore): ServerFilePath? =
    thumbUrl?.let(::ServerFilePath) ?: localBlob?.let(blobs::pathOf)?.let(::localFilePath)

private fun PhotoRow.fullPath(blobs: BlobStore): ServerFilePath? =
    url?.let(::ServerFilePath) ?: localBlob?.let(blobs::pathOf)?.let(::localFilePath)

internal fun PhotoRow.toDomain(blobs: BlobStore) = Photo(
    id = PhotoId(id),
    path = fullPath(blobs) ?: ServerFilePath(""),
    thumbPath = thumbPath(blobs) ?: ServerFilePath(""),
    width = width,
    height = height,
    createdAt = createdAt,
    kind = kind.toDomain(),
    isPending = localBlob != null,
    quarterTurns = quarterTurns,
)

internal fun ObjectRow.toSummary(
    photos: List<PhotoRow>,
    pending: Boolean,
    blobs: BlobStore,
): ObjectSummary {
    val cover = coverPhotoId?.let { id -> photos.find { it.id == id } }
    return ObjectSummary(
        id = ObjectId(id),
        title = title,
        address = address,
        status = status.toDomain(),
        clientName = clientName,
        coverThumbPath = cover?.thumbPath(blobs),
        photoCount = photos.count { it.kind == AttachmentKindDto.PHOTO },
        createdAt = createdAt,
        updatedAt = updatedAt,
        latitude = latitude,
        longitude = longitude,
        isPending = pending,
    )
}

internal fun ObjectRow.toDetails(
    contacts: List<ContactRow>,
    photos: List<PhotoRow>,
    dirty: Set<String>,
    blobs: BlobStore,
): ObjectDetails {
    val shown = ObjectDetailsDto(
        id = id,
        title = title,
        address = address,
        status = status,
        clientName = clientName,
        clientPhone = clientPhone,
        notes = notes,
        chatLink = chatLink,
        latitude = latitude,
        longitude = longitude,
        coverPhotoId = coverPhotoId,
        photos = emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    ).toDomain()
    return shown.copy(
        contacts = contacts.sortedBy { it.order }.map { row ->
            row.dto.toDomain().let { Contact(it.id, it.name, it.phone, it.role, Keys.contact(row.dto.id) in dirty) }
        }.toImmutableList(),
        photos = photos.sortedBy { it.order }.map { it.toDomain(blobs) }.toImmutableList(),
        isPending = Keys.obj(id) in dirty,
    )
}

/** The list a query asks for: filtered by what is in the address or title, sorted as the server sorts. */
internal fun List<ObjectSummary>.matching(query: ObjectQuery): List<ObjectSummary> {
    val needle = query.search.trim().lowercase()
    val found = if (needle.isEmpty()) {
        this
    } else {
        filter {
            "${it.address} ${it.title.orEmpty()}".lowercase().contains(needle)
        }
    }
    val order: Comparator<ObjectSummary> = when (query.sort) {
        ObjectSort.ADDRESS_ASC -> compareBy { it.address.lowercase() }
        ObjectSort.ADDRESS_DESC -> compareByDescending { it.address.lowercase() }
        ObjectSort.CREATED_NEWEST -> compareByDescending { it.createdAt }
        ObjectSort.UPDATED_NEWEST -> compareByDescending { it.updatedAt }
    }
    // The id breaks ties, so equal addresses or timestamps keep a stable order.
    return found.sortedWith(order.thenBy { it.id.value })
}
