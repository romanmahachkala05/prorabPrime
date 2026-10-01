package ru.prorabprime.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.Expenses

/** What was spent, over every object: read from the phone's copy, so it works without a signal too. */
interface ExpensesRepository {
    fun observeExpenses(): Flow<Result<Expenses>>
}
