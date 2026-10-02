package ru.prorabprime.testing

import ru.prorabprime.domain.model.Account
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.AccountRepository

/** In-memory [AccountRepository]. */
class FakeAccountRepository : AccountRepository {

    var account = Account("owner", usedBytes = 0, limitBytes = null)

    /** When set, the call fails with it. */
    var error: AppError? = null

    override suspend fun account(): Result<Account> = error?.asFailure() ?: Result.success(account)
}
