package ru.prorabprime.feature.objects.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
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
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.ObserveObjectSortUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SaveObjectSortUseCase
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.launchCatching

internal class ObjectsListViewModel(
    private val stateHolder: IObjectsListStateHolder,
    private val errorHandler: IObjectsListErrorHandler,
    observeObjects: ObserveObjectsUseCase,
    private val refreshObjects: RefreshObjectsUseCase,
    observeObjectSort: ObserveObjectSortUseCase,
    private val saveObjectSort: SaveObjectSortUseCase,
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
