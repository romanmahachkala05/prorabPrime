package ru.prorabprime.feature.materials

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.MaterialStatus
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface MaterialsStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : MaterialsStatus

    data object Loading : MaterialsStatus

    data class Error(
        val message: UiText,
    ) : MaterialsStatus
}

@Immutable
internal data class MaterialUi(
    val id: String,
    val title: String,
    val status: MaterialStatus,
)

@Immutable
internal data class MaterialsUi(
    val items: ImmutableList<MaterialUi> = persistentListOf(),
) {
    val total: Int get() = items.size
    val inApartment: Int get() = items.count { it.status == MaterialStatus.IN_APARTMENT }
}

/** The form open over the list: a new material when [materialId] is null. */
@Immutable
internal data class MaterialEditorUi(
    val materialId: String? = null,
    val title: String = "",
    val status: MaterialStatus = MaterialStatus.NOT_CHOSEN,
    val errors: ImmutableMap<ObjectField, FieldProblem> = persistentMapOf(),
    val isSaving: Boolean = false,
)

@Immutable
internal data class MaterialsState(
    val status: MaterialsStatus = MaterialsStatus.Loading,
    val materials: MaterialsUi? = null,
    val editor: MaterialEditorUi? = null,
    val dialog: DialogModel? = null,
    val pendingDeleteId: String? = null,
)

internal sealed interface MaterialsEvent {
    data object Retry : MaterialsEvent

    data object AddClicked : MaterialsEvent

    data object AddDefaultsClicked : MaterialsEvent

    data class EditClicked(
        val materialId: String,
    ) : MaterialsEvent

    /** A tap on a row's status: on to the next of the three. */
    data class StatusTapped(
        val materialId: String,
    ) : MaterialsEvent

    data class TitleChanged(
        val text: String,
    ) : MaterialsEvent

    data class StatusPicked(
        val status: MaterialStatus,
    ) : MaterialsEvent

    data object SaveClicked : MaterialsEvent

    data class DeleteClicked(
        val materialId: String,
    ) : MaterialsEvent

    data object EditorDismissed : MaterialsEvent

    data object DialogConfirmed : MaterialsEvent

    data object DialogDismissed : MaterialsEvent
}
