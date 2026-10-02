package ru.prorabprime.server.model

import java.util.UUID
import kotlin.time.Instant
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ContactRoleDto
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
    val chatLink: String? = null,
)

/** A point on the map. */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

data class ObjectRecord(
    val id: UUID,
    val ownerId: OwnerId,
    val fields: ObjectFields,
    val coverPhotoId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val coordinates: Coordinates? = null,
)

/** A row of the objects list, with what the list shows about the object's photos. */
data class ObjectListItem(
    val record: ObjectRecord,
    val coverThumbFileName: String?,
    val photoCount: Int,
)

/** An object in the trash, with what the trash shows about it. */
data class TrashedObject(
    val record: ObjectRecord,
    val coverThumbFileName: String?,
    val photoCount: Int,
    val deletedAt: Instant,
)

/** A photo in the trash whose object is not: [objectTitle] says where it came from. */
data class TrashedPhoto(
    val photo: PhotoRecord,
    val objectTitle: String?,
    val objectAddress: String,
    val deletedAt: Instant,
)

data class ObjectDetails(
    val record: ObjectRecord,
    val contacts: List<ContactRecord>,
    /** In carousel order. */
    val photos: List<PhotoRecord>,
)

/** The editable fields of a contact, already trimmed and validated. */
data class ContactFields(
    val name: String,
    val phone: String?,
    val role: ContactRoleDto,
)

data class ContactRecord(
    val id: UUID,
    val objectId: UUID,
    val fields: ContactFields,
    val sortOrder: Int,
    val createdAt: Instant,
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
    val kind: AttachmentKindDto = AttachmentKindDto.PHOTO,
    val note: String? = null,
    val receipt: ReceiptData? = null,
)

/** What a receipt's fiscal QR code says. */
data class ReceiptData(
    val amountKopecks: Long,
    /** Local time at the shop, `2026-10-01T15:26`; null when the code has none. */
    val purchasedAt: String?,
    /** The code's text as read. */
    val qr: String,
)

data class ObjectListQuery(
    val search: String? = null,
    val sort: SortFieldDto = SortFieldDto.UPDATED,
    val order: SortOrderDto = SortOrderDto.DESC,
)
