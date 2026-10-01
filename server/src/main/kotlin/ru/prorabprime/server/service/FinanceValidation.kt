package ru.prorabprime.server.service

import java.time.LocalDate
import java.time.format.DateTimeParseException
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.FieldErrorDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.FinanceLimits
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.ExtraWorkFields
import ru.prorabprime.server.model.FinanceTerms
import ru.prorabprime.server.model.PaymentFields

private fun invalid(field: ObjectFieldDto, problem: FieldProblemDto = FieldProblemDto.INVALID) =
    FieldErrorDto(field, problem)

private fun validAmount(kopecks: Long, allowZero: Boolean): Boolean =
    kopecks in (if (allowZero) 0L else 1L)..FinanceLimits.MAX_AMOUNT_KOPECKS

/** A payment is money that really moved: a positive amount on a real day. */
fun validatePayment(request: PaymentRequestDto): Result<PaymentFields> {
    val paidOn = try {
        LocalDate.parse(request.paidOn.trim())
    } catch (@Suppress("SwallowedException") e: DateTimeParseException) {
        null
    }
    val note = request.note.trimToNull()
    val errors = buildList {
        if (!validAmount(request.amountKopecks, allowZero = false)) add(invalid(ObjectFieldDto.PAYMENT_AMOUNT))
        if (paidOn == null) add(invalid(ObjectFieldDto.PAYMENT_DATE))
        if ((note?.length ?: 0) >
            FinanceLimits.NOTE
        ) {
            add(invalid(ObjectFieldDto.PAYMENT_NOTE, FieldProblemDto.TOO_LONG))
        }
    }
    return if (errors.isEmpty() && paidOn != null) {
        Result.success(PaymentFields(request.side, request.amountKopecks, request.method, paidOn, note))
    } else {
        ServiceError.Validation("Invalid payment fields", errors).asFailure()
    }
}

fun validateExtraWork(request: ExtraWorkRequestDto): Result<ExtraWorkFields> {
    val title = request.title.trim()
    val errors = buildList {
        if (title.isEmpty()) add(invalid(ObjectFieldDto.WORK_TITLE, FieldProblemDto.REQUIRED))
        if (title.length > FinanceLimits.WORK_TITLE) add(invalid(ObjectFieldDto.WORK_TITLE, FieldProblemDto.TOO_LONG))
        if (!validAmount(request.amountKopecks, allowZero = true)) add(invalid(ObjectFieldDto.WORK_AMOUNT))
    }
    return if (errors.isEmpty()) {
        Result.success(ExtraWorkFields(title, request.amountKopecks, request.status))
    } else {
        ServiceError.Validation("Invalid extra work fields", errors).asFailure()
    }
}

fun validateTerms(request: FinanceTermsDto): Result<FinanceTerms> {
    val amounts = listOfNotNull(request.clientTotalKopecks, request.crewTotalKopecks)
    return if (amounts.all { validAmount(it, allowZero = true) }) {
        Result.success(FinanceTerms(request.clientTotalKopecks, request.crewTotalKopecks))
    } else {
        ServiceError.Validation("Invalid totals", listOf(invalid(ObjectFieldDto.TOTAL_AMOUNT))).asFailure()
    }
}
