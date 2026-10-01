package ru.prorabprime.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.Expenses
import ru.prorabprime.domain.repository.ExpensesRepository

class ObserveExpensesUseCase(
    private val repository: ExpensesRepository,
) {
    operator fun invoke(): Flow<Result<Expenses>> = repository.observeExpenses()
}
