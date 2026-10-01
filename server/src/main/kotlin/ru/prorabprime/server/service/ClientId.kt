package ru.prorabprime.server.service

import java.util.UUID
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure

/**
 * The id a client chose for a record it is creating (offline, it cannot wait for the server to pick one).
 * Absent means the server picks; present but not a UUID is a rejected request.
 */
fun parseClientId(raw: String?): Result<UUID?> {
    if (raw.isNullOrBlank()) return Result.success(null)
    return runCatching { UUID.fromString(raw.trim()) }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { ServiceError.Validation("id is not a valid UUID").asFailure() },
    )
}

/** A retried create finds what it made; an id that belongs to a record of another owner is a conflict. */
fun <T> alreadyCreated(existing: T?, sameOwner: (T) -> Boolean): Result<T>? = when {
    existing == null -> null
    sameOwner(existing) -> Result.success(existing)
    else -> ServiceError.Conflict("This id is already used").asFailure()
}
