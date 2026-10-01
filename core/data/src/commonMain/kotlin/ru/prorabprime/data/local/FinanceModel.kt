package ru.prorabprime.data.local

import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.FinanceDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.SideSummaryDto

/**
 * The books of an object, worked out on the phone with the server's own rule: the client owes the contract
 * total plus the extras agreed on (nothing while neither exists); the crew is owed what was agreed for it.
 */
internal fun financeOf(
    terms: FinanceTermsDto,
    payments: List<PaymentRow>,
    extras: List<ExtraWorkRow>,
): FinanceDto {
    val sortedPayments = payments.sortedWith(compareBy({ it.dto.paidOn }, { it.order }))
    val sortedExtras = extras.sortedBy { it.order }
    val agreedExtras = sortedExtras.filter { it.dto.status == ExtraWorkStatusDto.AGREED }.sumOf { it.dto.amountKopecks }
    val pendingExtras = sortedExtras.filter { it.dto.status == ExtraWorkStatusDto.NOT_AGREED }
        .sumOf { it.dto.amountKopecks }
    val clientAgreed = if (terms.clientTotalKopecks == null && agreedExtras == 0L) {
        null
    } else {
        (terms.clientTotalKopecks ?: 0L) + agreedExtras
    }
    return FinanceDto(
        terms = terms,
        client = summary(sortedPayments, PaymentSideDto.CLIENT, clientAgreed),
        crew = summary(sortedPayments, PaymentSideDto.CREW, terms.crewTotalKopecks),
        extrasAgreedKopecks = agreedExtras,
        extrasPendingKopecks = pendingExtras,
        payments = sortedPayments.map { it.dto },
        extraWorks = sortedExtras.map { it.dto },
    )
}

private fun summary(
    payments: List<PaymentRow>,
    side: PaymentSideDto,
    agreed: Long?,
): SideSummaryDto {
    val paid = payments.filter { it.dto.side == side }.sumOf { it.dto.amountKopecks }
    return SideSummaryDto(agreedKopecks = agreed, paidKopecks = paid, remainingKopecks = agreed?.minus(paid))
}
