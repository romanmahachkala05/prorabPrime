package ru.prorabprime.feature.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.prorabprime.domain.model.Finance
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.ui.StateOwner

internal class FinanceViewModel(
    private val objectId: ObjectId,
    private val stateHolder: IFinanceStateHolder,
    private val errorHandler: IFinanceErrorHandler,
    private val actions: FinanceActions,
    today: () -> LocalDay = ::currentDay,
) : ViewModel(),
    StateOwner<FinanceState> by stateHolder {

    private val payments = PaymentEditorHandler(
        objectId,
        viewModelScope,
        stateHolder,
        errorHandler,
        actions.savePayment,
        actions.deletePayment,
        today,
    )
    private val works = WorkEditorHandler(
        objectId,
        viewModelScope,
        stateHolder,
        errorHandler,
        actions.saveExtraWork,
        actions.deleteExtraWork,
    )
    private val terms = TermsEditorHandler(objectId, viewModelScope, stateHolder, errorHandler, actions.saveTerms)

    private var historyJob: Job? = null
    private var financeJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: FinanceEvent) {
        when (event) {
            FinanceEvent.Retry -> retry()
            FinanceEvent.ShowHistory -> showHistory()
            FinanceEvent.HideHistory -> hideHistory()
            FinanceEvent.DialogConfirmed -> runPendingAction()
            FinanceEvent.DialogDismissed -> stateHolder.dismissDialog()
            is PaymentEvent -> payments.onEvent(event)
            is WorkEvent -> works.onEvent(event)
            is TermsEvent -> terms.onEvent(event)
        }
    }

    /** The repository reloads this after any write, so the screen never asks for a refresh. */
    private fun load() {
        financeJob?.cancel()
        financeJob = actions.observeFinance(objectId)
            .onEach(::render)
            .launchIn(viewModelScope)
    }

    private fun render(result: Result<Finance>) {
        result
            .onSuccess { stateHolder.showFinance(it.toUi()) }
            .onFailure { failure -> viewModelScope.launch { errorHandler.onLoadFailure(failure.asAppError()) } }
    }

    private fun retry() {
        stateHolder.showLoading()
        load()
    }

    /** The history is watched only while its sheet is open, and a failure to load it is not shown. */
    private fun showHistory() {
        historyJob?.cancel()
        stateHolder.setHistory(persistentListOf())
        historyJob = actions.observeHistory(objectId)
            .onEach { result -> result.onSuccess { stateHolder.setHistory(it.toRevisionsUi()) } }
            .launchIn(viewModelScope)
    }

    private fun hideHistory() {
        historyJob?.cancel()
        stateHolder.setHistory(null)
    }

    private fun runPendingAction() {
        val action = state.value.pendingAction
        stateHolder.dismissDialog()
        when (action) {
            is FinanceAction.DeletePayment -> payments.delete(action.paymentId)
            is FinanceAction.DeleteWork -> works.delete(action.workId)
            null -> Unit
        }
    }
}

/** What a new payment's day starts as. */
private fun currentDay(): LocalDay = LocalDay.ofInstant(Clock.System.now())
