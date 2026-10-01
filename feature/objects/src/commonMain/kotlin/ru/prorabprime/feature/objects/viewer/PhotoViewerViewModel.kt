package ru.prorabprime.feature.objects.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.RotatePhotoUseCase
import ru.prorabprime.domain.usecase.SetPhotoNoteUseCase
import ru.prorabprime.feature.objects.photos.line
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.toUiText

/**
 * Small enough to own its state directly: two transitions and no collaborators to share them
 * with, so a separate StateHolder would only forward calls.
 */
internal class PhotoViewerViewModel(
    args: PhotoViewerArgs,
    observeObject: ObserveObjectUseCase,
    private val rotatePhoto: RotatePhotoUseCase,
    private val setPhotoNote: SetPhotoNoteUseCase,
    private val notifier: SnackbarNotifier,
) : ViewModel(),
    StateOwner<PhotoViewerState> {

    private val _state = MutableStateFlow(PhotoViewerState())
    override val state: StateFlow<PhotoViewerState> = _state.asStateFlow()

    init {
        observeObject(ObjectId(args.objectId))
            .onEach { result ->
                result
                    .onSuccess { details ->
                        // Photos and receipts are separate folders: page through the tapped one's.
                        val kind = details.photos.find { it.id.value == args.photoId }?.kind
                        val folder = details.photos.filter { kind == null || it.kind == kind }
                        val index = folder.indexOfFirst { it.id.value == args.photoId }.coerceAtLeast(0)
                        _state.update {
                            PhotoViewerState(
                                PhotoViewerStatus.Content,
                                folder.map {
                                    ViewerPhoto(it.id.value, it.path, it.quarterTurns, it.note, it.receipt?.line())
                                }.toImmutableList(),
                                index,
                            )
                        }
                    }.onFailure { failure ->
                        _state.update { it.copy(status = PhotoViewerStatus.Error(failure.asAppError().toUiText())) }
                    }
            }.launchIn(viewModelScope)
    }

    fun saveNote(photoId: String, note: String) {
        viewModelScope.launch {
            setPhotoNote(PhotoId(photoId), note).onFailure { notifier.showError(it.asAppError().toUiText()) }
        }
    }

    fun rotate(photoId: String) {
        viewModelScope.launch {
            rotatePhoto(PhotoId(photoId)).onFailure { notifier.showError(it.asAppError().toUiText()) }
        }
    }
}
