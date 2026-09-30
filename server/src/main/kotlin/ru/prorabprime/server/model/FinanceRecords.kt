package ru.prorabprime.server.model

import java.time.LocalDate
import java.util.UUID
import kotlin.time.Instant
import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.RevisionActionDto

/** The editable fields of a payment, already trimmed and validated. */
data class PaymentFields(
    val side: PaymentSideDto,
    val amountKopecks: Long,
    val method: PaymentMethodDto,
    val paidOn: LocalDate,
    val note: String?,
)

data class PaymentRecord(
    val id: UUID,
    val objectId: UUID,
    val fields: PaymentFields,
    val createdAt: Instant,
)

data class PaymentRevisionRecord(
    val id: UUID,
    val objectId: UUID,
    val paymentId: UUID,
    val action: RevisionActionDto,
    val fields: PaymentFields,
    val at: Instant,
)

data class ExtraWorkFields(
    val title: String,
    val amountKopecks: Long,
    val status: ExtraWorkStatusDto,
)

data class ExtraWorkRecord(
    val id: UUID,
    val objectId: UUID,
    val fields: ExtraWorkFields,
    val createdAt: Instant,
)

data class FinanceTerms(
    val clientTotalKopecks: Long? = null,
    val crewTotalKopecks: Long? = null,
)

/** One side of the books; null amounts while nothing is agreed on that side. */
data class SideSummary(
    val agreedKopecks: Long?,
    val paidKopecks: Long,
    val remainingKopecks: Long?,
)

data class FinanceOverview(
    val terms: FinanceTerms,
    val payments: List<PaymentRecord>,
    val extraWorks: List<ExtraWorkRecord>,
) {
    val extrasAgreedKopecks: Long
        get() = extraWorks.filter { it.fields.status == ExtraWorkStatusDto.AGREED }.sumOf { it.fields.amountKopecks }

    val extrasPendingKopecks: Long
        get() = extraWorks.filter {
            it.fields.status == ExtraWorkStatusDto.NOT_AGREED
        }.sumOf { it.fields.amountKopecks }

    /** The client owes the contract total plus the extras agreed on; nothing is owed while neither exists. */
    val client: SideSummary
        get() {
            val agreed = if (terms.clientTotalKopecks == null && extrasAgreedKopecks == 0L) {
                null
            } else {
                (terms.clientTotalKopecks ?: 0L) + extrasAgreedKopecks
            }
            return summary(PaymentSideDto.CLIENT, agreed)
        }

    val crew: SideSummary get() = summary(PaymentSideDto.CREW, terms.crewTotalKopecks)

    private fun summary(side: PaymentSideDto, agreed: Long?): SideSummary {
        val paid = payments.filter { it.fields.side == side }.sumOf { it.fields.amountKopecks }
        return SideSummary(agreedKopecks = agreed, paidKopecks = paid, remainingKopecks = agreed?.minus(paid))
    }
}
