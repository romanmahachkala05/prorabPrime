package ru.prorabprime.feature.objects.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.DeletePhotoUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.feature.objects.photos.amountLabel
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.gallery_delete_confirm
import ru.prorabprime.feature.objects.resources.gallery_delete_message
import ru.prorabprime.feature.objects.resources.gallery_delete_title
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching
import ru.prorabprime.ui.toUiText

/**
 * Small enough to own its state directly: it reads one flow and keeps a selection, so a separate
 * StateHolder would only forward calls.
 */
internal class GalleryViewModel(
    args: GalleryArgs,
    observeObject: ObserveObjectUseCase,
    private val deletePhoto: DeletePhotoUseCase,
    private val notifier: SnackbarNotifier,
) : ViewModel(),
    StateOwner<GalleryState> {

    private val kind = if (args.receipts) AttachmentKind.RECEIPT else AttachmentKind.PHOTO
    private val _state = MutableStateFlow(GalleryState(receipts = args.receipts))
    override val state: StateFlow<GalleryState> = _state.asStateFlow()

    init {
        observeObject(ObjectId(args.objectId))
            .onEach { result ->
                result
                    .onSuccess { details ->
                        val shown = details.photos.filter { it.kind == kind }
                            .map { it.toUi(details.coverPhotoId) }.toImmutableList()
                        _state.update { old ->
                            // A photo deleted elsewhere cannot stay picked.
                            val picked = old.selected.filter { id -> shown.any { it.id == id } }.toImmutableSet()
                            old.copy(status = GalleryStatus.Content, photos = shown, selected = picked)
                        }
                    }.onFailure { failure ->
                        _state.update { it.copy(status = GalleryStatus.Error(failure.asAppError().toUiText())) }
                    }
            }.launchIn(viewModelScope)
    }

    fun onEvent(event: GalleryEvent) {
        when (event) {
            GalleryEvent.SelectingToggled -> _state.update {
                it.copy(selecting = !it.selecting, selected = persistentSetOf())
            }

            is GalleryEvent.PhotoToggled -> _state.update { it.withToggled(event.photoId) }

            GalleryEvent.SelectAllToggled -> _state.update {
                val all = it.photos.map { photo -> photo.id }.toImmutableSet()
                it.copy(selected = if (it.selected == all) persistentSetOf() else all)
            }

            GalleryEvent.DeleteClicked -> askToDelete()

            GalleryEvent.DialogConfirmed -> deleteSelected()

            GalleryEvent.DialogDismissed -> _state.update { it.copy(dialog = null) }
        }
    }

    private fun GalleryState.withToggled(photoId: String) = copy(
        selecting = true,
        selected = (if (photoId in selected) selected - photoId else selected + photoId).toImmutableSet(),
    )

    private fun askToDelete() {
        val count = state.value.selected.size
        if (count == 0) return
        _state.update {
            it.copy(
                dialog = DialogModel.Confirmation(
                    title = UiText.Resource(Res.string.gallery_delete_title),
                    message = UiText.Resource(Res.string.gallery_delete_message, persistentListOf(count)),
                    confirmLabel = UiText.Resource(Res.string.gallery_delete_confirm),
                    destructive = true,
                ),
            )
        }
    }

    /** Deletes one by one and stops at the first failure, so what was not deleted stays picked. */
    private fun deleteSelected() {
        val ids = state.value.selected.toList()
        _state.update { it.copy(dialog = null) }
        launchCatching(onFailure = { notifier.showError(it.asAppError().toUiText()) }) {
            for (id in ids) {
                val failure = deletePhoto(PhotoId(id)).exceptionOrNull()
                if (failure != null) {
                    notifier.showError(failure.asAppError().toUiText())
                    return@launchCatching
                }
                _state.update { it.copy(selected = (it.selected - id).toImmutableSet()) }
            }
            _state.update { it.copy(selecting = false) }
        }
    }
}

private fun Photo.toUi(coverPhotoId: PhotoId?) = GalleryPhotoUi(
    id = id.value,
    thumb = thumbPath,
    isCover = id == coverPhotoId,
    isPending = isPending,
    quarterTurns = quarterTurns,
    amount = receipt?.amountLabel(),
)
