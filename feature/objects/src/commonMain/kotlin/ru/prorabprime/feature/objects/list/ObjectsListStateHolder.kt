package ru.prorabprime.feature.objects.list

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText

internal interface IObjectsListStateHolder : StateOwner<ObjectsListState> {
    /** Content, or the fitting empty state for the current search. Ends any refresh. */
    fun showObjects(items: ImmutableList<ObjectCardUi>)

    fun showLoading()

    fun showError(message: UiText)

    fun setSearch(text: String)

    fun setSort(sort: ObjectSort)

    /** Opens the search line, or closes it and clears the text. */
    fun toggleSearch()

    fun toggleStatus(status: ObjectStatus)

    fun clearStatuses()

    fun setRefreshing(refreshing: Boolean)

    fun setCapture(capture: CaptureUi?)
}

internal class ObjectsListStateHolder : IObjectsListStateHolder {

    private val _state = MutableStateFlow(ObjectsListState())
    override val state: StateFlow<ObjectsListState> = _state.asStateFlow()

    override fun showObjects(items: ImmutableList<ObjectCardUi>) = _state.update {
        val status = when {
            items.isNotEmpty() -> ObjectsListStatus.Content
            it.search.isNotBlank() || it.statuses.isNotEmpty() -> ObjectsListStatus.NothingFound
            else -> ObjectsListStatus.Empty
        }
        it.copy(status = status, items = items, isRefreshing = false)
    }

    override fun showLoading() = _state.update { it.copy(status = ObjectsListStatus.Loading) }

    override fun showError(message: UiText) = _state.update {
        it.copy(status = ObjectsListStatus.Error(message), isRefreshing = false)
    }

    override fun setSearch(text: String) = _state.update { it.copy(search = text) }

    override fun setSort(sort: ObjectSort) = _state.update { it.copy(sort = sort) }

    override fun toggleSearch() = _state.update {
        if (it.searchOpen) it.copy(searchOpen = false, search = "") else it.copy(searchOpen = true)
    }

    override fun toggleStatus(status: ObjectStatus) = _state.update {
        it.copy(statuses = (if (status in it.statuses) it.statuses - status else it.statuses + status).toImmutableSet())
    }

    override fun clearStatuses() = _state.update { it.copy(statuses = persistentSetOf()) }

    override fun setRefreshing(refreshing: Boolean) = _state.update { it.copy(isRefreshing = refreshing) }

    override fun setCapture(capture: CaptureUi?) = _state.update { it.copy(capture = capture) }
}
