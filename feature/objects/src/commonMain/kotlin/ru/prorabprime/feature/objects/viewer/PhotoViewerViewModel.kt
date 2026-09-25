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
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.toUiText

/**
 * Small enough to own its state directly: two transitions and no collaborators to share them
 * with, so a separate StateHolder would only forward calls.
 */
internal class PhotoViewerViewModel(
    args: PhotoViewerArgs,
    observeObject: ObserveObjectUseCase,
) : ViewModel(),
    StateOwner<PhotoViewerState> {

    private val _state = MutableStateFlow(PhotoViewerState())
    override val state: StateFlow<PhotoViewerState> = _state.asStateFlow()

    init {
        observeObject(ObjectId(args.objectId))
            .onEach { result ->
                result
                    .onSuccess { details ->
                        val index = details.photos.indexOfFirst { it.id.value == args.photoId }.coerceAtLeast(0)
                        _state.update {
                            PhotoViewerState(
                                PhotoViewerStatus.Content,
                                details.photos.map { p ->
                                    p.path
                                }.toImmutableList(),
                                index,
                            )
                        }
                    }.onFailure { failure ->
                        _state.update { it.copy(status = PhotoViewerStatus.Error(failure.asAppError().toUiText())) }
                    }
            }.launchIn(viewModelScope)
    }
}
