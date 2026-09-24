package ru.prorabprime.server.model

import java.util.UUID
import kotlin.time.Instant
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto

/** The editable fields of an object, already trimmed and validated. */
data class ObjectFields(
    val title: String?,
    val address: String,
    val status: ObjectStatusDto,
    val clientName: String?,
    val clientPhone: String?,
    val notes: String?,
)

data class ObjectRecord(
    val id: UUID,
    val fields: ObjectFields,
    val coverPhotoId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** A row of the objects list, with what the list shows about the object's photos. */
data class ObjectListItem(
    val record: ObjectRecord,
    val coverThumbFileName: String?,
    val photoCount: Int,
)

data class ObjectDetails(
    val record: ObjectRecord,
    /** In carousel order. */
    val photos: List<PhotoRecord>,
)

data class PhotoRecord(
    val id: UUID,
    val objectId: UUID,
    val fileName: String,
    val thumbFileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val sortOrder: Int,
    val createdAt: Instant,
)

data class ObjectListQuery(
    val search: String? = null,
    val sort: SortFieldDto = SortFieldDto.UPDATED,
    val order: SortOrderDto = SortOrderDto.DESC,
)
