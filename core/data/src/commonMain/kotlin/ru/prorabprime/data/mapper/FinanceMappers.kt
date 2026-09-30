package ru.prorabprime.data.mapper

import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.contract.ExtraWorkDto
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.FinanceDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.PaymentDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.contract.PaymentRevisionDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.RevisionActionDto
import ru.prorabprime.contract.SideSummaryDto
import ru.prorabprime.domain.model.ExtraWork
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.Finance
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Payment
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.domain.model.PaymentRevision
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.domain.model.RevisionAction
import ru.prorabprime.domain.model.SideSummary

internal fun FinanceDto.toDomain() = Finance(
    clientTotalKopecks = terms.clientTotalKopecks,
    crewTotalKopecks = terms.crewTotalKopecks,
    client = client.toDomain(),
    crew = crew.toDomain(),
    extrasAgreedKopecks = extrasAgreedKopecks,
    extrasPendingKopecks = extrasPendingKopecks,
    payments = payments.map { it.toDomain() }.toImmutableList(),
    extraWorks = extraWorks.map { it.toDomain() }.toImmutableList(),
)

private fun SideSummaryDto.toDomain() = SideSummary(agreedKopecks, paidKopecks, remainingKopecks)

internal fun PaymentDto.toDomain() = Payment(
    id = PaymentId(id),
    side = side.toDomain(),
    amountKopecks = amountKopecks,
    method = method.toDomain(),
    // The server sends what it stored; a day it cannot have written reads as the epoch.
    paidOn = LocalDay.parseIso(paidOn) ?: LocalDay(0),
    note = note,
)

internal fun ExtraWorkDto.toDomain() = ExtraWork(ExtraWorkId(id), title, amountKopecks, status.toDomain())

internal fun PaymentRevisionDto.toDomain() = PaymentRevision(
    id = id,
    paymentId = PaymentId(paymentId),
    action = action.toDomain(),
    side = side.toDomain(),
    amountKopecks = amountKopecks,
    method = method.toDomain(),
    paidOn = LocalDay.parseIso(paidOn) ?: LocalDay(0),
    note = note,
    at = at,
)

/** The draft has been validated by now, so a missing amount or day cannot reach here. */
internal fun PaymentDraft.toRequestDto() = PaymentRequestDto(
    side = side.toDto(),
    amountKopecks = requireNotNull(amountKopecks),
    method = method.toDto(),
    paidOn = requireNotNull(paidOn).toIso(),
    note = note,
)

internal fun ExtraWorkDraft.toRequestDto() = ExtraWorkRequestDto(
    title = title,
    amountKopecks = requireNotNull(amountKopecks),
    status = status.toDto(),
)

internal fun FinanceTermsDraft.toDto() = FinanceTermsDto(clientTotalKopecks, crewTotalKopecks)
