package ru.prorabprime.testing

import ru.prorabprime.domain.ConnectionChecker
import ru.prorabprime.domain.model.Account
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.model.asFailure

class FakeConnectionChecker : ConnectionChecker {

    var error: AppError? = null

    /** What a successful check says about the account; null is a server that does not say. */
    var account: Account? = null

    val checked = mutableListOf<ServerSettings>()

    override suspend fun check(settings: ServerSettings): Result<Account?> {
        checked += settings
        return error?.asFailure() ?: Result.success(account)
    }
}
