package ru.prorabprime.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import ru.prorabprime.domain.model.Expenses
import ru.prorabprime.domain.repository.ExpensesRepository

/** In-memory [ExpensesRepository]: says nothing until [result] is set, then emits it as set. */
class FakeExpensesRepository : ExpensesRepository {

    val result = MutableStateFlow<Result<Expenses>?>(null)

    override fun observeExpenses(): Flow<Result<Expenses>> = result.filterNotNull()
}
