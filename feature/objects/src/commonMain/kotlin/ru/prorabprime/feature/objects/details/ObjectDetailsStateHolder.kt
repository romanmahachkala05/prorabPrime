package ru.prorabprime.feature.objects.details

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText

internal interface IObjectDetailsStateHolder : StateOwner<ObjectDetailsState> {
    fun showDetails(details: ObjectDetailsUi)

    fun showLoading()

    fun showError(message: UiText)

    fun askToConfirm(dialog: DialogModel, action: ObjectDetailsAction)

    /** Closes the dialog and forgets what it would have done. */
    fun dismissDialog()

    fun setDeleting(deleting: Boolean)

    fun close()
}

internal class ObjectDetailsStateHolder : IObjectDetailsStateHolder {

    private val _state = MutableStateFlow(ObjectDetailsState())
    override val state: StateFlow<ObjectDetailsState> = _state.asStateFlow()

    override fun showDetails(details: ObjectDetailsUi) = _state.update {
        it.copy(status = ObjectDetailsStatus.Content, details = details)
    }

    override fun showLoading() = _state.update { it.copy(status = ObjectDetailsStatus.Loading) }

    override fun showError(message: UiText) = _state.update { it.copy(status = ObjectDetailsStatus.Error(message)) }

    override fun askToConfirm(dialog: DialogModel, action: ObjectDetailsAction) = _state.update {
        it.copy(dialog = dialog, pendingAction = action)
    }

    override fun dismissDialog() = _state.update { it.copy(dialog = null, pendingAction = null) }

    override fun setDeleting(deleting: Boolean) = _state.update { it.copy(isDeleting = deleting) }

    override fun close() = _state.update { it.copy(isClosed = true, isDeleting = false) }
}
