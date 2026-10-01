package ru.prorabprime.contract

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** Money is whole kopecks everywhere on the wire, so no rounding can creep in. */
@Serializable
enum class PaymentSideDto {
    /** Money the client paid us. */
    CLIENT,

    /** Money we paid the crew. */
    CREW,
}

@Serializable
enum class PaymentMethodDto {
    CASH,
    TRANSFER,
    CARD,
    OTHER,
}

@Serializable
enum class ExtraWorkStatusDto {
    AGREED,
    NOT_AGREED,
}

/** [paidOn] is the day the money changed hands, as `yyyy-MM-dd`. */
@Serializable
data class PaymentDto(
    val id: String,
    val side: PaymentSideDto,
    val amountKopecks: Long,
    val method: PaymentMethodDto,
    val paidOn: String,
    val note: String? = null,
)

/** Body of `POST /api/objects/{id}/payments` and `PUT /api/payments/{id}`. */
@Serializable
data class PaymentRequestDto(
    val side: PaymentSideDto,
    val amountKopecks: Long,
    val method: PaymentMethodDto,
    val paidOn: String,
    val note: String? = null,
    /** Chosen by the client on create (a UUID), so a retry of the same create finds the record it made. */
    val id: String? = null,
)

@Serializable
data class ExtraWorkDto(
    val id: String,
    val title: String,
    val amountKopecks: Long,
    val status: ExtraWorkStatusDto,
)

/** Body of `POST /api/objects/{id}/extra-works` and `PUT /api/extra-works/{id}`. */
@Serializable
data class ExtraWorkRequestDto(
    val title: String,
    val amountKopecks: Long,
    val status: ExtraWorkStatusDto = ExtraWorkStatusDto.NOT_AGREED,
    /** Chosen by the client on create (a UUID), so a retry of the same create finds the record it made. */
    val id: String? = null,
)

/** What was agreed for the whole object; either side may be unset. Body of `PUT .../finance/terms`. */
@Serializable
data class FinanceTermsDto(
    val clientTotalKopecks: Long? = null,
    val crewTotalKopecks: Long? = null,
)

/** One side of the books. The amounts are null while nothing is agreed on that side. */
@Serializable
data class SideSummaryDto(
    val agreedKopecks: Long? = null,
    val paidKopecks: Long,
    val remainingKopecks: Long? = null,
)

@Serializable
data class FinanceDto(
    val terms: FinanceTermsDto,
    val client: SideSummaryDto,
    val crew: SideSummaryDto,
    val extrasAgreedKopecks: Long,
    val extrasPendingKopecks: Long,
    val payments: List<PaymentDto>,
    val extraWorks: List<ExtraWorkDto>,
)

@Serializable
enum class RevisionActionDto {
    CREATED,
    UPDATED,
    DELETED,
}

/** One step in the life of a payment: what it looked like after the change (or before, when deleted). */
@Serializable
data class PaymentRevisionDto(
    val id: String,
    val paymentId: String,
    val action: RevisionActionDto,
    val side: PaymentSideDto,
    val amountKopecks: Long,
    val method: PaymentMethodDto,
    val paidOn: String,
    val note: String? = null,
    val at: Instant,
)

@Serializable
data class IdDto(
    val id: String,
)

object FinanceLimits {
    const val MAX_AMOUNT_KOPECKS = 100_000_000_000L
    const val NOTE = 500
    const val WORK_TITLE = 200
}
