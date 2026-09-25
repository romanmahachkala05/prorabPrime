package ru.prorabprime.data.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import ru.prorabprime.contract.ErrorDto
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.PhotoRejection
import ru.prorabprime.domain.model.asFailure

/**
 * Runs one server call and classifies any failure as an [AppError] — the only code in the app
 * that knows what an HTTP status or an `IOException` means (CatsListKMP ADR-0032).
 * Cancellation is never classified: it means the caller went away (CatsListKMP ADR-0013).
 */
internal suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
    val error = failure.toAppError()
    dataLogWarning("Server call failed as $error", failure)
    error.asFailure()
}

internal suspend fun Throwable.toAppError(): AppError = when (this) {
    is ResponseException -> response.toAppError()

    // Connection refused, unknown host, timeouts, a bad server address.
    is IOException -> AppError.Network

    // Unparseable bodies and anything unforeseen.
    else -> AppError.Unknown
}

private suspend fun HttpResponse.toAppError(): AppError = when (status) {
    HttpStatusCode.Unauthorized -> AppError.Unauthorized
    HttpStatusCode.NotFound -> AppError.NotFound
    HttpStatusCode.BadRequest -> AppError.Validation(fieldErrors())
    HttpStatusCode.PayloadTooLarge -> AppError.PhotoRejected(PhotoRejection.TOO_LARGE)
    HttpStatusCode.UnsupportedMediaType -> AppError.PhotoRejected(PhotoRejection.UNSUPPORTED_TYPE)
    else -> AppError.Server(status.value)
}

/** A 400 without a readable body still counts as a validation error, just with no fields named. */
private suspend fun HttpResponse.fieldErrors() = runCatching { body<ErrorDto>() }
    .getOrNull()
    ?.fieldErrors
    .orEmpty()
    .associate { it.field.toDomain() to it.problem.toDomain() }
    .toImmutableMap()
