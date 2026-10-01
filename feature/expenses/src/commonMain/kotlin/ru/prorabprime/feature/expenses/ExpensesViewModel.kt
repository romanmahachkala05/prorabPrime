package ru.prorabprime.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.Expenses
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.toUiText

/**
 * Small enough to own its state directly: it reads two flows and keeps the filter and the form, so a
 * separate StateHolder would only forward calls.
 */
internal class ExpensesViewModel(
    actions: ExpensesActions,
    notifier: SnackbarNotifier,
    private val today: () -> LocalDay,
) : ViewModel(),
    StateOwner<ExpensesState> {

    private val _state = MutableStateFlow(ExpensesState())
    override val state: StateFlow<ExpensesState> = _state.asStateFlow()

    private val form = ExpenseFormHandler(viewModelScope, _state, actions, notifier, today)

    /** What was last read, so a new filter can be applied without reading again. */
    private var expenses: Expenses? = null

    init {
        actions.observeExpenses()
            .onEach { result ->
                result
                    .onSuccess {
                        expenses = it
                        _state.update { old -> report(it, old.filter, old) }
                    }.onFailure { failure ->
                        _state.update { it.copy(status = ExpensesStatus.Error(failure.asAppError().toUiText())) }
                    }
            }.launchIn(viewModelScope)
        actions.observeObjects(ObjectQuery())
            .onEach { result ->
                result.onSuccess { objects ->
                    val choices = objects.map { ObjectChoiceUi(it.id.value, it.title ?: it.address) }
                    _state.update { it.copy(objects = choices.toImmutableList()) }
                }
            }.launchIn(viewModelScope)
    }

    fun onEvent(event: ExpensesEvent) {
        when (event) {
            is ExpensesEvent.FilterSelected -> selectFilter(event.filter)
            ExpensesEvent.AddClicked -> form.open()
            is ExpenseFormEvent -> form.onEvent(event)
        }
    }

    private fun selectFilter(filter: ExpenseFilter) {
        val read = expenses ?: return _state.update { it.copy(filter = filter) }
        _state.update { report(read, filter, it) }
    }

    /** The report for [filter], with what the report does not own — the objects and the form — kept from [old]. */
    private fun report(
        read: Expenses,
        filter: ExpenseFilter,
        old: ExpensesState,
    ) = read.toState(today(), filter).copy(objects = old.objects, form = old.form)
}
