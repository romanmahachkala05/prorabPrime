package ru.prorabprime.server.service

import java.time.LocalDate
import java.util.UUID
import kotlin.time.Clock
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.PhotoLimits
import ru.prorabprime.contract.ReceiptRequestDto
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.ReceiptData
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository

/** The sum and time of a receipt, set by hand where the QR code could not be read or was read wrong. */
class ReceiptService(
    private val objects: ObjectRepository,
    private val photos: PhotoRepository,
    private val transactor: Transactor,
    private val clock: Clock,
) {
    /** No amount in the request clears what the receipt knows. */
    suspend fun set(
        owner: OwnerId,
        photoId: UUID,
        request: ReceiptRequestDto,
    ): Result<Unit> {
        val photo = photos.find(owner, photoId) ?: return ServiceError.NotFound("No photo $photoId").asFailure()
        if (photo.kind != AttachmentKindDto.RECEIPT) {
            return ServiceError.Validation("Photo $photoId is not a receipt").asFailure()
        }
        val receipt = receiptOf(request, photo.receipt?.qr.orEmpty()).getOrElse { return Result.failure(it) }
        transactor.inTransaction {
            photos.setReceipt(owner, photoId, receipt)
            objects.touch(owner, photo.objectId, clock.now())
        }
        return Result.success(Unit)
    }
}

private fun receiptOf(request: ReceiptRequestDto, qr: String): Result<ReceiptData?> {
    val amount = request.amountKopecks ?: return Result.success(null)
    val purchasedAt = request.purchasedAt
    return when {
        amount < 0 || amount > PhotoLimits.MAX_RECEIPT_KOPECKS ->
            ServiceError.Validation("The amount is out of range").asFailure()

        purchasedAt != null && !isTime(purchasedAt) ->
            ServiceError.Validation("purchasedAt must be yyyy-MM-dd or yyyy-MM-ddTHH:mm").asFailure()

        else -> Result.success(ReceiptData(amount, purchasedAt, qr))
    }
}

private val TIME = Regex("""^(\d{4}-\d{2}-\d{2})(T([01]\d|2[0-3]):[0-5]\d)?$""")

private fun isTime(text: String): Boolean {
    val day = TIME.matchEntire(text)?.groupValues?.get(1) ?: return false
    return runCatching { LocalDate.parse(day) }.isSuccess
}
