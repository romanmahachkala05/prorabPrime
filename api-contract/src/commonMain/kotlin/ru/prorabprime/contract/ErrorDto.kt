package ru.prorabprime.contract

import kotlinx.serialization.Serializable

/** The body of every non-2xx response. [message] is for logs, never shown to the user. */
@Serializable
data class ErrorDto(
    val code: ErrorCode,
    val message: String,
    val fieldErrors: List<FieldErrorDto> = emptyList(),
)

@Serializable
enum class ErrorCode {
    UNAUTHORIZED,
    NOT_FOUND,
    VALIDATION,
    UNSUPPORTED_MEDIA,
    PAYLOAD_TOO_LARGE,
    QUOTA_EXCEEDED,
    CONFLICT,
    INTERNAL,
}

/** One rejected field of an [ObjectRequestDto], so the form can mark it. */
@Serializable
data class FieldErrorDto(
    val field: ObjectFieldDto,
    val problem: FieldProblemDto,
)

@Serializable
enum class ObjectFieldDto {
    TITLE,
    ADDRESS,
    CLIENT_NAME,
    CLIENT_PHONE,
    NOTES,
    CONTACT_NAME,
    CONTACT_PHONE,
    CHAT_LINK,
    PAYMENT_AMOUNT,
    PAYMENT_DATE,
    PAYMENT_NOTE,
    WORK_TITLE,
    WORK_AMOUNT,
    TOTAL_AMOUNT,
    MATERIAL_TITLE,
    TASK_TITLE,
    TASK_DAY,
    TASK_TIME,
}

@Serializable
enum class FieldProblemDto {
    REQUIRED,
    TOO_LONG,
    INVALID,
}
