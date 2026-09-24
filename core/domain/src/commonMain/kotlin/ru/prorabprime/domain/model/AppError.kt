package ru.prorabprime.domain.model

import kotlinx.collections.immutable.ImmutableMap

/**
 * What went wrong, in terms the UI can act on. Every failure crossing out of `data` is one of
 * these, classified once by `:core:data`'s error mapper (CatsListKMP ADR-0032). The original
 * `Throwable` is not carried: it is logged where the mapping happens, and leaving it out lets
 * errors compare by value in tests.
 */
sealed interface AppError {

    /** No connection, a timeout, or a host that did not answer. */
    data object Network : AppError

    /** HTTP 401: the token is missing or wrong. */
    data object Unauthorized : AppError

    /** HTTP 404: the object or photo no longer exists. */
    data object NotFound : AppError

    /** The request was rejected; [fieldErrors] says which form fields, and may be empty. */
    data class Validation(
        val fieldErrors: ImmutableMap<ObjectField, FieldProblem>,
    ) : AppError

    /** The server refused an upload (HTTP 415 or 413). */
    data class PhotoRejected(
        val reason: PhotoRejection,
    ) : AppError

    /** HTTP 5xx, or any other status nothing above covers. */
    data class Server(
        val code: Int,
    ) : AppError

    /** A response that could not be read, or a failure classified as nothing else. */
    data object Unknown : AppError
}

enum class PhotoRejection {
    UNSUPPORTED_TYPE,
    TOO_LARGE,
}

/**
 * Carries an [AppError] through `Result`, which insists on a `Throwable`. `data` fails with
 * this and nothing else; callers unwrap it with [asAppError].
 */
class AppErrorException(
    val error: AppError,
) : Exception(error.toString())

fun Throwable.asAppError(): AppError = (this as? AppErrorException)?.error ?: AppError.Unknown

fun <T> AppError.asFailure(): Result<T> = Result.failure(AppErrorException(this))
