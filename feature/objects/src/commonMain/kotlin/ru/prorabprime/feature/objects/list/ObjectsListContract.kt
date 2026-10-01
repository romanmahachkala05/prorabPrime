package ru.prorabprime.feature.objects.list

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.LocalImageRef
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
    /** Not yet on the server. */
    val isPending: Boolean = false,
)

/** A picture just taken, waiting for the user to say which object and folder it goes to. */
@Immutable
internal data class CaptureUi(
    val images: ImmutableList<LocalImageRef>,
    val kind: AttachmentKind = AttachmentKind.PHOTO,
    /** Written for every picture of this batch; empty is no note. */
    val note: String = "",
)

@Immutable
internal data class ObjectsListState(
    val status: ObjectsListStatus = ObjectsListStatus.Loading,
    val items: ImmutableList<ObjectCardUi> = persistentListOf(),
    val search: String = "",
    val sort: ObjectSort = ObjectSort.DEFAULT,
    val isRefreshing: Boolean = false,
    val capture: CaptureUi? = null,
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

    /** Pictures came back from the camera. */
    data class PhotosCaptured(
        val images: List<LocalImageRef>,
    ) : ObjectsListEvent

    data class CaptureKindChanged(
        val kind: AttachmentKind,
    ) : ObjectsListEvent

    data class CaptureNoteChanged(
        val note: String,
    ) : ObjectsListEvent

    data class CaptureTargetChosen(
        val objectId: String,
    ) : ObjectsListEvent

    data object CaptureDismissed : ObjectsListEvent
}
