package ru.prorabprime.server.service

import ru.prorabprime.contract.ContactLimits
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.contract.FieldErrorDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.ContactFields

/** Trims the request, turns a blank phone into null, and checks it against the columns. */
fun validateContact(request: ContactRequestDto): Result<ContactFields> {
    val fields = ContactFields(
        name = request.name.trim(),
        phone = request.phone.trimToNull(),
        role = request.role,
    )
    val errors = buildList {
        if (fields.name.isEmpty()) add(FieldErrorDto(ObjectFieldDto.CONTACT_NAME, FieldProblemDto.REQUIRED))
        if (fields.name.length > ContactLimits.NAME) {
            add(FieldErrorDto(ObjectFieldDto.CONTACT_NAME, FieldProblemDto.TOO_LONG))
        }
        if ((fields.phone?.length ?: 0) > ContactLimits.PHONE) {
            add(FieldErrorDto(ObjectFieldDto.CONTACT_PHONE, FieldProblemDto.TOO_LONG))
        }
    }
    return if (errors.isEmpty()) {
        Result.success(fields)
    } else {
        ServiceError.Validation("Invalid contact fields", errors).asFailure()
    }
}
