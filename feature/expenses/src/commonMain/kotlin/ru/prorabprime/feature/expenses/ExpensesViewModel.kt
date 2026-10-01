package ru.prorabprime.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.Expenses
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.ObserveExpensesUseCase
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.toUiText

/**
 * Small enough to own its state directly: it reads one flow and keeps one choice, the filter, so a
 * separate StateHolder would only forward calls.
 */
internal class ExpensesViewModel(
    observeExpenses: ObserveExpensesUseCase,
    private val today: () -> LocalDay,
) : ViewModel(),
    StateOwner<ExpensesState> {

    private val _state = MutableStateFlow(ExpensesState())
    override val state: StateFlow<ExpensesState> = _state.asStateFlow()

    /** What was last read, so a new filter can be applied without reading again. */
    private var expenses: Expenses? = null

    init {
        observeExpenses()
            .onEach { result ->
                result
                    .onSuccess {
                        expenses = it
                        _state.update { old -> it.toState(today(), old.filter) }
                    }.onFailure { failure ->
                        _state.update { it.copy(status = ExpensesStatus.Error(failure.asAppError().toUiText())) }
                    }
            }.launchIn(viewModelScope)
    }

    fun selectFilter(filter: ExpenseFilter) {
        expenses?.let { _state.value = it.toState(today(), filter) } ?: _state.update { it.copy(filter = filter) }
    }
}
