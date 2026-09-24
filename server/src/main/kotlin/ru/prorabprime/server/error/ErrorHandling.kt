package ru.prorabprime.server.error

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import ru.prorabprime.contract.ErrorCode
import ru.prorabprime.contract.ErrorDto

/** The one place failures become HTTP responses (ARCHITECTURE.md §11). */
fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<ServiceException> { call, cause ->
            val (status, body) = cause.error.toResponse()
            call.respond(status, body)
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorDto(ErrorCode.VALIDATION, "Malformed request"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error on ${call.request.local.uri}", cause)
            // The cause stays in the log: its message may describe internals.
            call.respond(HttpStatusCode.InternalServerError, ErrorDto(ErrorCode.INTERNAL, "Internal error"))
        }
        // The bearer provider's challenge has no body of its own.
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(status, ErrorDto(ErrorCode.UNAUTHORIZED, "Missing or invalid token"))
        }
        unhandled { call ->
            call.respond(HttpStatusCode.NotFound, ErrorDto(ErrorCode.NOT_FOUND, "No such route"))
        }
    }
}

internal fun ServiceError.toResponse(): Pair<HttpStatusCode, ErrorDto> = when (this) {
    is ServiceError.NotFound -> HttpStatusCode.NotFound to ErrorDto(ErrorCode.NOT_FOUND, message)

    is ServiceError.Validation -> HttpStatusCode.BadRequest to ErrorDto(ErrorCode.VALIDATION, message, fieldErrors)

    is ServiceError.UnsupportedMedia ->
        HttpStatusCode.UnsupportedMediaType to ErrorDto(ErrorCode.UNSUPPORTED_MEDIA, message)

    is ServiceError.TooLarge -> HttpStatusCode.PayloadTooLarge to ErrorDto(ErrorCode.PAYLOAD_TOO_LARGE, message)
}
