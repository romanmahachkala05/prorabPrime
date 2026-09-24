package ru.prorabprime.server.service

import ru.prorabprime.contract.FieldErrorDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.ObjectLimits
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.ObjectFields

/** Trims the request, turns blank optional fields into null, and checks it against the columns. */
fun validateObject(request: ObjectRequestDto): Result<ObjectFields> {
    val fields = ObjectFields(
        title = request.title.trimToNull(),
        address = request.address.trim(),
        status = request.status,
        clientName = request.clientName.trimToNull(),
        clientPhone = request.clientPhone.trimToNull(),
        notes = request.notes.trimToNull(),
    )
    val errors = buildList {
        if (fields.address.isEmpty()) add(FieldErrorDto(ObjectFieldDto.ADDRESS, FieldProblemDto.REQUIRED))
        tooLong(fields.address, ObjectLimits.ADDRESS, ObjectFieldDto.ADDRESS)?.let(::add)
        tooLong(fields.title, ObjectLimits.TITLE, ObjectFieldDto.TITLE)?.let(::add)
        tooLong(fields.clientName, ObjectLimits.CLIENT_NAME, ObjectFieldDto.CLIENT_NAME)?.let(::add)
        tooLong(fields.clientPhone, ObjectLimits.CLIENT_PHONE, ObjectFieldDto.CLIENT_PHONE)?.let(::add)
    }
    return if (errors.isEmpty()) {
        Result.success(fields)
    } else {
        ServiceError.Validation("Invalid object fields", errors).asFailure()
    }
}

private fun tooLong(
    value: String?,
    limit: Int,
    field: ObjectFieldDto,
): FieldErrorDto? = if ((value?.length ?: 0) > limit) FieldErrorDto(field, FieldProblemDto.TOO_LONG) else null

private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
