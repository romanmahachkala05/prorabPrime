package ru.prorabprime.feature.objects.edit

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface ObjectEditStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : ObjectEditStatus

    data object Loading : ObjectEditStatus

    data class Error(
        val message: UiText,
    ) : ObjectEditStatus
}

/** The form's fields as typed; nothing is trimmed until it is saved. */
@Immutable
internal data class ObjectForm(
    val title: String = "",
    val address: String = "",
    val status: ObjectStatus = ObjectStatus.IN_PROGRESS,
    val clientName: String = "",
    val clientPhone: String = "",
    val notes: String = "",
)

@Immutable
internal data class ObjectEditState(
    val status: ObjectEditStatus = ObjectEditStatus.Loading,
    val isNew: Boolean = true,
    val form: ObjectForm = ObjectForm(),
    val fieldErrors: ImmutableMap<ObjectField, FieldProblem> = persistentMapOf(),
    val isSaving: Boolean = false,
    /** Set when saving succeeded; for a new object it carries the new id. */
    val saved: SaveResult? = null,
)

internal sealed interface SaveResult {
    data class Created(
        val objectId: String,
    ) : SaveResult

    data object Updated : SaveResult
}

internal sealed interface ObjectEditEvent {
    data class FieldChanged(
        val field: ObjectField,
        val value: String,
    ) : ObjectEditEvent

    data class StatusChanged(
        val status: ObjectStatus,
    ) : ObjectEditEvent

    data object SaveClicked : ObjectEditEvent

    data object Retry : ObjectEditEvent
}
