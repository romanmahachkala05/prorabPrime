package ru.prorabprime.data.repository

import ru.prorabprime.contract.AccountDto
import ru.prorabprime.data.remote.RemoteApi
import ru.prorabprime.domain.model.Account
import ru.prorabprime.domain.repository.AccountRepository

internal class AccountRepositoryImpl(
    private val api: RemoteApi,
) : AccountRepository {
    override suspend fun account(): Result<Account> = api.account().map { it.toDomain() }
}

internal fun AccountDto.toDomain() = Account(name, usedBytes, limitBytes)
