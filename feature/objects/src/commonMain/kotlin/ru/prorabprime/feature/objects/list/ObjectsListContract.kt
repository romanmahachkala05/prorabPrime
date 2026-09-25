package ru.prorabprime.feature.objects.list

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface ObjectsListStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : ObjectsListStatus

    /** No objects at all yet. */
    data object Empty : ObjectsListStatus

    /** Objects exist, but none match the search. */
    data object NothingFound : ObjectsListStatus

    data object Loading : ObjectsListStatus

    data class Error(
        val message: UiText,
    ) : ObjectsListStatus
}

/** One card of the list, ready to render. */
@Immutable
internal data class ObjectCardUi(
    val id: String,
    val title: String,
    /** The address, when the title is something else; null when the title is the address. */
    val address: String?,
    val status: ObjectStatus,
    val photoCount: Int,
    val cover: ServerFilePath?,
)

@Immutable
internal data class ObjectsListState(
    val status: ObjectsListStatus = ObjectsListStatus.Loading,
    val items: ImmutableList<ObjectCardUi> = persistentListOf(),
    val search: String = "",
    val sort: ObjectSort = ObjectSort.DEFAULT,
    val isRefreshing: Boolean = false,
)

internal sealed interface ObjectsListEvent {
    data class SearchChanged(
        val text: String,
    ) : ObjectsListEvent

    data class SortSelected(
        val sort: ObjectSort,
    ) : ObjectsListEvent

    /** Pull-to-refresh. */
    data object Refresh : ObjectsListEvent

    /** The retry button of the error state. */
    data object Retry : ObjectsListEvent
}
