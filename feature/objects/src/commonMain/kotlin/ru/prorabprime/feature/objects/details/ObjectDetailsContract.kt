package ru.prorabprime.feature.objects.details

import androidx.compose.runtime.Immutable
import ru.prorabprime.domain.model.ObjectStatus
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
)

/** An operation waiting for the user to confirm it in [ObjectDetailsState.dialog]. */
internal sealed interface ObjectDetailsAction {
    data object DeleteObject : ObjectDetailsAction
}

@Immutable
internal data class ObjectDetailsState(
    val status: ObjectDetailsStatus = ObjectDetailsStatus.Loading,
    val details: ObjectDetailsUi? = null,
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
}
