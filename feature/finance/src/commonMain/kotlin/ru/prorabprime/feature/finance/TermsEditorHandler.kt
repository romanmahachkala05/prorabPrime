package ru.prorabprime.feature.finance

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.Money
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.SaveFinanceTermsUseCase
import ru.prorabprime.ui.launchCatching

/** The form for what was agreed with the client and with the crew. */
internal class TermsEditorHandler(
    private val objectId: ObjectId,
    private val scope: CoroutineScope,
    private val stateHolder: IFinanceStateHolder,
    private val errorHandler: IFinanceErrorHandler,
    private val saveTerms: SaveFinanceTermsUseCase,
) {
    fun onEvent(event: TermsEvent) {
        when (event) {
            TermsEvent.Open -> open()
            is TermsEvent.ClientChanged -> edit { it.copy(clientText = event.text, errors = persistentMapOf()) }
            is TermsEvent.CrewChanged -> edit { it.copy(crewText = event.text, errors = persistentMapOf()) }
            TermsEvent.Save -> save()
            TermsEvent.Dismiss -> stateHolder.closeEditor()
        }
    }

    private fun edit(transform: (FinanceEditorUi.Terms) -> FinanceEditorUi.Terms) =
        stateHolder.editForm { (it as? FinanceEditorUi.Terms)?.let(transform) ?: it }

    private fun open() {
        val finance = stateHolder.state.value.finance ?: return
        stateHolder.openEditor(
            FinanceEditorUi.Terms(
                clientText = finance.clientTotalKopecks?.let(Money::toInput).orEmpty(),
                crewText = finance.crewTotalKopecks?.let(Money::toInput).orEmpty(),
            ),
        )
    }

    /** An empty field means nothing agreed on that side; anything that is not an amount is an error. */
    private fun save() {
        val editor = stateHolder.state.value.editor as? FinanceEditorUi.Terms ?: return
        if (editor.isSaving) return
        val client = parse(editor.clientText)
        val crew = parse(editor.crewText)
        if (client is Typed.Bad || crew is Typed.Bad) {
            edit { it.copy(errors = persistentMapOf(ObjectField.TOTAL_AMOUNT to FieldProblem.INVALID)) }
            return
        }
        edit { it.copy(isSaving = true) }
        scope.launchCatching(TAG, onFailure = { onSaveFailure(it.asAppError()) }) {
            saveTerms(objectId, FinanceTermsDraft(client.kopecksOrNull(), crew.kopecksOrNull()))
                .onSuccess { stateHolder.closeEditor() }
                .onFailure { onSaveFailure(it.asAppError()) }
        }
    }

    private fun parse(text: String): Typed = when {
        text.isBlank() -> Typed.Empty
        else -> Money.parseRubles(text)?.let(Typed::Amount) ?: Typed.Bad
    }

    private fun Typed.kopecksOrNull(): Long? = (this as? Typed.Amount)?.kopecks

    /** What was typed in an amount field. */
    private sealed interface Typed {
        data object Empty : Typed

        data object Bad : Typed

        data class Amount(
            val kopecks: Long,
        ) : Typed
    }

    private suspend fun onSaveFailure(error: AppError) {
        edit { it.copy(isSaving = false) }
        if (error is AppError.Validation) {
            edit { it.copy(errors = error.fieldErrors) }
        } else {
            errorHandler.onActionFailure(error)
        }
    }

    private companion object {
        const val TAG = "TermsEditorHandler"
    }
}
