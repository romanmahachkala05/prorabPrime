package ru.prorabprime.feature.trash

import androidx.lifecycle.ViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.Trash
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.LoadTrashUseCase
import ru.prorabprime.domain.usecase.PurgeFromTrashUseCase
import ru.prorabprime.domain.usecase.RestoreFromTrashUseCase
import ru.prorabprime.feature.trash.resources.Res
import ru.prorabprime.feature.trash.resources.trash_empty_confirm
import ru.prorabprime.feature.trash.resources.trash_empty_message
import ru.prorabprime.feature.trash.resources.trash_empty_title
import ru.prorabprime.feature.trash.resources.trash_purge_confirm
import ru.prorabprime.feature.trash.resources.trash_purge_object_message
import ru.prorabprime.feature.trash.resources.trash_purge_photo_message
import ru.prorabprime.feature.trash.resources.trash_purge_title
import ru.prorabprime.feature.trash.resources.trash_purged
import ru.prorabprime.feature.trash.resources.trash_restored
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching
import ru.prorabprime.ui.toUiText

/**
 * Small enough to own its state directly: it reads one list and runs one action at a time, so a
 * separate StateHolder would only forward calls.
 */
internal class TrashViewModel(
    private val loadTrash: LoadTrashUseCase,
    private val restore: RestoreFromTrashUseCase,
    private val purge: PurgeFromTrashUseCase,
    private val notifier: SnackbarNotifier,
) : ViewModel(),
    StateOwner<TrashState> {

    private val _state = MutableStateFlow(TrashState())
    override val state: StateFlow<TrashState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: TrashEvent) {
        when (event) {
            TrashEvent.Retry -> {
                _state.update { it.copy(status = TrashStatus.Loading) }
                load()
            }

            is TrashEvent.RestoreObject -> act(UiText.Resource(Res.string.trash_restored)) {
                restore.obj(ObjectId(event.id))
            }

            is TrashEvent.RestorePhoto -> act(UiText.Resource(Res.string.trash_restored)) {
                restore.photo(PhotoId(event.id))
            }

            is TrashEvent.PurgeObjectClicked -> ask(TrashAction.PurgeObject(event.id), PURGE_OBJECT)

            is TrashEvent.PurgePhotoClicked -> ask(TrashAction.PurgePhoto(event.id), PURGE_PHOTO)

            TrashEvent.EmptyClicked -> ask(TrashAction.EmptyAll, EMPTY_ALL)

            TrashEvent.DialogConfirmed -> runPending()

            TrashEvent.DialogDismissed -> _state.update { it.copy(dialog = null, pendingAction = null) }
        }
    }

    private fun ask(action: TrashAction, dialog: DialogModel) =
        _state.update { it.copy(dialog = dialog, pendingAction = action) }

    private fun runPending() {
        val action = state.value.pendingAction
        _state.update { it.copy(dialog = null, pendingAction = null) }
        val purged = UiText.Resource(Res.string.trash_purged)
        when (action) {
            is TrashAction.PurgeObject -> act(purged) { purge.obj(ObjectId(action.id)) }
            is TrashAction.PurgePhoto -> act(purged) { purge.photo(PhotoId(action.id)) }
            TrashAction.EmptyAll -> act(purged) { purge.all() }
            null -> Unit
        }
    }

    private fun load() {
        launchCatching(onFailure = { showLoadFailure(it) }) {
            loadTrash().fold(onSuccess = ::show, onFailure = { showLoadFailure(it) })
        }
    }

    /** Runs one change, says how it went, and reads the trash again. */
    private fun act(done: UiText, change: suspend () -> Result<Unit>) {
        if (state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        launchCatching(onFailure = { failed(it) }) {
            change().fold(
                onSuccess = {
                    notifier.showSuccess(done)
                    loadTrash().onSuccess(::show).onFailure { _state.update { s -> s.copy(isBusy = false) } }
                },
                onFailure = { failed(it) },
            )
        }
    }

    private suspend fun failed(failure: Throwable) {
        _state.update { it.copy(isBusy = false) }
        notifier.showError(failure.asAppError().toUiText())
    }

    private fun showLoadFailure(failure: Throwable) = _state.update {
        // A failed reload leaves what is on screen; only a first read has nothing to show instead.
        if (it.status == TrashStatus.Content) {
            it.copy(isBusy = false)
        } else {
            it.copy(status = TrashStatus.Error(failure.asAppError().toUiText()), isBusy = false)
        }
    }

    private fun show(trash: Trash) = _state.update {
        it.copy(
            status = TrashStatus.Content,
            objects = trash.objects.map { deleted ->
                TrashObjectUi(
                    id = deleted.id.value,
                    title = deleted.title ?: deleted.address,
                    address = deleted.address.takeIf { deleted.title != null },
                    cover = deleted.coverThumbPath,
                    photoCount = deleted.photoCount,
                    daysLeft = deleted.daysLeft,
                )
            }.toImmutableList(),
            photos = trash.photos.map { deleted ->
                TrashPhotoUi(
                    id = deleted.id.value,
                    objectTitle = deleted.objectTitle ?: deleted.objectAddress,
                    kind = deleted.kind,
                    thumb = deleted.thumbPath,
                    daysLeft = deleted.daysLeft,
                )
            }.toImmutableList(),
            isBusy = false,
        )
    }

    private companion object {
        val PURGE_OBJECT = purgeDialog(Res.string.trash_purge_object_message)
        val PURGE_PHOTO = purgeDialog(Res.string.trash_purge_photo_message)
        val EMPTY_ALL = DialogModel.Confirmation(
            title = UiText.Resource(Res.string.trash_empty_title),
            message = UiText.Resource(Res.string.trash_empty_message),
            confirmLabel = UiText.Resource(Res.string.trash_empty_confirm),
            destructive = true,
        )

        private fun purgeDialog(message: org.jetbrains.compose.resources.StringResource) = DialogModel.Confirmation(
            title = UiText.Resource(Res.string.trash_purge_title),
            message = UiText.Resource(message, persistentListOf()),
            confirmLabel = UiText.Resource(Res.string.trash_purge_confirm),
            destructive = true,
        )
    }
}
