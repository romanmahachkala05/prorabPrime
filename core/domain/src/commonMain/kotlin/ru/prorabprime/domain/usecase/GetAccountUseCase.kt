package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.Account
import ru.prorabprime.domain.repository.AccountRepository

class GetAccountUseCase(
    private val repository: AccountRepository,
) {
    suspend operator fun invoke(): Result<Account> = repository.account()
}
