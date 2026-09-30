package ru.prorabprime.feature.finance

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText

internal interface IFinanceStateHolder : StateOwner<FinanceState> {
    fun showFinance(finance: FinanceUi)

    fun showLoading()

    fun showError(message: UiText)

    fun openEditor(editor: FinanceEditorUi)

    /** Applies [transform] to the open form; does nothing when none is open. */
    fun editForm(transform: (FinanceEditorUi) -> FinanceEditorUi)

    fun closeEditor()

    /** An open sheet with [revisions], or closes it with null. */
    fun setHistory(revisions: ImmutableList<RevisionUi>?)

    fun askToConfirm(dialog: DialogModel, action: FinanceAction)

    /** Closes the dialog and forgets what it would have done. */
    fun dismissDialog()
}

internal class FinanceStateHolder : IFinanceStateHolder {

    private val _state = MutableStateFlow(FinanceState())
    override val state: StateFlow<FinanceState> = _state.asStateFlow()

    override fun showFinance(finance: FinanceUi) = _state.update {
        it.copy(status = FinanceStatus.Content, finance = finance)
    }

    override fun showLoading() = _state.update { it.copy(status = FinanceStatus.Loading) }

    override fun showError(message: UiText) = _state.update { it.copy(status = FinanceStatus.Error(message)) }

    override fun openEditor(editor: FinanceEditorUi) = _state.update { it.copy(editor = editor) }

    override fun editForm(transform: (FinanceEditorUi) -> FinanceEditorUi) = _state.update { state ->
        state.copy(editor = state.editor?.let(transform))
    }

    override fun closeEditor() = _state.update { it.copy(editor = null) }

    override fun setHistory(revisions: ImmutableList<RevisionUi>?) = _state.update { it.copy(history = revisions) }

    override fun askToConfirm(dialog: DialogModel, action: FinanceAction) = _state.update {
        it.copy(dialog = dialog, pendingAction = action)
    }

    override fun dismissDialog() = _state.update { it.copy(dialog = null, pendingAction = null) }
}
