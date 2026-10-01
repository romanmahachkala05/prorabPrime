package ru.prorabprime.feature.objects.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.ObserveObjectSortUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SaveObjectSortUseCase
import ru.prorabprime.domain.usecase.UploadPhotoUseCase
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectslist_photo_uploaded
import ru.prorabprime.feature.objects.resources.objectslist_receipt_uploaded
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching

internal class ObjectsListViewModel(
    private val stateHolder: IObjectsListStateHolder,
    private val errorHandler: IObjectsListErrorHandler,
    observeObjects: ObserveObjectsUseCase,
    private val refreshObjects: RefreshObjectsUseCase,
    observeObjectSort: ObserveObjectSortUseCase,
    private val saveObjectSort: SaveObjectSortUseCase,
    private val uploadPhoto: UploadPhotoUseCase,
    private val notifier: SnackbarNotifier,
) : ViewModel(),
    StateOwner<ObjectsListState> by stateHolder {

    init {
        val sort = observeObjectSort().onEach(stateHolder::setSort)

        // The search runs on the server, so typing is debounced here rather than in the UI.
        @OptIn(FlowPreview::class)
        val search = state.map { it.search.trim() }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MS)

        @OptIn(ExperimentalCoroutinesApi::class)
        combine(search, sort) { text, order -> ObjectQuery(text, order) }
            .distinctUntilChanged()
            .flatMapLatest { observeObjects(it) }
            .onEach(::render)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ObjectsListEvent) {
        when (event) {
            is ObjectsListEvent.SearchChanged -> stateHolder.setSearch(event.text)

            is ObjectsListEvent.SortSelected -> saveSort(event)

            ObjectsListEvent.Refresh -> refresh()

            ObjectsListEvent.Retry -> retry()

            is ObjectsListEvent.PhotosCaptured ->
                stateHolder.setCapture(CaptureUi(event.images.toImmutableList()))

            is ObjectsListEvent.CaptureKindChanged ->
                state.value.capture?.let { stateHolder.setCapture(it.copy(kind = event.kind)) }

            is ObjectsListEvent.CaptureNoteChanged ->
                state.value.capture?.let { stateHolder.setCapture(it.copy(note = event.note)) }

            is ObjectsListEvent.CaptureTargetChosen -> uploadCapture(event.objectId)

            ObjectsListEvent.CaptureDismissed -> stateHolder.setCapture(null)
        }
    }

    private fun render(result: Result<ImmutableList<ObjectSummary>>) {
        result
            .onSuccess { stateHolder.showObjects(it.toCardsUi()) }
            .onFailure { failure -> viewModelScope.launch { errorHandler.onLoadFailure(failure.asAppError()) } }
    }

    private fun saveSort(event: ObjectsListEvent.SortSelected) {
        stateHolder.setSort(event.sort)
        launchCatching(onFailure = { errorHandler.onLoadFailure(it.asAppError()) }) { saveObjectSort(event.sort) }
    }

    /** Runs in `viewModelScope`: it outlives the sheet, not the screen. */
    private fun uploadCapture(objectId: String) {
        val capture = state.value.capture ?: return
        val target = state.value.items.find { it.id == objectId } ?: return
        stateHolder.setCapture(null)
        launchCatching(onFailure = { errorHandler.onUploadFailure(it.asAppError()) }) {
            capture.images.forEach { image ->
                uploadPhoto(ObjectId(objectId), image, capture.kind, capture.note)
                    .onSuccess { notifier.showSuccess(uploadedMessage(capture.kind, target.title)) }
                    .onFailure { errorHandler.onUploadFailure(it.asAppError()) }
            }
        }
    }

    private fun uploadedMessage(kind: AttachmentKind, title: String): UiText = UiText.Resource(
        if (kind == AttachmentKind.PHOTO) {
            Res.string.objectslist_photo_uploaded
        } else {
            Res.string.objectslist_receipt_uploaded
        },
        persistentListOf(title),
    )

    private fun refresh() {
        stateHolder.setRefreshing(true)
        launchCatching(onFailure = { errorHandler.onLoadFailure(it.asAppError()) }) { refreshObjects() }
    }

    private fun retry() {
        stateHolder.showLoading()
        launchCatching(onFailure = { errorHandler.onLoadFailure(it.asAppError()) }) { refreshObjects() }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
