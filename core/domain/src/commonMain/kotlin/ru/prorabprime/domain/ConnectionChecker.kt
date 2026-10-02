package ru.prorabprime.domain

import ru.prorabprime.domain.model.ServerSettings

/**
 * Checks settings before they are saved: that the server answers at all, and that the token is
 * accepted. Fails with `AppError.Network` or `AppError.Unauthorized` respectively.
 */
interface ConnectionChecker {
    suspend fun check(settings: ServerSettings): Result<Unit>
}
