package ru.prorabprime.domain.repository

import ru.prorabprime.domain.model.Account

/** The account of the saved token. Not copied to the phone: it needs the server, and fails without one. */
interface AccountRepository {
    suspend fun account(): Result<Account>
}
