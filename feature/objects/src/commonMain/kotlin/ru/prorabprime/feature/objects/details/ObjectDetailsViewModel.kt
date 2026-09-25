package ru.prorabprime.feature.objects.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectdetails_cover_set
import ru.prorabprime.feature.objects.resources.objectdetails_delete_confirm
import ru.prorabprime.feature.objects.resources.objectdetails_delete_message
import ru.prorabprime.feature.objects.resources.objectdetails_delete_photo_message
import ru.prorabprime.feature.objects.resources.objectdetails_delete_photo_title
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
    private val actions: ObjectDetailsActions,
    private val notifier: SnackbarNotifier,
) : ViewModel(),
    StateOwner<ObjectDetailsState> by stateHolder {

    init {
        // The repository reloads this after any write: the form's, an upload, a new cover.
        actions.observeObject(objectId)
            .onEach(::render)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ObjectDetailsEvent) {
        when (event) {
            ObjectDetailsEvent.DeleteClicked -> stateHolder.askToConfirm(
                DELETE_OBJECT_DIALOG,
                ObjectDetailsAction.DeleteObject,
            )

            ObjectDetailsEvent.DialogConfirmed -> runPendingAction()

            ObjectDetailsEvent.DialogDismissed -> stateHolder.dismissDialog()

            ObjectDetailsEvent.Retry -> retry()

            is ObjectDetailsEvent.PhotosPicked -> event.images.forEach(::upload)

            is ObjectDetailsEvent.RetryUpload -> upload(event.image)

            is ObjectDetailsEvent.DismissUpload -> stateHolder.removeUpload(event.image)

            is ObjectDetailsEvent.MakeCoverClicked -> makeCover(event.photoId)

            is ObjectDetailsEvent.DeletePhotoClicked ->
                stateHolder.askToConfirm(DELETE_PHOTO_DIALOG, ObjectDetailsAction.DeletePhoto(event.photoId))
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
            ObjectDetailsAction.DeleteObject -> deleteObject()
            is ObjectDetailsAction.DeletePhoto -> deletePhoto(action.photoId)
            null -> Unit
        }
    }

    /**
     * Runs in `viewModelScope`, so an upload outlives a rotation; it does not outlive leaving
     * the screen, which stage 1 accepts (no background uploads yet).
     */
    private fun upload(image: LocalImageRef) {
        stateHolder.startUpload(image)
        launchCatching(onFailure = { errorHandler.onUploadFailure(image, it.asAppError()) }) {
            actions.uploadPhoto(objectId, image)
                .onSuccess { stateHolder.removeUpload(image) }
                .onFailure { errorHandler.onUploadFailure(image, it.asAppError()) }
        }
    }

    private fun makeCover(photoId: String) {
        launchCatching(onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            actions.setCoverPhoto(objectId, PhotoId(photoId))
                .onSuccess { notifier.showMessage(COVER_SET) }
                .onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private fun deletePhoto(photoId: String) {
        launchCatching(onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            actions.deletePhoto(PhotoId(photoId)).onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private fun deleteObject() {
        stateHolder.setDeleting(true)
        launchCatching(onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            actions.deleteObject(objectId)
                .onSuccess {
                    stateHolder.close()
                    notifier.showMessage(DELETED)
                }.onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private fun retry() {
        stateHolder.showLoading()
        launchCatching(onFailure = { errorHandler.onLoadFailure(it.asAppError()) }) { actions.refreshObjects() }
    }

    private companion object {
        val DELETE_OBJECT_DIALOG = DialogModel.Confirmation(
            title = UiText.Resource(Res.string.objectdetails_delete_title),
            message = UiText.Resource(Res.string.objectdetails_delete_message),
            confirmLabel = UiText.Resource(Res.string.objectdetails_delete_confirm),
            destructive = true,
        )
        val DELETE_PHOTO_DIALOG = DialogModel.Confirmation(
            title = UiText.Resource(Res.string.objectdetails_delete_photo_title),
            message = UiText.Resource(Res.string.objectdetails_delete_photo_message),
            confirmLabel = UiText.Resource(Res.string.objectdetails_delete_confirm),
            destructive = true,
        )
        val DELETED = UiText.Resource(Res.string.objectdetails_deleted)
        val COVER_SET = UiText.Resource(Res.string.objectdetails_cover_set)
    }
}

internal fun ObjectDetails.toUi() = ObjectDetailsUi(
    title = displayTitle,
    address = address.takeIf { title != null },
    status = status,
    clientName = clientName,
    clientPhone = clientPhone,
    notes = notes,
    photos = photos.map { PhotoUi(it.id.value, it.thumbPath, isCover = it.id == coverPhotoId) }.toImmutableList(),
)
