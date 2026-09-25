package ru.prorabprime.feature.objects.details

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface ObjectDetailsStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : ObjectDetailsStatus

    data object Loading : ObjectDetailsStatus

    data class Error(
        val message: UiText,
    ) : ObjectDetailsStatus
}

@Immutable
internal data class ObjectDetailsUi(
    val title: String,
    /** Shown separately only when the title is something other than the address. */
    val address: String?,
    val status: ObjectStatus,
    val clientName: String?,
    val clientPhone: String?,
    val notes: String?,
    val photos: ImmutableList<PhotoUi> = persistentListOf(),
)

@Immutable
internal data class PhotoUi(
    val id: String,
    val thumb: ServerFilePath,
    val isCover: Boolean,
)

/** A picture on its way to the server, shown in the carousel until it arrives as a [PhotoUi]. */
@Immutable
internal data class UploadUi(
    val image: LocalImageRef,
    val failure: UiText? = null,
) {
    val isFailed: Boolean get() = failure != null
}

/** An operation waiting for the user to confirm it in [ObjectDetailsState.dialog]. */
internal sealed interface ObjectDetailsAction {
    data object DeleteObject : ObjectDetailsAction

    data class DeletePhoto(
        val photoId: String,
    ) : ObjectDetailsAction
}

@Immutable
internal data class ObjectDetailsState(
    val status: ObjectDetailsStatus = ObjectDetailsStatus.Loading,
    val details: ObjectDetailsUi? = null,
    val uploads: ImmutableList<UploadUi> = persistentListOf(),
    val dialog: DialogModel? = null,
    val pendingAction: ObjectDetailsAction? = null,
    val isDeleting: Boolean = false,
    /** Set once the object is gone; the screen then leaves. */
    val isClosed: Boolean = false,
)

internal sealed interface ObjectDetailsEvent {
    data object DeleteClicked : ObjectDetailsEvent

    data object DialogConfirmed : ObjectDetailsEvent

    data object DialogDismissed : ObjectDetailsEvent

    data object Retry : ObjectDetailsEvent

    /** Pictures taken with the camera or chosen in the gallery. */
    data class PhotosPicked(
        val images: List<LocalImageRef>,
    ) : ObjectDetailsEvent

    data class RetryUpload(
        val image: LocalImageRef,
    ) : ObjectDetailsEvent

    data class DismissUpload(
        val image: LocalImageRef,
    ) : ObjectDetailsEvent

    data class MakeCoverClicked(
        val photoId: String,
    ) : ObjectDetailsEvent

    data class DeletePhotoClicked(
        val photoId: String,
    ) : ObjectDetailsEvent
}
