package ru.prorabprime.server.routes

import ru.prorabprime.contract.ExtraWorkDto
import ru.prorabprime.contract.FinanceDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.PaymentDto
import ru.prorabprime.contract.PaymentRevisionDto
import ru.prorabprime.contract.SideSummaryDto
import ru.prorabprime.server.model.ExtraWorkRecord
import ru.prorabprime.server.model.FinanceOverview
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.PaymentRevisionRecord
import ru.prorabprime.server.model.SideSummary

fun FinanceOverview.toDto() = FinanceDto(
    terms = FinanceTermsDto(terms.clientTotalKopecks, terms.crewTotalKopecks),
    client = client.toDto(),
    crew = crew.toDto(),
    extrasAgreedKopecks = extrasAgreedKopecks,
    extrasPendingKopecks = extrasPendingKopecks,
    payments = payments.map { it.toDto() },
    extraWorks = extraWorks.map { it.toDto() },
)

private fun SideSummary.toDto() = SideSummaryDto(agreedKopecks, paidKopecks, remainingKopecks)

fun PaymentRecord.toDto() = PaymentDto(
    id = id.toString(),
    side = fields.side,
    amountKopecks = fields.amountKopecks,
    method = fields.method,
    paidOn = fields.paidOn.toString(),
    note = fields.note,
)

fun PaymentRevisionRecord.toDto() = PaymentRevisionDto(
    id = id.toString(),
    paymentId = paymentId.toString(),
    action = action,
    side = fields.side,
    amountKopecks = fields.amountKopecks,
    method = fields.method,
    paidOn = fields.paidOn.toString(),
    note = fields.note,
    at = at,
)

fun ExtraWorkRecord.toDto() = ExtraWorkDto(
    id = id.toString(),
    title = fields.title,
    amountKopecks = fields.amountKopecks,
    status = fields.status,
)
