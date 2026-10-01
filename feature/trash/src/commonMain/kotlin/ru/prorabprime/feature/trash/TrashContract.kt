package ru.prorabprime.feature.trash

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface TrashStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : TrashStatus

    data object Loading : TrashStatus

    data class Error(
        val message: UiText,
    ) : TrashStatus
}

/** A deleted object, ready to render. */
@Immutable
internal data class TrashObjectUi(
    val id: String,
    val title: String,
    /** The address, when the title is something else. */
    val address: String?,
    val cover: ServerFilePath?,
    val photoCount: Int,
    val daysLeft: Int,
)

/** A deleted photo or receipt, with where it came from. */
@Immutable
internal data class TrashPhotoUi(
    val id: String,
    val objectTitle: String,
    val kind: AttachmentKind,
    val thumb: ServerFilePath,
    val daysLeft: Int,
)

/** What waits for the user to confirm it in [TrashState.dialog]. */
internal sealed interface TrashAction {
    data class PurgeObject(
        val id: String,
    ) : TrashAction

    data class PurgePhoto(
        val id: String,
    ) : TrashAction

    data object EmptyAll : TrashAction
}

@Immutable
internal data class TrashState(
    val status: TrashStatus = TrashStatus.Loading,
    val objects: ImmutableList<TrashObjectUi> = persistentListOf(),
    val photos: ImmutableList<TrashPhotoUi> = persistentListOf(),
    val dialog: DialogModel? = null,
    val pendingAction: TrashAction? = null,
    /** A restore or a removal is under way. */
    val isBusy: Boolean = false,
) {
    val isEmpty: Boolean get() = objects.isEmpty() && photos.isEmpty()
}

internal sealed interface TrashEvent {
    data object Retry : TrashEvent

    data class RestoreObject(
        val id: String,
    ) : TrashEvent

    data class RestorePhoto(
        val id: String,
    ) : TrashEvent

    data class PurgeObjectClicked(
        val id: String,
    ) : TrashEvent

    data class PurgePhotoClicked(
        val id: String,
    ) : TrashEvent

    data object EmptyClicked : TrashEvent

    data object DialogConfirmed : TrashEvent

    data object DialogDismissed : TrashEvent
}
