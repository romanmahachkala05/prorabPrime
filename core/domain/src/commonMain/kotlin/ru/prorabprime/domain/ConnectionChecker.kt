package ru.prorabprime.domain

import ru.prorabprime.domain.model.Account
import ru.prorabprime.domain.model.ServerSettings

/**
 * Checks settings before they are saved: that the server answers at all, and that the token is
 * accepted. Fails with `AppError.Network` or `AppError.Unauthorized` respectively. On success, the account
 * the token belongs to, or null from a server that does not say (an older one).
 */
interface ConnectionChecker {
    suspend fun check(settings: ServerSettings): Result<Account?>
}
