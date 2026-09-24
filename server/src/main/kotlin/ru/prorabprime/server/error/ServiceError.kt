package ru.prorabprime.server.error

import ru.prorabprime.contract.FieldErrorDto

/** A failure a service reports; StatusPages turns it into an HTTP status and an `ErrorDto`. */
sealed interface ServiceError {
    val message: String

    data class NotFound(
        override val message: String,
    ) : ServiceError

    data class Validation(
        override val message: String,
        val fieldErrors: List<FieldErrorDto> = emptyList(),
    ) : ServiceError

    data class UnsupportedMedia(
        override val message: String,
    ) : ServiceError

    data class TooLarge(
        override val message: String,
    ) : ServiceError
}

/**
 * Carries a [ServiceError] through `Result`. A route unwraps a service result with
 * `getOrThrow()`, and StatusPages — the one place that maps errors — catches this.
 */
class ServiceException(
    val error: ServiceError,
) : RuntimeException(error.message)

fun <T> ServiceError.asFailure(): Result<T> = Result.failure(ServiceException(this))
