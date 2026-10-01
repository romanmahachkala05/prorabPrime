package ru.prorabprime.data.local

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ContactDto
import ru.prorabprime.contract.ExtraWorkDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.MaterialDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PaymentDto
import ru.prorabprime.contract.PaymentRevisionDto
import ru.prorabprime.contract.TaskDto

/*
 * What the phone keeps, one row per record. They mirror the wire DTOs so that what the server sends
 * can be stored as it is and shown through the same mappers. `order` keeps a child where the server
 * listed it, and puts a record made on the phone at the end.
 */

@Serializable
internal data class ObjectRow(
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
    val createdAt: Instant,
    val updatedAt: Instant,
    /** The server's `updatedAt` when this row was last pulled; null for an object only the phone knows. */
    val serverUpdatedAt: Instant? = null,
)

@Serializable
internal data class ContactRow(
    val objectId: String,
    val order: Long,
    val dto: ContactDto,
)

/** A picture: the server's record of it, or, until it is sent, the phone's own [localBlob]. */
@Serializable
internal data class PhotoRow(
    val objectId: String,
    val order: Long,
    val id: String,
    val kind: AttachmentKindDto,
    val width: Int,
    val height: Int,
    val createdAt: Instant,
    val url: String? = null,
    val thumbUrl: String? = null,
    val localBlob: String? = null,
    /** Clockwise quarter turns asked for on the phone and not yet made by the server (0 to 3). */
    val quarterTurns: Int = 0,
    val note: String? = null,
    /** From the QR code on a receipt, read by the server; null for a photo and for a receipt it could not read. */
    val receiptAmountKopecks: Long? = null,
    val receiptAt: String? = null,
)

@Serializable
internal data class PaymentRow(
    val objectId: String,
    val order: Long,
    val dto: PaymentDto,
)

@Serializable
internal data class ExtraWorkRow(
    val objectId: String,
    val order: Long,
    val dto: ExtraWorkDto,
)

@Serializable
internal data class MaterialRow(
    val objectId: String,
    val order: Long,
    val dto: MaterialDto,
)

@Serializable
internal data class TermsRow(
    val objectId: String,
    val dto: FinanceTermsDto,
)

/** The server's history of an object's payments; read-only on the phone. */
@Serializable
internal data class HistoryRow(
    val objectId: String,
    val revisions: List<PaymentRevisionDto>,
)

@Serializable
internal data class TaskRow(
    val dto: TaskDto,
    val order: Long,
)

/** Facts about the copy itself. One row, key `meta`. */
@Serializable
internal data class MetaRow(
    val key: String = META_KEY,
    /** The server this copy was pulled from; another address means another copy. */
    val serverKey: String? = null,
    val lastSyncAt: Instant? = null,
    /** Whether a first sync has been tried at all, so an empty list can tell "nothing yet" from "no data". */
    val syncTried: Boolean = false,
)

internal const val META_KEY = "meta"
