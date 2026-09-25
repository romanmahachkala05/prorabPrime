package ru.prorabprime.feature.objects.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.DeleteObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectdetails_delete_confirm
import ru.prorabprime.feature.objects.resources.objectdetails_delete_message
import ru.prorabprime.feature.objects.resources.objectdetails_delete_title
import ru.prorabprime.feature.objects.resources.objectdetails_deleted
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching

internal class ObjectDetailsViewModel(
    private val objectId: ObjectId,
    private val stateHolder: IObjectDetailsStateHolder,
    private val errorHandler: IObjectDetailsErrorHandler,
    observeObject: ObserveObjectUseCase,
    private val refreshObjects: RefreshObjectsUseCase,
    private val deleteObject: DeleteObjectUseCase,
    private val notifier: SnackbarNotifier,
) : ViewModel(),
    StateOwner<ObjectDetailsState> by stateHolder {

    init {
        // The repository reloads this after any write, including the edit form's.
        observeObject(objectId)
            .onEach(::render)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ObjectDetailsEvent) {
        when (event) {
            ObjectDetailsEvent.DeleteClicked -> stateHolder.askToConfirm(
                DELETE_DIALOG,
                ObjectDetailsAction.DeleteObject,
            )

            ObjectDetailsEvent.DialogConfirmed -> runPendingAction()

            ObjectDetailsEvent.DialogDismissed -> stateHolder.dismissDialog()

            ObjectDetailsEvent.Retry -> retry()
        }
    }

    private fun render(result: Result<ObjectDetails>) {
        // Deleting ends this object's stream with NotFound; that is not an error to show.
        if (state.value.isDeleting || state.value.isClosed) return
        result
            .onSuccess { stateHolder.showDetails(it.toUi()) }
            .onFailure { failure -> viewModelScope.launch { errorHandler.onLoadFailure(failure.asAppError()) } }
    }

    private fun runPendingAction() {
        val action = state.value.pendingAction
        stateHolder.dismissDialog()
        when (action) {
            ObjectDetailsAction.DeleteObject -> delete()
            null -> Unit
        }
    }

    private fun delete() {
        stateHolder.setDeleting(true)
        launchCatching(onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            deleteObject(objectId)
                .onSuccess {
                    stateHolder.close()
                    notifier.showMessage(DELETED)
                }.onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private fun retry() {
        stateHolder.showLoading()
        launchCatching(onFailure = { errorHandler.onLoadFailure(it.asAppError()) }) { refreshObjects() }
    }

    private companion object {
        val DELETE_DIALOG = DialogModel.Confirmation(
            title = UiText.Resource(Res.string.objectdetails_delete_title),
            message = UiText.Resource(Res.string.objectdetails_delete_message),
            confirmLabel = UiText.Resource(Res.string.objectdetails_delete_confirm),
            destructive = true,
        )
        val DELETED = UiText.Resource(Res.string.objectdetails_deleted)
    }
}

internal fun ObjectDetails.toUi() = ObjectDetailsUi(
    title = displayTitle,
    address = address.takeIf { title != null },
    status = status,
    clientName = clientName,
    clientPhone = clientPhone,
    notes = notes,
)
